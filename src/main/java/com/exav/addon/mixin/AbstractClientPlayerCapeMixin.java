package com.exav.addon.mixin;

import com.exav.addon.modules.CustomCape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives your own player the Custom Cape (client side only). */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerCapeMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void exav$customCape(CallbackInfoReturnable<PlayerSkin> cir) {
        if ((Object) this == Minecraft.getInstance().player) cir.setReturnValue(CustomCape.apply(cir.getReturnValue()));
    }
}
