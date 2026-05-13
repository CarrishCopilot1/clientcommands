package net.earthcomputer.enchantpredict.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.earthcomputer.enchantpredict.Configs;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * /cconfig command — minimal port preserving the operation form
 * {@code /cconfig enchantpredict <key> [set <value>]}, matching the original
 * {@code /cconfig clientcommands &lt;key&gt; ...} flow.
 */
public final class CConfigCommand {

    @FunctionalInterface
    private interface ValueRegistrar {
        void register(LiteralArgumentBuilder<CommandSourceStack> key);
    }

    private static final Map<String, ValueRegistrar> ENTRIES = new LinkedHashMap<>();

    static {
        addBool("enchantingPrediction", () -> Configs.enchantingPrediction, Configs::setEnchantingPrediction);
        addBool("playerRNGMaintenance", () -> Configs.playerRNGMaintenance, Configs::setPlayerRNGMaintenance);
        addBool("toolBreakWarning", () -> Configs.toolBreakWarning, Configs::setToolBreakWarning);
        addInt("maxEnchantItemThrows", 0, 1_000_000, Configs::getMaxEnchantItemThrows, Configs::setMaxEnchantItemThrows);
        addInt("minEnchantBookshelves", 0, 15, Configs::getMinEnchantBookshelves, Configs::setMinEnchantBookshelves);
        addInt("maxEnchantBookshelves", 0, 15, Configs::getMaxEnchantBookshelves, Configs::setMaxEnchantBookshelves);
        addInt("minEnchantLevels", 1, 30, Configs::getMinEnchantLevels, Configs::setMinEnchantLevels);
        addInt("maxEnchantLevels", 1, 30, Configs::getMaxEnchantLevels, Configs::setMaxEnchantLevels);
        addInt("maxEnchantSlot", 1, 3, Configs::getMaxEnchantSlot, Configs::setMaxEnchantSlot);
        addFloat("itemThrowsPerTick", 0f, 20f, () -> Configs.itemThrowsPerTick, Configs::setItemThrowsPerTick);
    }

    private static void addBool(String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        ENTRIES.put(key, lit -> lit
            .executes(ctx -> get(key, getter.get()))
            .then(Commands.literal("set").then(Commands.argument("value", BoolArgumentType.bool())
                .executes(ctx -> set(key, BoolArgumentType.getBool(ctx, "value"), setter)))));
    }

    private static void addInt(String key, int min, int max, Supplier<Integer> getter, Consumer<Integer> setter) {
        ENTRIES.put(key, lit -> lit
            .executes(ctx -> get(key, getter.get()))
            .then(Commands.literal("set").then(Commands.argument("value", IntegerArgumentType.integer(min, max))
                .executes(ctx -> set(key, IntegerArgumentType.getInteger(ctx, "value"), setter)))));
    }

    private static void addFloat(String key, float min, float max, Supplier<Float> getter, Consumer<Float> setter) {
        ENTRIES.put(key, lit -> lit
            .executes(ctx -> get(key, getter.get()))
            .then(Commands.literal("set").then(Commands.argument("value", FloatArgumentType.floatArg(min, max))
                .executes(ctx -> set(key, FloatArgumentType.getFloat(ctx, "value"), setter)))));
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("cconfig").then(buildSubtree());
        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSubtree() {
        LiteralArgumentBuilder<CommandSourceStack> mod = Commands.literal("enchantpredict");
        for (var e : ENTRIES.entrySet()) {
            LiteralArgumentBuilder<CommandSourceStack> key = Commands.literal(e.getKey());
            e.getValue().register(key);
            mod.then(key);
        }
        return mod;
    }

    private static int get(String key, Object value) {
        ClientCommandHelper.sendFeedback(Component.translatable("commands.cconfig.get", key, String.valueOf(value)));
        return Command.SINGLE_SUCCESS;
    }

    private static <T> int set(String key, T value, Consumer<T> setter) {
        setter.accept(value);
        ClientCommandHelper.sendFeedback(Component.translatable("commands.cconfig.set", key, String.valueOf(value)));
        return Command.SINGLE_SUCCESS;
    }
}
