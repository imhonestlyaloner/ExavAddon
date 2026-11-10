package com.exav.addon.mixin;

import com.exav.addon.modules.ArtistEggs;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Camera shake and zoom punch when a giant Yeat bell gets hit. */
@Mixin(GameRenderer.class)
public abstract class GameRendererImpactMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"))
    private void exav$bellShake(PoseStack pose, float partialTick, CallbackInfo ci) {
        ArtistEggs.cameraShake(pose);
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void exav$bellPunch(Camera camera, float partialTick, boolean useSetting, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(ArtistEggs.fov(cir.getReturnValue()));
    }
}
