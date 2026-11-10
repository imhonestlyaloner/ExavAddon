package com.exav.addon.scan;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * Flood-fills the enclosed (below the surface) open space in a square of chunks around a sus chunk and keeps
 * every connected pocket of at least minSize blocks. Runs a bit at a time via {@link #step(long)}.
 */
public final class CaveScanner {
    private static final int MAX_CAVE_BLOCKS = 250_000;
    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final BlockReader reader;
    private final int minSize;
    private final int x0, z0, x1, z1; // inclusive block bounds of the area
    private final LongOpenHashSet visited = new LongOpenHashSet();
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    private final List<long[]> found = new ArrayList<>();
    private LongArrayList component;

    // seed cursor
    private int cx, cz, cy;
    private int columnTop;
    private boolean done;

    public CaveScanner(BlockReader reader, int chunkX, int chunkZ, int radiusChunks, int minSize) {
        this.reader = reader;
        this.minSize = minSize;
        this.x0 = (chunkX - radiusChunks) << 4;
        this.z0 = (chunkZ - radiusChunks) << 4;
        this.x1 = ((chunkX + radiusChunks) << 4) + 15;
        this.z1 = ((chunkZ + radiusChunks) << 4) + 15;
        this.cx = x0;
        this.cz = z0;
        startColumn();
    }

    public List<long[]> results() {
        return found;
    }

    /** @return true once the whole area has been scanned */
    public boolean step(long deadline) {
        int ops = 0;
        while (!done) {
            if (++ops % 512 == 0 && System.nanoTime() > deadline) return false;

            if (component != null) {
                if (queue.isEmpty() || component.size() >= MAX_CAVE_BLOCKS) {
                    if (component.size() >= minSize) found.add(component.toLongArray());
                    component = null;
                    queue.clear();
                    continue;
                }
                long p = queue.dequeueLong();
                int px = BlockPos.getX(p), py = BlockPos.getY(p), pz = BlockPos.getZ(p);
                for (int[] d : DIRS) {
                    int nx = px + d[0], ny = py + d[1], nz = pz + d[2];
                    if (nx < x0 || nx > x1 || nz < z0 || nz > z1) continue;
                    long key = BlockPos.asLong(nx, ny, nz);
                    if (visited.contains(key) || !enclosedOpen(nx, ny, nz)) continue;
                    visited.add(key);
                    queue.enqueue(key);
                    component.add(key);
                }
                continue;
            }

            // look for the next seed
            if (cy >= columnTop) {
                nextColumn();
                continue;
            }
            int y = cy++;
            long key = BlockPos.asLong(cx, y, cz);
            if (visited.contains(key) || !reader.open(cx, y, cz)) continue;
            visited.add(key);
            component = new LongArrayList();
            component.add(key);
            queue.enqueue(key);
        }
        return true;
    }

    private boolean enclosedOpen(int x, int y, int z) {
        return y >= reader.minY() && y < reader.surface(x, z) && reader.open(x, y, z);
    }

    private void nextColumn() {
        cz++;
        if (cz > z1) {
            cz = z0;
            cx++;
            if (cx > x1) {
                done = true;
                return;
            }
        }
        startColumn();
    }

    private void startColumn() {
        cy = reader.minY();
        // unloaded chunks report a surface below the world, so their columns are skipped
        columnTop = reader.surface(cx, cz);
    }
}
