package net.earthcomputer.enchantpredict.mixin.rngevents;

import net.earthcomputer.enchantpredict.features.PlayerRandCracker;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Detects keyboard item drops (Q / Ctrl+Q) so the cracker can track the player RNG
 * advances they cause.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Inject(method = "drop(Z)Z", at = @At("HEAD"))
    private void onDrop(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
        PlayerRandCracker.onDropItem();
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), require = 0)
    private void onDropStack(ItemStack stack, boolean random, CallbackInfoReturnable<?> cir) {
        if (!stack.isEmpty()) {
            PlayerRandCracker.onDropItem();
        }
    }
}
