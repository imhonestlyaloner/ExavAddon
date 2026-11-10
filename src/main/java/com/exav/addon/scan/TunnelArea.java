package com.exav.addon.scan;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.ChunkPos;

/**
 * Finds tunnels in a square of chunks around a sus chunk (the sus chunk itself first), reusing cached
 * per-chunk results where they're fresh enough.
 */
public final class TunnelArea {
    private final BlockSource reader;
    private final TunnelCache cache;
    private final long maxAgeMs;
    private final boolean allShapes;
    private final int minLength;
    private final LongArrayList chunks = new LongArrayList();
    private final List<Tunnel> found = new ArrayList<>();
    private int index;
    private long scanning = Long.MIN_VALUE;
    private TunnelSet set;
    private int finished;

    public TunnelArea(BlockSource reader, TunnelCache cache, long maxAgeMs, int chunkX, int chunkZ, int radius,
                      boolean allShapes, int minLength) {
        this.reader = reader;
        this.cache = cache;
        this.maxAgeMs = maxAgeMs;
        this.allShapes = allShapes;
        this.minLength = minLength;
        chunks.add(ChunkPos.asLong(chunkX, chunkZ));
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r) chunks.add(ChunkPos.asLong(chunkX + dx, chunkZ + dz));
                }
            }
        }
    }

    /** @return true when every chunk is done */
    public boolean step(long deadline) {
        while (index < chunks.size()) {
            long key = chunks.getLong(index);
            if (set == null || scanning != key) {
                List<Tunnel> cached = cache.get(key, maxAgeMs, allShapes, minLength);
                int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
                if (cached != null || !reader.isLoaded(cx, cz)) {
                    if (cached != null) found.addAll(cached);
                    index++;
                    finished++;
                    continue;
                }
                set = new TunnelSet(reader, cx, cz, allShapes, minLength);
                scanning = key;
            }
            if (!set.step(deadline)) return false;
            List<Tunnel> tunnels = set.results();
            cache.put(key, allShapes, minLength, tunnels);
            found.addAll(tunnels);
            set = null;
            index++;
            finished++;
            if (System.nanoTime() > deadline) return index >= chunks.size();
        }
        return true;
    }

    /** Tunnels found so far (including your own; the caller filters). */
    public List<Tunnel> results() {
        return found;
    }

    public int chunksDone() {
        return finished;
    }

    public int chunksTotal() {
        return chunks.size();
    }
}
