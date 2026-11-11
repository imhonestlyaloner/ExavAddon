package com.exav.addon.mixin;

import com.exav.addon.modules.BetterGlint;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Better Glint's galaxy on your first-person arms: draws each arm part a second time with the galaxy over it. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarHandGalaxyMixin {
    @WrapOperation(method = "renderHand",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
    private void exav$galaxyArm(SubmitNodeCollector collector, ModelPart part, PoseStack pose, RenderType type, int light, int overlay,
                                TextureAtlasSprite sprite, Operation<Void> original) {
        original.call(collector, part, pose, type, light, overlay, sprite);
        RenderType galaxy = BetterGlint.armType();
        if (galaxy != null) collector.submitModelPart(part, pose, galaxy, light, overlay, null);
    }
}
