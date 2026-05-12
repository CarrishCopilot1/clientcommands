package net.earthcomputer.enchantpredict.features;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.seedfinding.mcseed.lcg.LCG;
import com.seedfinding.mcseed.rand.Rand;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.earthcomputer.enchantpredict.Configs;
import net.earthcomputer.enchantpredict.command.ClientCommandHelper;
import net.earthcomputer.enchantpredict.event.ClientLevelEvents;
import net.earthcomputer.enchantpredict.task.ItemThrowTask;
import net.earthcomputer.enchantpredict.task.LongTask;
import net.earthcomputer.enchantpredict.task.LongTaskList;
import net.earthcomputer.enchantpredict.task.OneTickTask;
import net.earthcomputer.enchantpredict.task.SimpleTask;
import net.earthcomputer.enchantpredict.task.TaskManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

/**
 * Enchantment cracker — port of {@code net.earthcomputer.clientcommands.features.EnchantmentCracker}.
 *
 * <p>Behavioural parity with the Fabric source: brute-force XP seed search from the
 * 12 server-supplied bits, player-RNG state recovery via successive enchant clues,
 * and {@link #manipulateEnchantments} item-throw based outcome manipulation.</p>
 *
 * <p>Adaptations for 1.21.1: dropped {@code MultiVersionCompat} / {@code LegacyEnchantment}
 * branches (single-version target) and ported the GUI overlay path from
 * {@code GuiGraphicsExtractor} (1.21.5+) to {@link GuiGraphics} (1.21.1).</p>
 */
public class EnchantmentCracker {

    public static final Logger LOGGER = LogUtils.getLogger();
    private static final int PROGRESS_BAR_WIDTH = 50;

    @Nullable
    private static WeakReference<LongTask> currentEnchantingTask = null;
    private static boolean isCurrentlyThrowingItems = false;
    private static int expectedNumBookshelves = -1;

    public static void registerEvents() {
        ClientLevelEvents.UNLOAD_LEVEL.register(isDisconnect -> {
            if (isDisconnect) expectedNumBookshelves = -1;
        });
        PlayerRandCracker.RNG_CALLED_EVENT.register(EnchantmentCracker::onRNGCallEvent);
    }

    // ----- RENDERING -----

    public static void renderOverlay(GuiGraphics graphics) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        CrackState crackState = Configs.enchCrackState;

        List<Component> lines = new ArrayList<>();

        lines.add(Component.translatable("enchCrack.state", Component.translatable("enchCrack.state." + crackState.getSerializedName())));
        lines.add(Component.translatable("playerManip.state", Component.translatable("playerManip.state." + Configs.playerCrackState.getSerializedName())));

        lines.add(Component.empty());

        if (crackState == CrackState.CRACKED && !possibleXPSeeds.isEmpty()) {
            lines.add(Component.translatable("enchCrack.xpSeed.one", String.format("%08X", possibleXPSeeds.iterator().next())));
        } else if (crackState == CrackState.CRACKING) {
            lines.add(Component.translatable("enchCrack.xpSeed.many", possibleXPSeeds.size()));
        }

        lines.add(Component.empty());

        if (enchantingTablePos != null) {
            int numBookshelves = getEnchantPower(level, enchantingTablePos);
            if (expectedNumBookshelves == -1) {
                lines.add(Component.translatable("enchCrack.bookshelfCount", numBookshelves));
            } else {
                boolean bookshelfCountMatches = numBookshelves == expectedNumBookshelves || (numBookshelves > 15 && expectedNumBookshelves == 15);
                lines.add(Component.translatable("enchCrack.bookshelfCount.expected", expectedNumBookshelves));
                lines.add(Component.translatable("enchCrack.bookshelfCount.actual",
                    Component.literal(String.valueOf(numBookshelves))
                        .withStyle(bookshelfCountMatches ? ChatFormatting.GREEN : ChatFormatting.RED)));
                if (!bookshelfCountMatches) {
                    lines.add(Component.translatable("enchCrack.bookshelfCount.incorrect").withStyle(ChatFormatting.RED));
                }
            }
            lines.add(Component.empty());
        }

