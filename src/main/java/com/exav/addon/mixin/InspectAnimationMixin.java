package com.exav.addon.mixin;

import com.exav.addon.modules.InspectCycle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Inspect Cycle: hands CS Knife a different inspect animation each time it starts one. */
@Pseudo
@Mixin(targets = "net.anubis.client.module.impl.render.InspectAnimation", remap = false)
public abstract class InspectAnimationMixin {
    @Inject(method = "clip", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void exav$cycle(String style, CallbackInfoReturnable<Object> cir) {
        if (InspectCycle.passThrough()) return;
        Object clip = InspectCycle.pick(null, style);
        if (clip != null) cir.setReturnValue(clip);
    }
}
