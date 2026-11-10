package com.exav.addon.scan;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs one {@link TunnelScanner} per tunnel shape players dig, one after another, and collects every tunnel found
 * (minus the ones you dug yourself).
 */
public final class TunnelSet {
    /** width, height, slope, min length */
    private static final int[][] ALL = {
            {1, 2, 0, 12}, // branch mine
            {1, 3, 0, 12},
            {2, 2, 0, 10},
            {2, 3, 0, 8},
            {3, 3, 0, 8},  // netherite / hammer tunnels
            {4, 4, 0, 8},
            {5, 5, 0, 8},  // tunnel bores
            {1, 3, 1, 8},  // staircases up / down
            {1, 3, -1, 8},
            {2, 3, 1, 8},
            {2, 3, -1, 8},
    };
    private static final int[][] ONLY_3X3 = {{3, 3, 0, 8}};

    private final List<TunnelScanner> scanners = new ArrayList<>();
    private int current;

    /** @param minLength3x3 minimum length for the 3x3 (and larger) tunnels; smaller shapes keep their own minimums */
    public TunnelSet(BlockSource reader, int chunkX, int chunkZ, boolean allShapes, int minLength3x3) {
        for (int[] s : allShapes ? ALL : ONLY_3X3) {
            int min = s[0] >= 3 ? minLength3x3 : s[3];
            scanners.add(new TunnelScanner(reader, chunkX, chunkZ, min, s[0], s[1], s[2]));
        }
    }

    public boolean step(long deadline) {
        while (current < scanners.size()) {
            if (!scanners.get(current).step(deadline)) return false;
            current++;
            if (System.nanoTime() > deadline) return current >= scanners.size();
        }
        return true;
    }

    /** Every tunnel found (including ones you dug; filter with {@link #withoutMine}). */
    public List<Tunnel> results() {
        List<Tunnel> out = new ArrayList<>();
        for (TunnelScanner s : scanners) out.addAll(s.results());
        return out;
    }

    public static List<Tunnel> withoutMine(List<Tunnel> tunnels) {
        List<Tunnel> out = new ArrayList<>(tunnels.size());
        for (Tunnel t : tunnels) {
            if (!t.isMine()) out.add(t);
        }
        return out;
    }
}
