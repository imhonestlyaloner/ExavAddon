package com.exav.addon.scan;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds straight tunnels of an exact size (3x3 by default, 1x2 for branch mines) that pass through one chunk. Runs a bit at a time via {@link #step(long)}.
 *
 * Every candidate 3x3 cross-section ("slice") is checked along X and Z. A slice is:
 *  - NONE when any of its interior blocks isn't open (or it's above the surface)
 *  - GOOD when the interior is open and each of the 4 surrounding sides (floor, ceiling, 2 walls) is mostly solid
 *    (2 of 3, 2 of 2, 1 of 1), which is what makes it exactly that size and not part of a bigger room
 *  - OPEN when the interior is open but the walls aren't (side openings, crossings, caves cutting through)
 * A tunnel is a run of slices with at least minLength GOOD ones and no more than MAX_GAP OPEN slices in a row.
 */
public final class TunnelScanner {
    private static final byte NONE = 0;
    private static final byte OPEN = 1;
    private static final byte GOOD = 2;
    /** how far past the chunk edge a tunnel is followed */
    private static final int EXTEND = 64;
    /** consecutive non-3x3 slices allowed inside a tunnel (side openings, crossings) */
    private static final int MAX_GAP = 4;

    private final BlockReader reader;
    private final int chunkX;
    private final int chunkZ;
    private final int minLength;
    private final int width;
    private final int height;
    private final int yMax;
    private int y;
    private final List<Tunnel> found = new ArrayList<>();

    // current run
    private int runStart;
    private int lastGood;
    private int good;
    private int gap;
    private boolean inRun;

    public TunnelScanner(BlockReader reader, int chunkX, int chunkZ, int minLength) {
        this(reader, chunkX, chunkZ, minLength, 3, 3);
    }

    public TunnelScanner(BlockReader reader, int chunkX, int chunkZ, int minLength, int width, int height) {
        this.reader = reader;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minLength = minLength;
        this.width = width;
        this.height = height;
        this.y = reader.minY() + 1;
        int top = reader.minY();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                top = Math.max(top, reader.surface((chunkX << 4) + x, (chunkZ << 4) + z));
            }
        }
        this.yMax = top - height; // the ceiling (y + height) has to be at or under the surface
    }

    /** @return true once the whole chunk has been scanned */
    public boolean step(long deadline) {
        while (y <= yMax) {
            for (int axis = 0; axis < 2; axis++) {
                int lateralBase = axis == 0 ? chunkZ << 4 : chunkX << 4;
                for (int l = lateralBase - width + 1; l <= lateralBase + 15; l++) {
                    scanLine(axis, y, l);
                }
            }
            y++;
            if (System.nanoTime() > deadline) return y > yMax;
        }
        return true;
    }

    public List<Tunnel> results() {
        return found;
    }

    private void scanLine(int axis, int y, int l) {
        int c0 = axis == 0 ? chunkX << 4 : chunkZ << 4;
        int c1 = c0 + 15;
        int start = c0;
        if (slice(axis, c0, y, l) != NONE) {
            while (start > c0 - EXTEND && slice(axis, start - 1, y, l) != NONE) start--;
        }
        inRun = false;
        for (int t = start; t <= c1 + EXTEND; t++) {
            boolean past = t > c1;
            if (past && !inRun) return;
            byte s = slice(axis, t, y, l);
            if (s == NONE) {
                finish(axis, y, l, c0, c1);
                if (past) return;
            } else if (s == GOOD) {
                if (!inRun) {
                    inRun = true;
                    runStart = t;
                    good = 0;
                }
                good++;
                gap = 0;
                lastGood = t;
            } else if (inRun) {
                if (++gap > MAX_GAP) finish(axis, y, l, c0, c1);
            }
        }
        finish(axis, y, l, c0, c1);
    }

    private void finish(int axis, int y, int l, int c0, int c1) {
        if (inRun && good >= minLength && lastGood >= c0 && runStart <= c1) {
            found.add(new Tunnel(axis, y, l, runStart, lastGood, width, height));
        }
        inRun = false;
        good = 0;
        gap = 0;
    }

    private byte slice(int axis, int t, int y, int l) {
        // centre first, it rejects almost everything underground
        if (!at(axis, t, y + height / 2, l + width / 2, true)) return NONE;
        for (int dy = 0; dy < height; dy++) {
            for (int dl = 0; dl < width; dl++) {
                if (!at(axis, t, y + dy, l + dl, true)) return NONE;
            }
        }
        int cx = axis == 0 ? t : l + width / 2;
        int cz = axis == 0 ? l + width / 2 : t;
        if (reader.surface(cx, cz) < y + height) return NONE;

        int floor = 0, ceiling = 0, wallA = 0, wallB = 0;
        for (int i = 0; i < width; i++) {
            if (at(axis, t, y - 1, l + i, false)) floor++;
            if (at(axis, t, y + height, l + i, false)) ceiling++;
        }
        for (int i = 0; i < height; i++) {
            if (at(axis, t, y + i, l - 1, false)) wallA++;
            if (at(axis, t, y + i, l + width, false)) wallB++;
        }
        return floor >= need(width) && ceiling >= need(width) && wallA >= need(height) && wallB >= need(height) ? GOOD : OPEN;
    }

    /** solid blocks needed on a side of n blocks: 1 of 1, 2 of 2, 2 of 3 */
    private static int need(int n) {
        return (2 * n + 2) / 3;
    }

    /** wantOpen: is the block open; otherwise: is it solid. */
    private boolean at(int axis, int t, int y, int lateral, boolean wantOpen) {
        int x = axis == 0 ? t : lateral;
        int z = axis == 0 ? lateral : t;
        return wantOpen ? reader.open(x, y, z) : reader.solid(x, y, z);
    }
}
