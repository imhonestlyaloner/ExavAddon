package com.exav.addon.mixin;

import com.exav.addon.sky.AlwaysEggs;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the artist easter eggs submit their floating pictures along with the entities (camera-relative pose). */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererSkyImagesMixin {
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void exav$skyImages(PoseStack pose, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
        AlwaysEggs.submit(pose, collector);
    }
}
