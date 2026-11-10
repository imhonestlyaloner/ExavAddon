package com.exav.addon.scan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Decides whether a block change the server sent looks like a player broke or placed something.
 *
 * Only changes to or from air count. Anti-xray reveals (fake stone turning into the real block when it gets exposed)
 * are solid-to-solid, so they never count. Natural changes are filtered out: fluids flowing, sand/gravel falling,
 * plants growing (cave vines, lichen, dripstone, amethyst, sculk...), fire, snow.
 */
public final class LiveEdits {
    private LiveEdits() {
    }

    public static boolean looksPlayerMade(BlockState oldState, BlockState newState, BlockPos pos) {
        boolean wasAir = oldState.isAir();
        boolean nowAir = newState.isAir();
        if (wasAir == nowAir) return false;
        BlockState block = wasAir ? newState : oldState;
        if (!block.getFluidState().isEmpty()) return false;
        if (block.getBlock() instanceof FallingBlock) return false;
        if (natural(BuiltInRegistries.BLOCK.getKey(block.getBlock()).getPath())) return false;
        // placing torches / rails / ladders counts, breaking or placing anything solid counts
        return BaseEvidence.kind(block.getBlock()) != BaseEvidence.NONE || !block.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, pos).isEmpty();
    }

    private static boolean natural(String id) {
        return id.contains("vine") || id.contains("lichen") || id.contains("dripstone") || id.contains("amethyst")
                || id.contains("sculk") || id.equals("fire") || id.equals("soul_fire") || id.contains("snow")
                || id.contains("moss") || id.contains("azalea") || id.equals("spore_blossom") || id.contains("kelp")
                || id.contains("seagrass") || id.equals("bubble_column") || id.contains("leaves") || id.contains("mushroom")
                || id.equals("short_grass") || id.equals("tall_grass") || id.equals("fern") || id.contains("dead_bush")
                || id.equals("big_dripleaf") || id.equals("big_dripleaf_stem") || id.equals("small_dripleaf")
                || id.equals("pointed_dripstone") || id.equals("ice") || id.equals("frosted_ice") || id.equals("cobweb")
                || id.equals("powder_snow") || id.contains("_egg") || id.equals("infested_stone") || id.startsWith("infested_");
    }
}
