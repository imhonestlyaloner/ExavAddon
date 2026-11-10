package com.exav.addon.mixin;

import com.exav.addon.util.MyDrops;
import com.exav.addon.util.MyEdits;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tells MyEdits / MyDrops about blocks you break and right-click, mobs you hit and items you throw. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void exav$onDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        MyEdits.broke(pos);
        MyDrops.broke(pos);
    }

    @Inject(method = "attack", at = @At("HEAD"))
    private void exav$onAttack(Player player, Entity target, CallbackInfo ci) {
        MyDrops.attacked(target);
    }

    /** Throwing items out of an inventory screen (Q over a slot, or clicking outside the window). */
    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"))
    private void exav$onInventoryClick(int containerId, int slot, int button, ClickType type, Player player, CallbackInfo ci) {
        if (type == ClickType.THROW || slot == -999) MyDrops.dropped();
    }

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void exav$onUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        MyEdits.clicked(hit.getBlockPos(), hit.getDirection());
    }
}
