package com.exav.addon.mixin;

import com.exav.addon.modules.HolidayKnife;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets Holiday Knife draw the held sword. Higher priority than Anubis's CS Knife hook on the same call,
 * so it wraps it: when Holiday Knife draws, CS Knife doesn't.
 */
@Mixin(value = ItemInHandRenderer.class, priority = 1100)
public abstract class ItemInHandRendererMixin {
    @WrapOperation(
            method = "renderArmWithItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V")
    )
    private void exav$holidayKnife(ItemInHandRenderer renderer, LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                   PoseStack pose, SubmitNodeCollector collector, int light, Operation<Void> original,
                                   @Local(argsOnly = true) InteractionHand hand) {
        if (!HolidayKnife.render(pose, stack, context, collector, light, hand == InteractionHand.MAIN_HAND)) {
            original.call(renderer, entity, stack, context, pose, collector, light);
        }
    }
}
