package net.earthcomputer.enchantpredict.mixin.enchant;

import net.earthcomputer.enchantpredict.features.EnchantmentCracker;
import net.earthcomputer.enchantpredict.features.PlayerRandCracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.21.1 port of clientcommands {@code EnchantmentScreenMixin}.
 *
 * <p>The original injected into {@code extractRenderState} (1.21.5+); on 1.21.1 we
 * inject at the tail of {@code render} instead, drawing directly to the supplied
 * {@link GuiGraphics}.</p>
 */
@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin extends AbstractContainerScreen<EnchantmentMenu> {

    public EnchantmentScreenMixin(EnchantmentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    public void postRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (EnchantmentCracker.isEnchantingPredictionEnabled()) {
            EnchantmentCracker.renderOverlay(graphics);
        }
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handleInventoryButtonClick(II)V"))
    public void onItemEnchanted(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> ci) {
        PlayerRandCracker.onEnchantedItem();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (EnchantmentCracker.isEnchantingPredictionEnabled()) {
            addRenderableWidget(Button.builder(Component.translatable("enchCrack.addInfo"),
                button -> EnchantmentCracker.addEnchantmentSeedInfo(Minecraft.getInstance().level, getMenu()))
                .pos(width - 150, 0)
                .size(140, 20)
                .build());
        }
    }
}
