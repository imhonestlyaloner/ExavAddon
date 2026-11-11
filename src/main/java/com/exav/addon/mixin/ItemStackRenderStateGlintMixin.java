package com.exav.addon.mixin;

import com.exav.addon.glint.GlintMarked;
import com.exav.addon.modules.BetterGlint;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Holds the Better Glint mark, and tells the layers about it while they're submitted. */
@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateGlintMixin implements GlintMarked {
    @Unique
    private boolean exav$glint;

    @Override
    public boolean exav$glint() {
        return exav$glint;
    }

    @Override
    public void exav$setGlint(boolean glint) {
        exav$glint = glint;
    }

    @Inject(method = "submit", at = @At("HEAD"))
    private void exav$begin(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outline, CallbackInfo ci) {
        BetterGlint.begin(exav$glint);
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void exav$end(PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outline, CallbackInfo ci) {
        BetterGlint.end();
    }
}
