package com.exav.addon.mixin;

import com.exav.addon.sky.AlwaysEggs;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tints the fog (the horizon) to match the sky for the artist easter eggs. */
@Mixin(FogRenderer.class)
public abstract class FogRendererTintMixin {
    @Inject(method = "computeFogColor", at = @At("RETURN"))
    private void exav$tintFog(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darken, CallbackInfoReturnable<Vector4f> cir) {
        com.exav.addon.modules.VideoSkybox.tintFog(cir.getReturnValue());
        AlwaysEggs.tintFog(cir.getReturnValue());
    }
}
