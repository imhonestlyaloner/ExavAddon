package com.exav.addon.scan;

import java.util.List;

/** Scans one sus chunk: first its tunnels, then the caves around it. */
public final class ChunkJob {
    public record Result(List<Tunnel> tunnels, List<long[]> caves) {
    }

    private final long chunkKey;
    private final TunnelScanner tunnels;
    private final CaveScanner caves;
    private boolean tunnelsDone;
    private boolean cavesDone;

    public ChunkJob(BlockReader reader, long chunkKey, int chunkX, int chunkZ,
                    boolean findTunnels, int minTunnelLength, boolean findCaves, int caveRadius, int minCaveSize) {
        this.chunkKey = chunkKey;
        this.tunnels = findTunnels ? new TunnelScanner(reader, chunkX, chunkZ, minTunnelLength) : null;
        this.caves = findCaves ? new CaveScanner(reader, chunkX, chunkZ, caveRadius, minCaveSize) : null;
        this.tunnelsDone = tunnels == null;
        this.cavesDone = caves == null;
    }

    public long chunkKey() {
        return chunkKey;
    }

    public boolean step(long deadline) {
        if (!tunnelsDone) {
            tunnelsDone = tunnels.step(deadline);
            if (!tunnelsDone) return false;
        }
        if (!cavesDone) {
            cavesDone = caves.step(deadline);
        }
        return cavesDone;
    }

    public Result result() {
        return new Result(tunnels == null ? List.of() : List.copyOf(tunnels.results()),
                caves == null ? List.of() : List.copyOf(caves.results()));
    }
}
