package com.exav.addon.mixin;

import com.exav.addon.modules.EndSkyLyrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.renderer.LevelRenderer;

/**
 * Anubis draws Spotify sky lyrics after the sun, moon and stars, which the End doesn't have, so they never
 * showed up there. This draws them right after the End sky instead (in the same sky pass), outside the End sky
 * method itself so it still works when Anubis's Atmosphere replaces the End sky.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererEndSkyMixin {
    @Inject(
            method = "method_62215",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SkyRenderer;renderEndSky()V", shift = At.Shift.AFTER)
    )
    private static void exav$endSkyLyrics(CallbackInfo ci) {
        EndSkyLyrics.draw();
    }
}
