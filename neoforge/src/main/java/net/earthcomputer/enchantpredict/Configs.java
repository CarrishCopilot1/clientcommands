package net.earthcomputer.enchantpredict;

import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Locale;

/**
 * Configuration facade for the enchantment-prediction mod.
 *
 * <p>Keeps the same static field surface as the original
 * {@code net.earthcomputer.clientcommands.Configs} so that the ported feature
 * classes ({@link net.earthcomputer.enchantpredict.features.EnchantmentCracker},
 * {@link net.earthcomputer.enchantpredict.features.PlayerRandCracker}, &hellip;)
 * keep their original call-sites.</p>
 *
 * <p>Persistent values are mirrored into a NeoForge {@link ModConfigSpec}; transient
 * (runtime-only) values are plain static fields.</p>
 */
public final class Configs {

    // ---- Runtime / transient state (mirrors @Config(temporary = true) on the Fabric side) ----

    public static net.earthcomputer.enchantpredict.features.EnchantmentCracker.CrackState enchCrackState =
        net.earthcomputer.enchantpredict.features.EnchantmentCracker.CrackState.UNCRACKED;

    public static net.earthcomputer.enchantpredict.features.PlayerRandCracker.CrackState playerCrackState =
        net.earthcomputer.enchantpredict.features.PlayerRandCracker.CrackState.UNCRACKED;

    public static boolean enchantingPrediction = false;

    public static boolean playerRNGMaintenance = true;

    public static int minEnchantBookshelves = 0;
    public static int maxEnchantBookshelves = 15;
    public static int minEnchantLevels = 1;
    public static int maxEnchantLevels = 30;
    public static int maxEnchantSlot = 3;

    // ---- Persistent values ----

    public static int maxEnchantItemThrows = 64 * 256;
    public static boolean toolBreakWarning = false;
    public static float itemThrowsPerTick = 1.0f;

    // ---- Persistent spec ----

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue MAX_THROWS;
    private static final ModConfigSpec.BooleanValue TOOL_BREAK_WARNING;
    private static final ModConfigSpec.DoubleValue ITEM_THROWS_PER_TICK;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("enchantmentPrediction");
        MAX_THROWS = b.comment("Maximum item-throw count the /cenchant search will try.")
            .defineInRange("maxEnchantItemThrows", 64 * 256, 0, 1_000_000);
        TOOL_BREAK_WARNING = b.comment("Display an overlay warning when a tool is about to break.")
            .define("toolBreakWarning", false);
        ITEM_THROWS_PER_TICK = b.comment("Item throw rate during /cenchant manipulation (throws per game tick).")
            .defineInRange("itemThrowsPerTick", 1.0, 0.0, 20.0);
        b.pop();
        SPEC = b.build();
    }

    /** Pulls values from the loaded NeoForge config into the static facade. */
    public static void load() {
        maxEnchantItemThrows = MAX_THROWS.get();
        toolBreakWarning = TOOL_BREAK_WARNING.get();
        itemThrowsPerTick = ITEM_THROWS_PER_TICK.get().floatValue();
    }

    public static int getMaxEnchantItemThrows() { return maxEnchantItemThrows; }
    public static void setMaxEnchantItemThrows(int v) {
        maxEnchantItemThrows = Mth.clamp(v, 0, 1_000_000);
        MAX_THROWS.set(maxEnchantItemThrows);
    }

    public static int getMinEnchantBookshelves() { return minEnchantBookshelves; }
    public static void setMinEnchantBookshelves(int v) {
        minEnchantBookshelves = Mth.clamp(v, 0, 15);
        maxEnchantBookshelves = Math.max(maxEnchantBookshelves, minEnchantBookshelves);
    }

    public static int getMaxEnchantBookshelves() { return maxEnchantBookshelves; }
    public static void setMaxEnchantBookshelves(int v) {
        maxEnchantBookshelves = Mth.clamp(v, 0, 15);
        minEnchantBookshelves = Math.min(minEnchantBookshelves, maxEnchantBookshelves);
    }

    public static int getMinEnchantLevels() { return minEnchantLevels; }
    public static void setMinEnchantLevels(int v) {
        minEnchantLevels = Mth.clamp(v, 1, 30);
        maxEnchantLevels = Math.max(maxEnchantLevels, minEnchantLevels);
    }

    public static int getMaxEnchantLevels() { return maxEnchantLevels; }
    public static void setMaxEnchantLevels(int v) {
        maxEnchantLevels = Mth.clamp(v, 1, 30);
        minEnchantLevels = Math.min(minEnchantLevels, maxEnchantLevels);
    }

    public static int getMaxEnchantSlot() { return maxEnchantSlot; }
    public static void setMaxEnchantSlot(int v) {
        maxEnchantSlot = Mth.clamp(v, 1, 3);
    }

    public static void setEnchantingPrediction(boolean v) {
        enchantingPrediction = v;
        if (!v) {
            net.earthcomputer.enchantpredict.features.EnchantmentCracker.resetCracker();
        }
    }

    public static void setPlayerRNGMaintenance(boolean v) {
        playerRNGMaintenance = v;
    }

    public static void setToolBreakWarning(boolean v) {
        toolBreakWarning = v;
        TOOL_BREAK_WARNING.set(v);
    }

    public static void setItemThrowsPerTick(float v) {
        itemThrowsPerTick = Math.clamp(v, 0.0f, 20.0f);
        ITEM_THROWS_PER_TICK.set((double) itemThrowsPerTick);
    }

    public enum DummyEnum implements StringRepresentable {
        VALUE;
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }
}
