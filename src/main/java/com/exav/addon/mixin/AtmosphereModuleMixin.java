package com.exav.addon.mixin;

import com.exav.addon.sky.AlwaysEggs;
import net.anubis.client.module.impl.render.AtmosphereModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a Christmas song plays, Anubis's Atmosphere module acts as if it were off, so its custom skies
 * (and everything else it does to the sky) step aside for the Christmas sky. Nothing is changed in its settings.
 */
@Mixin(value = AtmosphereModule.class, remap = false)
public abstract class AtmosphereModuleMixin {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private static void exav$christmasOverride(CallbackInfoReturnable<AtmosphereModule> cir) {
        if (AlwaysEggs.christmas()) cir.setReturnValue(null);
    }
}
