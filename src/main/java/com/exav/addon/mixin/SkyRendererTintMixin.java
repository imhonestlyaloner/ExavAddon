package com.exav.addon.mixin;

import com.exav.addon.sky.AlwaysEggs;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tints the sky colour for the artist easter eggs (green for Yeat, dark red for ICP). */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererTintMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void exav$tintSky(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
        AlwaysEggs.tintSky(state);
    }
}
