package net.earthcomputer.enchantpredict.mixin.rngevents;

import net.earthcomputer.enchantpredict.features.EnchantmentCracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Captures the enchanting-table block position the player most recently interacted
 * with — needed by {@link EnchantmentCracker} to compute the bookshelf power.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void onUseItemOn(net.minecraft.client.player.LocalPlayer player, InteractionHand hand,
                              BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        Level level = player.level();
        BlockState state = level.getBlockState(hitResult.getBlockPos());
        if (state.getBlock() instanceof EnchantingTableBlock) {
            EnchantmentCracker.enchantingTablePos = hitResult.getBlockPos();
        }
    }
}
