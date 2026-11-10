package com.exav.addon.mixin;

import com.exav.addon.util.MyEdits;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tells MyEdits about blocks you break and right-click, so your own changes never count as evidence. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void exav$onDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        MyEdits.broke(pos);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void exav$onUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        MyEdits.clicked(hit.getBlockPos(), hit.getDirection());
    }
}
