package com.exav.addon.scan;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;

/** Tunnel scan results per chunk, so a chunk next to several sus chunks is only scanned once per rescan period. */
public final class TunnelCache {
    private record Entry(long time, boolean allShapes, int minLength, List<Tunnel> tunnels) {
    }

    private final Long2ObjectOpenHashMap<Entry> entries = new Long2ObjectOpenHashMap<>();

    public List<Tunnel> get(long chunkKey, long maxAgeMs, boolean allShapes, int minLength) {
        Entry e = entries.get(chunkKey);
        if (e == null || e.allShapes != allShapes || e.minLength != minLength) return null;
        if (System.currentTimeMillis() - e.time > maxAgeMs) return null;
        return e.tunnels;
    }

    public void put(long chunkKey, boolean allShapes, int minLength, List<Tunnel> tunnels) {
        entries.put(chunkKey, new Entry(System.currentTimeMillis(), allShapes, minLength, List.copyOf(tunnels)));
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }
}
