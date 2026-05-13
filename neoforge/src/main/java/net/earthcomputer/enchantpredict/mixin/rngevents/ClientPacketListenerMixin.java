package net.earthcomputer.enchantpredict.mixin.rngevents;

import net.earthcomputer.enchantpredict.features.CCrackRng;
import net.earthcomputer.enchantpredict.task.ItemThrowTask;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forwards item-entity spawn packets to the cracker / item-throw task so they can
 * be matched up with locally-initiated drops.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleAddEntity", at = @At("HEAD"))
    private void onHandleAddEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        ItemThrowTask.handleItemSpawn(packet);
        CCrackRng.onEntityCreation(packet);
    }
}
