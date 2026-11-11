package com.exav.addon.mixin;

import com.exav.addon.modules.BetterGlint;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import java.util.SequencedMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Gives the Better Glint render types their own buffers at the end of the main buffer list, so they're drawn after
 * the items they sit on (the glint shader only draws where the item already is - drawn first, it shows nothing).
 */
@Mixin(RenderBuffers.class)
public abstract class RenderBuffersGlintMixin {
    @ModifyArg(method = "<init>", index = 0,
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/client/renderer/MultiBufferSource;immediateWithBuffers(Ljava/util/SequencedMap;Lcom/mojang/blaze3d/vertex/ByteBufferBuilder;)Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;"))
    private SequencedMap<RenderType, ByteBufferBuilder> exav$addGlintBuffers(SequencedMap<RenderType, ByteBufferBuilder> fixed) {
        for (RenderType type : new RenderType[]{BetterGlint.itemType(), BetterGlint.armorType()}) {
            fixed.put(type, new ByteBufferBuilder(type.bufferSize()));
        }
        return fixed;
    }
}
