package com.exav.addon.util;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.render.BetterSusChunkModule;
import net.anubis.client.module.impl.render.SusChunkFinderModule;

/** Reads which chunks Anubis's sus chunk modules have flagged right now. */
public final class SusChunks {
    public static final String BOTH = "Both";
    public static final String FINDER = "Sus Chunk Finder";
    public static final String BETTER = "Better Sus Chunk";
    public static final String[] SOURCES = {BOTH, FINDER, BETTER};

    private SusChunks() {
    }

    public static LongSet finder() {
        SusChunkFinderModule m = ModuleRegistry.get(SusChunkFinderModule.class);
        return m != null && m.isEnabled() ? m.flaggedChunks() : LongSet.of();
    }

    public static LongSet better() {
        BetterSusChunkModule m = ModuleRegistry.get(BetterSusChunkModule.class);
        return m != null && m.isEnabled() ? m.flaggedChunks() : LongSet.of();
    }

    /** The given chunks plus every chunk within radius of them. */
    public static LongOpenHashSet around(LongSet chunks, int radius) {
        LongOpenHashSet out = new LongOpenHashSet();
        for (var it = chunks.iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            int cx = net.minecraft.world.level.ChunkPos.getX(key), cz = net.minecraft.world.level.ChunkPos.getZ(key);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) out.add(net.minecraft.world.level.ChunkPos.asLong(cx + dx, cz + dz));
            }
        }
        return out;
    }

    /**
     * Flagged chunks for the Exav modules. When Sus Chunk Confidence is on and filtering, only its confident
     * chunks (plus their AFK spot chunks) are returned.
     */
    public static LongOpenHashSet flagged(String source) {
        LongSet accepted = com.exav.addon.modules.SusConfidence.acceptedChunks();
        if (accepted != null) return new LongOpenHashSet(accepted);
        return flaggedRaw(source);
    }

    /** Union of the flagged chunks of the chosen source(s), unfiltered. */
    public static LongOpenHashSet flaggedRaw(String source) {
        LongOpenHashSet out = new LongOpenHashSet();
        if (!BETTER.equals(source)) out.addAll(finder());
        if (!FINDER.equals(source)) out.addAll(better());
        return out;
    }
}
