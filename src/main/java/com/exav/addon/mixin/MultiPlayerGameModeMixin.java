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

/** Tells MyEdits about blocks you break and right-click, so tunnels you dig yourself aren't labelled. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void exav$onDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        MyEdits.broke(pos);
    }

    @Inject(method = "attack", at = @At("HEAD"))
    private void exav$onAttack(net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.Entity target,
                               org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.exav.addon.modules.KillEffects.attacked(target);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void exav$onUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        MyEdits.clicked(hit.getBlockPos(), hit.getDirection());
        com.exav.addon.modules.ChestPreview.clicked(hit.getBlockPos());
    }
}
