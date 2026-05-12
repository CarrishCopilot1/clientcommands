package net.earthcomputer.enchantpredict.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.enchantpredict.Configs;
import net.earthcomputer.enchantpredict.features.CCrackRng;
import net.earthcomputer.enchantpredict.features.PlayerRandCracker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * /ccrackrng command — port of clientcommands {@code CrackRNGCommand}.
 *
 * <p>{@code ServerBrandManager.rngWarning()} is intentionally not invoked: the
 * server-brand subsystem is not part of this single-purpose port.</p>
 */
public class CrackRNGCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ccrackrng")
            .executes(ctx -> crackPlayerRNG(ctx.getSource())));
    }

    private static int crackPlayerRNG(CommandSourceStack source) throws CommandSyntaxException {
        CCrackRng.crack(seed -> {
            ClientCommandHelper.sendFeedback(Component.translatable("commands.ccrackrng.success", Long.toHexString(seed)));
            PlayerRandCracker.setSeed(seed);
            Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
        });
        return Command.SINGLE_SUCCESS;
    }
}
