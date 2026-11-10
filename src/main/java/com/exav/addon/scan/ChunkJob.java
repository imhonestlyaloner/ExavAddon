package com.exav.addon.scan;

import java.util.ArrayList;
import java.util.List;

/** Scans one sus chunk for Exav's Relog Alternative: first dug tunnels in and around it, then the caves around it. */
public final class ChunkJob {
    public record Result(List<Tunnel> tunnels, List<long[]> caves, boolean complete) {
    }

    private final long chunkKey;
    private final TunnelArea tunnels;
    private final CaveScanner caves;
    private final Result previous;
    private boolean tunnelsDone;
    private boolean cavesDone;
    private int reportedChunks = -1;

    public ChunkJob(BlockSource reader, long chunkKey, int chunkX, int chunkZ, Result previous,
                    boolean findTunnels, TunnelArea tunnelArea, boolean findCaves, int caveRadius, int minCaveSize) {
        this.chunkKey = chunkKey;
        this.previous = previous;
        this.tunnels = findTunnels ? tunnelArea : null;
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

    /** True once per newly finished tunnel chunk, so results can be shown before the whole job is done. */
    public boolean hasProgress() {
        int done = tunnels == null ? 0 : tunnels.chunksDone();
        if (done != reportedChunks) {
            reportedChunks = done;
            return true;
        }
        return false;
    }

    public String progress() {
        if (!tunnelsDone) return "tunnels " + tunnels.chunksDone() + "/" + tunnels.chunksTotal() + " chunks";
        if (!cavesDone) return "caves";
        return "done";
    }

    /** What's known so far; until caves finish, the previous scan's caves are kept on screen. */
    public Result result() {
        List<Tunnel> t;
        if (tunnels == null) t = List.of();
        else if (!tunnelsDone && previous != null && previous.complete()) t = previous.tunnels(); // rescan: keep the old ones until done
        else t = new ArrayList<>(tunnels.results());
        List<long[]> c;
        if (caves != null && cavesDone) c = List.copyOf(caves.results());
        else if (previous != null) c = previous.caves();
        else c = List.of();
        return new Result(t, c, tunnelsDone && cavesDone);
    }
}