        if (crackState == CrackState.CRACKED) {
            lines.add(Component.translatable("enchCrack.enchantments"));
        } else {
            lines.add(Component.translatable("enchCrack.clues"));
        }

        for (int slot = 0; slot < 3; slot++) {
            lines.add(Component.translatable("enchCrack.slot", slot + 1));
            List<EnchantmentInstance> enchs = getEnchantmentsInTable(slot);
            if (enchs != null) {
                sortIntoTooltipOrder(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT), enchs);
                for (EnchantmentInstance ench : enchs) {
                    lines.add(Component.literal("   ").append(Enchantment.getFullname(ench.enchantment(), ench.level())));
                }
            }
        }

        Font font = Minecraft.getInstance().font;
        int y = 0;
        for (Component line : lines) {
            graphics.drawString(font, line, 0, y, 0xffffffff, false);
            y += font.lineHeight;
        }
    }

    // ----- LOGIC -----

    static Set<Integer> possibleXPSeeds = new HashSet<>(1 << 20);
    private static int firstXpSeed;
    @Nullable
    public static BlockPos enchantingTablePos = null;
    private static boolean doneEnchantment = false;

    public static void resetCracker() {
        Configs.enchCrackState = CrackState.UNCRACKED;
        possibleXPSeeds.clear();
    }

    private static void prepareForNextEnchantmentSeedCrack(int serverReportedXPSeed) {
        serverReportedXPSeed &= 0x0000fff0;
        for (int highBits = 0; highBits < 65536; highBits++) {
            for (int low4Bits = 0; low4Bits < 16; low4Bits++) {
                possibleXPSeeds.add((highBits << 16) | serverReportedXPSeed | low4Bits);
            }
        }
    }

    public static void addEnchantmentSeedInfo(Level level, EnchantmentMenu menu) {
        CrackState crackState = Configs.enchCrackState;
        if (crackState == CrackState.CRACKED) return;

        ItemStack itemToEnchant = menu.getSlot(0).getItem();
        if (itemToEnchant.isEmpty() || !itemToEnchant.isEnchantable()) return;
        if (enchantingTablePos == null) return;

        BlockPos tablePos = enchantingTablePos;

        if (crackState == CrackState.UNCRACKED) {
            Configs.enchCrackState = CrackState.CRACKING;
            prepareForNextEnchantmentSeedCrack(menu.getEnchantmentSeed());
        }
        int power = getEnchantPower(level, tablePos);

        RandomSource rand = RandomSource.create();
        int[] actualEnchantCosts = menu.costs;
        int[] actualEnchantmentClues = menu.enchantClue;
        int[] actualLevelClues = menu.levelClue;

        Registry<Enchantment> enchantmentRegistry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        IdMap<Holder<Enchantment>> enchantmentIdMap = enchantmentRegistry.asHolderIdMap();

        Iterator<Integer> xpSeedItr = possibleXPSeeds.iterator();
        seedLoop: while (xpSeedItr.hasNext()) {
            int xpSeed = xpSeedItr.next();
            rand.setSeed(xpSeed);

            for (int slot = 0; slot < 3; slot++) {
                int cost = EnchantmentHelper.getEnchantmentCost(rand, slot, power, itemToEnchant);
                if (cost < slot + 1) cost = 0;
                if (cost != actualEnchantCosts[slot]) {
                    xpSeedItr.remove();
                    continue seedLoop;
                }
            }

            for (int slot = 0; slot < 3; slot++) {
                if (actualEnchantCosts[slot] > 0) {
                    List<EnchantmentInstance> enchantments = getEnchantmentList(enchantmentRegistry, rand, xpSeed, itemToEnchant, slot, actualEnchantCosts[slot]);
                    if (enchantments.isEmpty()) {
                        if (actualEnchantmentClues[slot] != -1 || actualLevelClues[slot] != -1) {
                            xpSeedItr.remove();
                            continue seedLoop;
                        }
                    } else {
                        EnchantmentInstance clue = enchantments.get(rand.nextInt(enchantments.size()));
                        if (enchantmentIdMap.getId(clue.enchantment()) != actualEnchantmentClues[slot]
                                || clue.level() != actualLevelClues[slot]) {
                            xpSeedItr.remove();
                            continue seedLoop;
                        }
                    }
                }
            }
        }

        if (possibleXPSeeds.isEmpty()) {
            Configs.enchCrackState = CrackState.UNCRACKED;
            LOGGER.warn("Invalid enchantment seed information. Server mods, desync or client bug?");
        } else if (possibleXPSeeds.size() == 1) {
            Configs.enchCrackState = CrackState.CRACKED;
            addPlayerRNGInfo(possibleXPSeeds.iterator().next());
        }
    }

    private static void addPlayerRNGInfo(int enchantmentSeed) {
        if (Configs.playerCrackState == PlayerRandCracker.CrackState.ENCH_CRACKING_1) {
            firstXpSeed = enchantmentSeed;
            Configs.playerCrackState = PlayerRandCracker.CrackState.HALF_CRACKED;
        } else if (Configs.playerCrackState == PlayerRandCracker.CrackState.ENCH_CRACKING_2) {
            // lattispaghetti
            long max_1 = Integer.toUnsignedLong(firstXpSeed) + 1;
            long min_1 = Integer.toUnsignedLong(firstXpSeed);
            long max_2 = Integer.toUnsignedLong(enchantmentSeed) + 1;
            long a = (24667315 * max_1 + 18218081 * max_2) >> 32;
            long b = (-4824621 * min_1 + 7847617 * max_2) >> 32;

            boolean valid = true;
            long seed = (7847617 * a - 18218081 * b) & PlayerRandCracker.MASK;
            if ((int) (seed >>> 16) != firstXpSeed) valid = false;
            seed = (seed * PlayerRandCracker.MULTIPLIER + PlayerRandCracker.ADDEND) & PlayerRandCracker.MASK;
            if ((int) (seed >>> 16) != enchantmentSeed) valid = false;
            if (valid) {
                PlayerRandCracker.setSeed(seed);
                Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
            } else {
                Configs.playerCrackState = PlayerRandCracker.CrackState.UNCRACKED;
                LOGGER.warn("Invalid player RNG information.");
            }
        }
    }

    private static void onRNGCallEvent(PlayerRandCracker.RNGCallEvent event) {
        if (event.getType() != PlayerRandCracker.RNGCallType.ENCHANTING) {
            LongTask enchantingTask = currentEnchantingTask == null ? null : currentEnchantingTask.get();
            if (enchantingTask != null) {
                if (!isCurrentlyThrowingItems) {
                    ClientCommandHelper.sendError(Component.translatable("commands.cenchant.unexpectedCall", event.getType().getResetMessage()));
                    enchantingTask._break();
                }
            }
            return;
        }

        if (Configs.playerCrackState == PlayerRandCracker.CrackState.UNCRACKED && !isEnchantingPredictionEnabled()) return;

        if (Configs.playerCrackState.knowsSeed()) {
            long prevSeed = PlayerRandCracker.getSeed();
            int xpSeed = PlayerRandCracker.nextInt();
            PlayerRandCracker.setSeed(prevSeed);
            possibleXPSeeds.clear();
            possibleXPSeeds.add(xpSeed);
            Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
            Configs.enchCrackState = CrackState.CRACKED;
            event.setMaintained();
        } else if (Configs.playerCrackState == PlayerRandCracker.CrackState.HALF_CRACKED) {
            possibleXPSeeds.clear();
            Configs.playerCrackState = PlayerRandCracker.CrackState.ENCH_CRACKING_2;
            Configs.enchCrackState = CrackState.UNCRACKED;
            event.setMaintainedEvenIfSeedUnknown();
        } else if (Configs.playerCrackState == PlayerRandCracker.CrackState.UNCRACKED
                || Configs.playerCrackState == PlayerRandCracker.CrackState.ENCH_CRACKING_1
                || Configs.playerCrackState == PlayerRandCracker.CrackState.ENCH_CRACKING_2) {
            possibleXPSeeds.clear();
            Configs.playerCrackState = PlayerRandCracker.CrackState.ENCH_CRACKING_1;
            Configs.enchCrackState = CrackState.UNCRACKED;
            event.setMaintainedEvenIfSeedUnknown();
        }
        doneEnchantment = true;
        expectedNumBookshelves = -1;
    }

    // ----- MANIPULATION -----

    public static String manipulateEnchantments(Item item, Predicate<List<EnchantmentInstance>> enchantmentsPredicate, boolean simulate, Consumer<@Nullable ManipulateResult> callback) throws CommandSyntaxException {
        LocalPlayer player = Minecraft.getInstance().player;
        assert player != null;

        ExecutorService threadPool = Executors.newFixedThreadPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() - (Minecraft.getInstance().hasSingleplayerServer() ? 2 : 1)),
            new ThreadFactoryBuilder().setNameFormat("Enchantment Cracker #%d").build()
        );

        int noDummyXpSeed = Configs.enchCrackState == CrackState.CRACKED ? possibleXPSeeds.iterator().next() : 0;

        ItemStack stack = new ItemStack(item);
        long playerSeed = PlayerRandCracker.getSeed();
        Registry<Enchantment> enchantmentRegistry = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

        List<CompletableFuture<@Nullable ManipulateResult>> futures = new ArrayList<>();

        for (int i = Configs.enchCrackState == CrackState.CRACKED ? ManipulateResult.NO_DUMMY : 0;
             i < (Configs.playerCrackState.knowsSeed() ? Configs.getMaxEnchantItemThrows() : 0);
             i++
        ) {
            int times = i;
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    Rand playerRand = new Rand(LCG.JAVA, playerSeed);
                    playerRand.advance(Math.max(times, 0) * 4L);
                    int xpSeed = times == ManipulateResult.NO_DUMMY ? noDummyXpSeed : (int) playerRand.nextBits(32);

                    int[] enchantLevels = new int[3];
                    RandomSource rand = RandomSource.create();
                    for (int bookshelvesNeeded = Configs.getMinEnchantBookshelves(); bookshelvesNeeded <= Configs.getMaxEnchantBookshelves(); bookshelvesNeeded++) {
                        rand.setSeed(xpSeed);
                        for (int slot = 0; slot < 3; slot++) {
                            int level = EnchantmentHelper.getEnchantmentCost(rand, slot, bookshelvesNeeded, stack);
                            if (level < slot + 1) level = 0;
                            enchantLevels[slot] = level;
                        }
                        int maxEnchantSlot = Configs.getMaxEnchantSlot();
                        for (int slot = 0; slot < maxEnchantSlot; slot++) {
                            List<EnchantmentInstance> enchantments = getEnchantmentList(enchantmentRegistry, rand, xpSeed, stack, slot, enchantLevels[slot]);
                            if (enchantmentsPredicate.test(enchantments)
                                && enchantLevels[slot] >= Configs.getMinEnchantLevels()
                                && enchantLevels[slot] <= Configs.getMaxEnchantLevels()
                            ) {
                                return new ManipulateResult(times, bookshelvesNeeded, slot, enchantments);
                            }
                        }
                    }
                } catch (Throwable e) {
                    LOGGER.error("An error occurred simulating enchantments", e);
                }
                return null;
            }, threadPool));
        }

        LongTaskList taskList = new LongTaskList() {
            @Override
            public Set<Object> getMutexKeys() {
                return simulate ? Set.of(EnchantmentCracker.class) : Set.of(EnchantmentCracker.class, ItemThrowTask.class);
            }
            @Override
            public void onCompleted() {
                super.onCompleted();
                currentEnchantingTask = null;
            }
        };
        currentEnchantingTask = new WeakReference<>(taskList);

        taskList.addTask(new SimpleTask() {
            private int index = 0;
            @Nullable
            ManipulateResult finalResult = null;
            private boolean hasShutDown = false;

            @Override
            protected void onTick() {
                while (index < futures.size()) {
                    var future = futures.get(index);
                    if (!future.isDone()) break;
                    ManipulateResult result = future.join();
                    if (result != null) {
                        finalResult = result;
                        _break();
                        return;
                    }
                    index++;
                }
            }

            @Override
            public boolean condition() { return index < futures.size(); }

            @Override
            public boolean stopOnLevelUnload(boolean isDisconnect) {
                if (!hasShutDown) { threadPool.shutdownNow(); hasShutDown = true; }
                return true;
            }

            @Override
            public void onCompleted() {
                if (!hasShutDown) { threadPool.shutdownNow(); hasShutDown = true; }

                if (!simulate && finalResult != null) {
                    doneEnchantment = false;
                    int timesNeeded = finalResult.itemThrows();
                    if (timesNeeded != ManipulateResult.NO_DUMMY) {
                        if (timesNeeded != 0) {
                            player.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 90);
                            player.connection.send(new ServerboundMovePlayerPacket.Rot(player.getYRot(), 90, player.onGround(), player.horizontalCollision));
                        }
                        if (timesNeeded > 0) {
                            isCurrentlyThrowingItems = true;
                            taskList.addTask(new ItemThrowTask(timesNeeded, ItemThrowTask.FLAG_WAIT_FOR_ITEMS) {
                                @Override
                                public void onCompleted() {
                                    super.onCompleted();
                                    LocalPlayer p = Minecraft.getInstance().player;
                                    if (p != null) p.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 2.0f);
                                    isCurrentlyThrowingItems = false;
                                }

                                @Override
                                protected void onFailedToThrowItem(PlayerRandCracker.ThrowItemsResult throwItemsResult) {
                                    super.onFailedToThrowItem(throwItemsResult);
                                    if (throwItemsResult.getType() != PlayerRandCracker.ThrowItemsResult.Type.NOT_ENOUGH_ITEMS) {
                                        taskList._break();
                                    }
                                }

                                @Override
                                protected void onUnexpectedRNGCall(PlayerRandCracker.RNGCallType callType) {
                                    ClientCommandHelper.sendError(Component.translatable("commands.cenchant.unexpectedCall", callType.getResetMessage()));
                                    taskList._break();
                                }

                                @Override
                                protected void onItemThrown(int current, int total) {
                                    MutableComponent builder = Component.empty();
                                    int color = Mth.hsvToRgb(current / (total * 3.0f), 1.0f, 1.0f);
                                    builder.append(Component.literal("[").withColor(0xAAAAAA));
                                    builder.append(Component.literal("~" + Math.round(100.0 * current / total) + "%").withColor(color));
                                    builder.append(Component.literal("] ").withColor(0xAAAAAA));
                                    int filledWidth = (int) Math.round((double) PROGRESS_BAR_WIDTH * current / total);
                                    int unfilledWidth = PROGRESS_BAR_WIDTH - filledWidth;
                                    builder.append(Component.literal("|".repeat(filledWidth)).withColor(color));
                                    builder.append(Component.literal("|".repeat(unfilledWidth)).withColor(0xAAAAAA));

                                    Minecraft.getInstance().gui.setOverlayMessage(builder, false);
                                }
                            });
                        }
                        // dummy enchantment wait
                        taskList.addTask(new LongTask() {
                            @Override
                            public void initialize() { ClientCommandHelper.sendFeedback("enchCrack.insn.dummy"); }
                            @Override public boolean condition() { return !doneEnchantment; }
                            @Override public void increment() {}
                            @Override public void body() { scheduleDelay(); }
                            @Override public String toString() { return "Enchantment Cracker Wait Dummy"; }
                        });
                    }

                    taskList.addTask(new OneTickTask() {
                        @Override
                        public void run() {
                            if (Configs.enchCrackState == CrackState.CRACKED) {
                                ClientCommandHelper.sendFeedback(Component.translatable("enchCrack.insn.ready").withStyle(ChatFormatting.BOLD));
                                ClientCommandHelper.sendFeedback("enchCrack.insn.bookshelves", finalResult.bookshelves);
                                ClientCommandHelper.sendFeedback("enchCrack.insn.slot", finalResult.slot + 1);
                                expectedNumBookshelves = finalResult.bookshelves;
                            }
                        }
                        @Override public String toString() { return "Enchantment Cracker Done Message"; }
                    });
                }

                callback.accept(finalResult);
            }

            @Override
            public String toString() { return "Enchantment Cracker Worker Poller"; }
        });

        return TaskManager.addTask("enchantmentCracker", taskList);
    }

    // ----- HELPERS -----

    public static boolean isEnchantingPredictionEnabled() { return Configs.enchantingPrediction; }

    private static int getEnchantPower(Level level, BlockPos tablePos) {
        int power = 0;
        for (BlockPos bookshelfOffset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
            if (EnchantingTableBlock.isValidBookShelf(level, tablePos, bookshelfOffset)) {
                power++;
            }
        }
        return power;
    }

    private static List<EnchantmentInstance> getEnchantmentList(Registry<Enchantment> enchantmentRegistry, RandomSource rand, int xpSeed, ItemStack stack, int enchantSlot, int level) {
        rand.setSeed(xpSeed + enchantSlot);
        var tag = enchantmentRegistry.getTagOrEmpty(EnchantmentTags.IN_ENCHANTING_TABLE);
        List<EnchantmentInstance> list = EnchantmentHelper.selectEnchantment(rand, stack, level, StreamSupport.stream(tag.spliterator(), false));

        if (stack.getItem() == Items.BOOK && list.size() > 1) {
            list.remove(rand.nextInt(list.size()));
        }
        return list;
    }

    @Nullable
    public static List<EnchantmentInstance> getEnchantmentsInTable(int slot) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;
        Registry<Enchantment> enchantmentRegistry = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

        CrackState crackState = Configs.enchCrackState;

        if (!(player.containerMenu instanceof EnchantmentMenu enchMenu)) return null;

        if (crackState != CrackState.CRACKED) {
            if (enchMenu.enchantClue[slot] == -1) return null;
            Holder<Enchantment> enchantment = enchantmentRegistry.asHolderIdMap().byId(enchMenu.enchantClue[slot]);
            if (enchantment == null) return null;
            return new ArrayList<>(Collections.singletonList(new EnchantmentInstance(enchantment, enchMenu.levelClue[slot])));
        } else {
            if (possibleXPSeeds.isEmpty()) return null;
            RandomSource rand = RandomSource.create();
            int xpSeed = possibleXPSeeds.iterator().next();
            ItemStack enchantingStack = enchMenu.getSlot(0).getItem();
            int enchantLevels = enchMenu.costs[slot];
            return getEnchantmentList(enchantmentRegistry, rand, xpSeed, enchantingStack, slot, enchantLevels);
        }
    }

    public static void sortIntoTooltipOrder(Registry<Enchantment> enchantmentRegistry, List<EnchantmentInstance> list) {
        var tooltipOrder = enchantmentRegistry.getTagOrEmpty(EnchantmentTags.TOOLTIP_ORDER);
        Object2IntMap<Holder<Enchantment>> tooltipIndex = new Object2IntOpenHashMap<>();
        int index = 0;
        for (Holder<Enchantment> ench : tooltipOrder) tooltipIndex.put(ench, index++);
        list.sort(Comparator.comparingInt(ench -> tooltipIndex.getInt(ench.enchantment())));
    }

    public record ManipulateResult(int itemThrows, int bookshelves, int slot, List<EnchantmentInstance> enchantments) {
        public static final int NO_DUMMY = -1;
    }

    public enum CrackState implements StringRepresentable {
        UNCRACKED("uncracked"), CRACKED("cracked"), CRACKING("cracking");
        private final String name;
        CrackState(String name) { this.name = name; }
        @Override public String getSerializedName() { return name; }
    }
}
