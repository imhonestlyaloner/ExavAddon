package com.exav.addon.mixin;

import com.exav.addon.modules.BetterGlint;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Draws the item's quads a second time with Better Glint's look over them, and its effect around the outline. */
@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
public abstract class ItemLayerGlintMixin {
    @WrapOperation(
            method = "submit",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V")
    )
    private void exav$betterGlint(SubmitNodeCollector collector, PoseStack pose, ItemDisplayContext context, int light, int overlay, int outline,
                                  int[] tints, List<BakedQuad> quads, RenderType type, ItemStackRenderState.FoilType foil, Operation<Void> original) {
        if (!BetterGlint.current()) {
            original.call(collector, pose, context, light, overlay, outline, tints, quads, type, foil);
            return;
        }
        original.call(collector, pose, context, light, overlay, outline, tints, quads, type,
                BetterGlint.keepVanilla() ? foil : ItemStackRenderState.FoilType.NONE);
        collector.submitItem(pose, context, light, overlay, outline, tints, quads, BetterGlint.itemType(), ItemStackRenderState.FoilType.NONE);
        BetterGlint.submitAura(collector, pose, context, quads);
    }
}
