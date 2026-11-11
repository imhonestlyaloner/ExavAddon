package com.exav.addon.mixin;

import com.exav.addon.glint.GlintMarked;
import com.exav.addon.modules.BetterGlint;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the render state of items that should get Better Glint (this is where the item stack is still known). */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverGlintMixin {
    @Inject(method = "updateForTopItem", at = @At("TAIL"))
    private void exav$markGlint(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level, ItemOwner owner, int seed, CallbackInfo ci) {
        ((GlintMarked) state).exav$setGlint(BetterGlint.wants(stack));
    }
}
