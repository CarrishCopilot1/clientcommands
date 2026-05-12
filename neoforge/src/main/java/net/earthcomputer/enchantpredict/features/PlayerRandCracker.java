package net.earthcomputer.enchantpredict.features;

import net.earthcomputer.enchantpredict.Configs;
import net.earthcomputer.enchantpredict.command.ClientCommandHelper;
import net.earthcomputer.enchantpredict.event.ClientLevelEvents;
import net.earthcomputer.enchantpredict.event.Event;
import net.earthcomputer.enchantpredict.interfaces.ICreativeSlot;
import net.earthcomputer.enchantpredict.util.CComponentUtil;
import net.earthcomputer.enchantpredict.util.CUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Player-RNG cracker — port of {@code clientcommands} {@link
 * net.earthcomputer.enchantpredict.features.PlayerRandCracker}.
 *
 * <p>Adaptations for Minecraft 1.21.1:</p>
 * <ul>
 *   <li>Removed the 1.21.2+ {@code Consumable}, {@code Equippable} and
 *       {@code ItemUseAnimation} types — uses {@link UseAnim} and {@link ArmorItem}
 *       instead.</li>
 *   <li>Removed {@code MultiVersionCompat} branches: behaviour is fixed to the
 *       1.21.1 random-call profile (unbreaking does not call random on 1.21+).</li>
 *   <li>Item-drop throttling check ({@code player.canDropItems()}) was added in
 *       1.21.2 and is dropped here.</li>
 *   <li>Inventory throw uses {@link ClickType#THROW} rather than the 1.21.2
 *       {@code ContainerInput.THROW}.</li>
 * </ul>
 */
public class PlayerRandCracker {

    // ===== RNG IMPLEMENTATION ===== //

    public static final long MULTIPLIER = 0x5deece66dL;
    public static final long ADDEND = 0xbL;
    public static final long MASK = (1L << 48) - 1;

    private static long seed;

    private static int next(int bits) {
        seed = (seed * MULTIPLIER + ADDEND) & MASK;
        return (int) (seed >>> (48 - bits));
    }

    public static int nextInt() { return next(32); }

    public static int nextInt(int bound) {
        if ((bound & -bound) == bound) return (int) ((bound * (long) next(31)) >> 31);
        int bits, val;
        do { bits = next(31); val = bits % bound; } while (bits - val + (bound - 1) < 0);
        return val;
    }

    public static float nextFloat() { return next(24) / (float) (1 << 24); }

    public static void setSeed(long s) { PlayerRandCracker.seed = s; }
    public static long getSeed() { return seed; }

    // ===== RESET DETECTION + RNG MAINTENANCE ===== //

    public static final Event<RNGCallListener> RNG_CALLED_EVENT = Event.createArrayBacked(RNGCallListener.class, listeners -> event -> {
        for (RNGCallListener listener : listeners) listener.onCall(event);
    });

    public static boolean isPredictingBlockBreaking = false;
    @Nullable
    private static Runnable postBlockBreakPredictAction = null;

    public static void registerEvents() {
        ClientLevelEvents.LOAD_LEVEL.register(level -> resetCracker(RNGCallType.RECREATED));
        RNG_CALLED_EVENT.register(PlayerRandCracker::throwItemsUntilOnRNGCallEvent);
    }

    public static void postSendBlockBreakingPredictionPacket() {
        if (postBlockBreakPredictAction != null) {
            postBlockBreakPredictAction.run();
            postBlockBreakPredictAction = null;
        }
    }

    public static void resetCracker(RNGCallType reason) { resetCracker(reason, true); }

    private static void resetCracker(RNGCallType reason, boolean isResettingUnconditionally) {
        if (isResettingUnconditionally) {
            RNG_CALLED_EVENT.invoker().onCall(new RNGCallEvent(reason, false));
        }
        if (Configs.playerCrackState != CrackState.UNCRACKED) {
            ClientCommandHelper.sendError(Component.translatable("playerManip.reset", reason.resetMessage));
            Configs.playerCrackState = CrackState.UNCRACKED;
        }
    }

    public static void onDropItem() {
        if (canMaintainPlayerRNG(RNGCallType.DROP_ITEM)) {
            for (int i = 0; i < 4; i++) nextInt();
        } else {
            resetCracker(RNGCallType.DROP_ITEM, false);
        }
    }

    /** Called by {@code LivingEntityMixin} when the player finishes using a food/drink stack. */
    public static void onConsume(ItemStack stack, Vec3 pos, int particleCount, int itemUseTimeLeft) {
        UseAnim animation = stack.getUseAnimation();
        // On 1.21.1, only EAT actually contains random calls during the use animation
        // (DRINK got its own random calls starting in 1.21.2). Mirror that here.
        if (animation != UseAnim.EAT) return;

        RNGCallType callType = RNGCallType.FOOD;

        if (canMaintainPlayerRNG(callType)) {
            if (itemUseTimeLeft < 0 && particleCount != 16) return;

            // random calls for the consume sounds
            for (int i = 0; i < 3; i++) nextInt();
            // random calls for the eating particles
            for (int i = 0; i < particleCount * 3; i++) nextInt();
        } else {
            resetCracker(callType, false);
        }
    }

    public static void onEquipItem() {
        if (canMaintainPlayerRNG(RNGCallType.EQUIP_ITEM)) {
            nextInt();
            nextInt();
        } else {
            resetCracker(RNGCallType.EQUIP_ITEM, false);
        }
    }

    public static void onAnvilUse() {
        if (canMaintainPlayerRNG(RNGCallType.ANVIL)) nextInt();
        else resetCracker(RNGCallType.ANVIL, false);
    }

    public static void onCrossbowUse() {
        if (canMaintainPlayerRNG(RNGCallType.CROSSBOW)) nextInt();
        else resetCracker(RNGCallType.CROSSBOW, false);
    }

    public static void onXpOrb() {
        // No deterministic way to know how many ticks of XP merge will happen — just reset.
        resetCracker(RNGCallType.XP);
    }

    public static void onItemDamageUncertain(int minAmount, int maxAmount, LivingEntity holder, ItemStack stack) {
        // On 1.21+ unbreaking no longer consumes random calls, so all this needs to do is the
        // tool break warning.
        if (holder instanceof LocalPlayer player && !player.getAbilities().instabuild) {
            if (stack.isDamageableItem() && maxAmount > 0) {
                handleToolBreakWarning(maxAmount, stack, player);
            }
        }
    }

    public static void onItemDamage(int amount, LivingEntity holder, ItemStack stack) {
        if (holder instanceof LocalPlayer player && !player.getAbilities().instabuild) {
            if (stack.isDamageableItem() && amount > 0) {
                handleToolBreakWarning(amount, stack, player);
            }
        }
    }

    private static void handleToolBreakWarning(int amount, ItemStack stack, LocalPlayer player) {
        if (Configs.toolBreakWarning && stack.getDamageValue() + amount >= stack.getMaxDamage() - 30) {
            if (stack.getDamageValue() + amount >= stack.getMaxDamage() - 15) {
                player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 10, 0.1f);
            }
            MutableComponent durability = Component.literal(String.valueOf(stack.getMaxDamage() - stack.getDamageValue() - 1)).withStyle(ChatFormatting.RED);
            Minecraft.getInstance().gui.setOverlayMessage(
                Component.translatable("playerManip.toolBreakWarning", durability).withStyle(ChatFormatting.GOLD),
                false);
        }
    }

    public static void onEnchantedItem() {
        if (canMaintainPlayerRNG(RNGCallType.ENCHANTING)) nextInt();
        else resetCracker(RNGCallType.ENCHANTING, false);
    }

    private static boolean canMaintainPlayerRNG(RNGCallType callType) {
        RNGCallEvent event = new RNGCallEvent(callType, Configs.playerRNGMaintenance && Configs.playerCrackState.knowsSeed());
        RNG_CALLED_EVENT.invoker().onCall(event);
        if (event.isMaintained && Configs.playerCrackState.knowsSeed()) {
            Configs.playerCrackState = CrackState.CRACKED;
            return true;
        } else {
            return event.isMaintainedEvenIfSeedUnknown;
        }
    }

    // ===== UTILITIES ===== //

    private static boolean isThrowItemsUntilThrowingItem = false;

    private static void throwItemsUntilOnRNGCallEvent(RNGCallEvent event) {
        if (isThrowItemsUntilThrowingItem && event.type == RNGCallType.DROP_ITEM) {
            event.setMaintained();
        }
    }

    public static ThrowItemsResult throwItemsUntil(Predicate<Random> condition, int max) {
        if (!Configs.playerCrackState.knowsSeed()) return new ThrowItemsResult(ThrowItemsResult.Type.UNKNOWN_SEED);
        Configs.playerCrackState = CrackState.CRACKED;

        long s = PlayerRandCracker.seed;
        Random rand = new Random(s ^ MULTIPLIER);

        int itemsNeeded = 0;
        for (; itemsNeeded <= max && !condition.test(rand); itemsNeeded++) {
            for (int i = 0; i < 4; i++) s = (s * MULTIPLIER + ADDEND) & MASK;
            rand.setSeed(s ^ MULTIPLIER);
        }
        if (itemsNeeded > max) return new ThrowItemsResult(ThrowItemsResult.Type.NOT_POSSIBLE, itemsNeeded);
        for (int i = 0; i < itemsNeeded; i++) {
            ThrowItemsResult result;
            isThrowItemsUntilThrowingItem = true;
            try { result = throwItem(); }
            finally { isThrowItemsUntilThrowingItem = false; }
            if (!result.isSuccess()) return result;
        }
        return new ThrowItemsResult(ThrowItemsResult.Type.SUCCESS);
    }

    public static ThrowItemsResult throwItem() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        MultiPlayerGameMode interactionManager = mc.gameMode;
        if (player == null || interactionManager == null) return new ThrowItemsResult(ThrowItemsResult.Type.THROTTLED);

        boolean isInContainer = mc.screen instanceof AbstractContainerScreen && !(mc.screen instanceof CreativeModeInventoryScreen);
        boolean useCreativeThrow = player.hasInfiniteMaterials() && !isInContainer;
        if (useCreativeThrow) {
            ItemStack stackToDrop = new ItemStack(Items.COBBLESTONE);
            player.drop(stackToDrop, true);
            interactionManager.handleCreativeModeItemDrop(stackToDrop);
            return new ThrowItemsResult(ThrowItemsResult.Type.SUCCESS);
        }

        Slot matchingSlot = getBestItemThrowSlot(player.containerMenu.slots);
        if (matchingSlot == null) return new ThrowItemsResult(ThrowItemsResult.Type.NOT_ENOUGH_ITEMS);
        interactionManager.handleInventoryMouseClick(
            player.containerMenu.containerId,
            matchingSlot.index, 0, ClickType.THROW, player);
        return new ThrowItemsResult(ThrowItemsResult.Type.SUCCESS);
    }

    public static void unthrowItem() {
        seed = (seed * 0xdba6ed0471f1L + 0x25493d2c3b3cL) & MASK;
    }

    @Nullable
    public static Slot getBestItemThrowSlot(List<Slot> slots) {
        slots = slots.stream().filter(slot -> {
            if (!slot.hasItem()) return false;
            if (slot instanceof ICreativeSlot) return false;
            if (EnchantmentHelper.has(slot.getItem(), EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) return false;
            if (slot.getItem().getItem() == Items.CHORUS_FRUIT) return false;
            return true;
        }).collect(Collectors.toList());

        Map<Item, Integer> itemCounts = new HashMap<>();
        for (Slot slot : slots) {
            itemCounts.merge(slot.getItem().getItem(), slot.getItem().getCount(), Integer::sum);
        }
        if (itemCounts.isEmpty()) return null;
        Item preferredItem = itemCounts.keySet().stream().max(
            Comparator.comparingInt(Item::getDefaultMaxStackSize).thenComparing(itemCounts::get)
        ).get();
        return slots.stream().filter(slot -> slot.getItem().getItem() == preferredItem).findFirst().get();
    }

    @Nullable
    private static final Field RANDOM_SEED;
    static {
        Field randomSeedField;
        try {
            randomSeedField = Random.class.getDeclaredField("seed");
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
        try {
            randomSeedField.setAccessible(true);
        } catch (Exception e) {
            randomSeedField = null;
        }
        RANDOM_SEED = randomSeedField;
    }

    public static OptionalLong getSeed(Random rand) {
        if (RANDOM_SEED == null) return OptionalLong.empty();
        try {
            return OptionalLong.of(((AtomicLong) RANDOM_SEED.get(rand)).get());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public static class ThrowItemsResult {
        private final Type type;
        private final Object[] messageArgs;

        public ThrowItemsResult(Type type, Object... messageArgs) {
            this.type = type;
            this.messageArgs = messageArgs;
        }

        public boolean isSuccess() { return type.success; }
        public Type getType() { return type; }

        public void sendErrorMessage() {
            for (MutableComponent message : type.messageCreator.apply(messageArgs)) {
                ClientCommandHelper.sendFeedback(message);
            }
        }

        public enum Type {
            NOT_ENOUGH_ITEMS(false, args -> List.of(
                Component.translatable("playerManip.notEnoughItems", args).withStyle(ChatFormatting.RED),
                Component.translatable("playerManip.notEnoughItems.help").withStyle(ChatFormatting.AQUA)
            )),
            NOT_POSSIBLE(false, "playerManip.throwError"),
            THROTTLED(false, args -> List.of(
                Component.translatable("playerManip.throttled", args).withStyle(ChatFormatting.RED),
                Component.translatable("playerManip.throttled.help").withStyle(ChatFormatting.AQUA)
            )),
            UNKNOWN_SEED(false, args -> List.of(Component.translatable("playerManip.uncracked")
                .append(" ")
                .append(CComponentUtil.getCommandTextComponent("commands.client.crack", "/ccrackrng"))
                .withStyle(ChatFormatting.RED))),
            SUCCESS(true, (Function<Object[], List<MutableComponent>>) null),
            ;
            private final boolean success;
            private final Function<Object[], List<MutableComponent>> messageCreator;
            Type(boolean success, String translationKey) {
                this(success, args -> List.of(Component.translatable(translationKey, args).withStyle(ChatFormatting.RED)));
            }
            Type(boolean success, Function<Object[], List<MutableComponent>> messageCreator) {
                this.success = success;
                this.messageCreator = messageCreator;
            }
        }
    }

    public enum CrackState implements StringRepresentable {
        UNCRACKED("uncracked"),
        CRACKED("cracked", true),
        ENCH_CRACKING_1("ench_cracking_1"),
        HALF_CRACKED("half_cracked"),
        ENCH_CRACKING_2("ench_cracking_2"),
        CRACKING("cracking"),
        EATING("eating");

        private final String name;
        private final boolean knowsSeed;
        CrackState(String name) { this(name, false); }
        CrackState(String name, boolean knowsSeed) { this.name = name; this.knowsSeed = knowsSeed; }
        @Override public String getSerializedName() { return name; }
        public boolean knowsSeed() { return knowsSeed; }
    }

    @FunctionalInterface
    public interface RNGCallListener {
        void onCall(RNGCallEvent event);
    }

    public static final class RNGCallEvent {
        private final RNGCallType type;
        private boolean isMaintained;
        private boolean isMaintainedEvenIfSeedUnknown = false;

        public RNGCallEvent(RNGCallType type, boolean isMaintained) {
            this.type = type;
            this.isMaintained = isMaintained;
        }
        public RNGCallType getType() { return type; }
        public void setMaintained() { this.isMaintained = true; }
        public void setMaintainedEvenIfSeedUnknown() { this.isMaintainedEvenIfSeedUnknown = true; }
    }

    public enum RNGCallType {
        ADVANCEMENT("advancement"),
        AMETHYST_CHIME("amethystChime"),
        ANVIL("anvil"),
        BANE_OF_ARTHROPODS("baneOfArthropods"),
        CONSUME("consume"),
        CROSSBOW("crossbow"),
        DRINK("drink"),
        DROP_ITEM("dropItem"),
        ENCHANTING("enchanting"),
        ENTER_WATER("enterWater"),
        ENTITY_CRAMMING("entityCramming"),
        EQUIP_ITEM("equipItem"),
        FALL_FLYING("fallFlying"),
        FOOD("food"),
        FROST_WALKER("frostWalker"),
        GIVE("give"),
        ITEM_BREAK("itemBreak"),
        MENDING("mending"),
        PLAYER_HURT("playerHurt"),
        POTION("potion"),
        RECREATED("recreated"),
        RESPIRATION("respiration"),
        SHIELD("shield"),
        SHOULDER_PARROT("shoulderParrot"),
        SOUL_SPEED("soulSpeed"),
        SPRINT("sprint"),
        SWIM("swim"),
        UNBREAKING("unbreaking"),
        XP("xp");

        private final Component resetMessage;
        RNGCallType(String resetMessage) {
            this.resetMessage = Component.translatable("playerManip.reset." + resetMessage);
        }
        public Component getResetMessage() { return resetMessage; }
    }
}
