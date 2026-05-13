package net.earthcomputer.enchantpredict.features;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.enchantpredict.Configs;
import net.earthcomputer.enchantpredict.command.ClientCommandHelper;
import net.earthcomputer.enchantpredict.task.ItemThrowTask;
import net.earthcomputer.enchantpredict.task.TaskManager;
import net.earthcomputer.enchantpredict.util.CComponentUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/**
 * /ccrackrng implementation — direct port of {@code clientcommands} CCrackRng.
 *
 * <p>Uses the latticg-based seedfinder ({@link CCrackRngGen}) to recover the
 * 48-bit Java LCG seed from 10 successive item-throw velocities.</p>
 */
public class CCrackRng {
    public static final int NUM_THROWS = 10;
    public static final float MAX_ERROR = 0.00883889f;

    @FunctionalInterface
    public interface OnCrack { void callback(long seed); }

    public static OnCrack callback;
    public static float[] nextFloats = new float[NUM_THROWS];
    public static int expectedItems = 0;
    private static int attemptCount = 0;
    private static final int MAX_ATTEMPTS = 5;

    private static String throwItems() throws CommandSyntaxException {
        LocalPlayer player = Minecraft.getInstance().player;
        assert player != null;
        player.absMoveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 90);
        player.connection.send(new ServerboundMovePlayerPacket.Rot(player.getYRot(), 90, player.onGround()));
        ItemThrowTask task = new ItemThrowTask(NUM_THROWS) {
            @Override
            protected void onSuccess() {
                try { CCrackRng.attemptCrack(); }
                catch (CommandSyntaxException e) {
                    ClientCommandHelper.sendError(ComponentUtils.fromMessage(e.getRawMessage()));
                }
            }
            @Override
            protected void onFailedToThrowItem(PlayerRandCracker.ThrowItemsResult throwItemsResult) {
                super.onFailedToThrowItem(throwItemsResult);
                Configs.playerCrackState = PlayerRandCracker.CrackState.UNCRACKED;
            }
            @Override
            protected void onUnexpectedRNGCall(PlayerRandCracker.RNGCallType callType) {
                ClientCommandHelper.sendError(Component.translatable("commands.ccrackrng.failed.unexpectedCall", callType.getResetMessage()));
            }
            @Override protected boolean requireCrackedRNG() { return false; }
            @Override protected void onItemSpawn(ClientboundAddEntityPacket packet) { onEntityCreation(packet); }
        };
        return TaskManager.addTask("ccrackrng", task);
    }

    public static void attemptCrack() throws CommandSyntaxException {
        long[] seeds = CCrackRngGen.getSeeds(
            Math.max(0, nextFloats[0] - MAX_ERROR), Math.min(1, nextFloats[0] + MAX_ERROR),
            Math.max(0, nextFloats[1] - MAX_ERROR), Math.min(1, nextFloats[1] + MAX_ERROR),
            Math.max(0, nextFloats[2] - MAX_ERROR), Math.min(1, nextFloats[2] + MAX_ERROR),
            Math.max(0, nextFloats[3] - MAX_ERROR), Math.min(1, nextFloats[3] + MAX_ERROR),
            Math.max(0, nextFloats[4] - MAX_ERROR), Math.min(1, nextFloats[4] + MAX_ERROR),
            Math.max(0, nextFloats[5] - MAX_ERROR), Math.min(1, nextFloats[5] + MAX_ERROR),
            Math.max(0, nextFloats[6] - MAX_ERROR), Math.min(1, nextFloats[6] + MAX_ERROR),
            Math.max(0, nextFloats[7] - MAX_ERROR), Math.min(1, nextFloats[7] + MAX_ERROR),
            Math.max(0, nextFloats[8] - MAX_ERROR), Math.min(1, nextFloats[8] + MAX_ERROR),
            Math.max(0, nextFloats[9] - MAX_ERROR), Math.min(1, nextFloats[9] + MAX_ERROR)
        ).toArray();

        if (seeds.length != 1) {
            attemptCount++;
            if (attemptCount > MAX_ATTEMPTS) {
                ClientCommandHelper.sendError(Component.translatable("commands.ccrackrng.failed"));
                ClientCommandHelper.sendHelp(Component.translatable("commands.ccrackrng.failed.help"));
                Configs.playerCrackState = PlayerRandCracker.CrackState.UNCRACKED;
            } else {
                CCrackRng.doCrack(CCrackRng.callback);
            }
            return;
        }
        Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
        callback.callback(seeds[0]);
    }

    public static void crack(OnCrack callback) throws CommandSyntaxException {
        attemptCount = 1;
        doCrack(callback);
    }

    private static void doCrack(OnCrack cb) throws CommandSyntaxException {
        callback = cb;
        ClientCommandHelper.addOverlayMessage(Component.translatable("commands.ccrackrng.retries", attemptCount, MAX_ATTEMPTS), 100);
        String currentTaskName = throwItems();
        Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKING;
        expectedItems = NUM_THROWS;
        if (attemptCount == 1) {
            Component message = Component.translatable("commands.ccrackrng.starting")
                .append(" ")
                .append(CComponentUtil.getCommandTextComponent("commands.client.cancel", "/ctask stop " + currentTaskName));
            ClientCommandHelper.sendFeedback(message);
        }
    }

    public static void onEntityCreation(ClientboundAddEntityPacket packet) {
        if (Configs.playerCrackState == PlayerRandCracker.CrackState.CRACKING) {
            if (expectedItems > 0) {
                // In 1.21.1 the packet exposes the raw velocity as double via getXa/getYa/getZa.
                float nextFloat = (float) Math.sqrt(packet.getXa() * packet.getXa() + packet.getZa() * packet.getZa()) * 50f;
                nextFloats[NUM_THROWS - expectedItems] = nextFloat;
                expectedItems--;
            }
        }
    }
}
