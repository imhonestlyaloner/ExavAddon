package com.exav.addon.mixin;

import com.exav.addon.audio.OggClip;
import com.mojang.blaze3d.audio.SoundBuffer;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets the game's sound loader read our .ogg files straight from the config folder. */
@Mixin(SoundBufferLibrary.class)
public abstract class SoundBufferLibraryMixin {
    @Inject(method = "getCompleteBuffer", at = @At("HEAD"), cancellable = true)
    private void exav$diskSound(Identifier path, CallbackInfoReturnable<CompletableFuture<SoundBuffer>> cir) {
        CompletableFuture<SoundBuffer> disk = OggClip.load(path);
        if (disk != null) cir.setReturnValue(disk);
    }
}
