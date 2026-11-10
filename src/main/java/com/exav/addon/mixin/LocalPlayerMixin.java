package com.exav.addon.mixin;

import com.exav.addon.util.MyDrops;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pressing the drop key. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "drop", at = @At("HEAD"))
    private void exav$onDrop(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
        MyDrops.dropped();
    }
}
