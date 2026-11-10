package com.exav.addon.mixin;

import com.exav.addon.modules.VideoSkybox;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the video skybox at the end of the sky pass (over the normal sky, before terrain). */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererSkyboxMixin {
    @Inject(method = "method_62215", at = @At("TAIL"))
    private static void exav$videoSkybox(CallbackInfo ci) {
        VideoSkybox.render();
    }
}
