package com.exav.addon.scan;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.anubis.client.util.BlockClusterGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Combines per-chunk results into what gets drawn: tunnels found from neighbouring sus chunks are joined,
 * overlapping cave pockets are joined, tunnel blocks are taken out of the caves, and each cave gets an outline mesh.
 * Pure data, safe to run off the render thread.
 */
public final class Merger {
    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    public record Cave(BlockClusterGeometry.Mesh mesh, AABB bounds, int size) {
    }

    public record Output(List<Tunnel> tunnels, List<AABB> tunnelBoxes, List<Cave> caves) {
        public static final Output EMPTY = new Output(List.of(), List.of(), List.of());
    }

    private Merger() {
    }

    public static Output merge(Collection<ChunkJob.Result> results, int minCaveSize, boolean includeMine) {
        List<Tunnel> tunnels = mergeTunnels(results);
        if (!includeMine) tunnels = TunnelSet.withoutMine(tunnels);
        List<AABB> boxes = new ArrayList<>(tunnels.size());
        for (Tunnel t : tunnels) boxes.addAll(t.boxes());
        return new Output(tunnels, boxes, mergeCaves(results, tunnels, minCaveSize, includeMine));
    }

    private static List<Tunnel> mergeTunnels(Collection<ChunkJob.Result> results) {
        Map<List<Integer>, List<Tunnel>> lines = new HashMap<>();
        for (ChunkJob.Result r : results) {
            for (Tunnel t : r.tunnels()) {
                lines.computeIfAbsent(List.of(t.axis(), t.y() - t.slope() * t.start(), t.lateral(), t.width(), t.height(), t.slope()), k -> new ArrayList<>()).add(t);
            }
        }
        List<Tunnel> merged = new ArrayList<>();
        for (List<Tunnel> line : lines.values()) {
            line.sort(Comparator.comparingInt(Tunnel::start));
            Tunnel cur = null;
            for (Tunnel t : line) {
                if (cur != null && t.start() <= cur.end() + 1) {
                    cur = new Tunnel(cur.axis(), cur.y(), cur.lateral(), cur.start(), Math.max(cur.end(), t.end()), cur.width(), cur.height(), cur.slope());
                } else {
                    if (cur != null) merged.add(cur);
                    cur = t;
                }
            }
            if (cur != null) merged.add(cur);
        }
        return merged;
    }

    private static List<Cave> mergeCaves(Collection<ChunkJob.Result> results, List<Tunnel> tunnels, int minCaveSize, boolean includeMine) {
        LongOpenHashSet all = new LongOpenHashSet();
        for (ChunkJob.Result r : results) {
            for (long[] cave : r.caves()) {
                for (long p : cave) all.add(p);
            }
        }
        if (all.isEmpty()) return List.of();

        // air you dug yourself isn't cave
        if (!includeMine) all.removeIf(p -> com.exav.addon.util.MyEdits.isMine(p));
        // tunnels are drawn on their own, don't count them as cave
        for (Tunnel t : tunnels) {
            for (AABB b : t.boxes()) {
                for (int x = (int) b.minX; x < (int) b.maxX; x++) {
                    for (int y = (int) b.minY; y < (int) b.maxY; y++) {
                        for (int z = (int) b.minZ; z < (int) b.maxZ; z++) {
                            all.remove(BlockPos.asLong(x, y, z));
                        }
                    }
                }
            }
        }

        List<Cave> caves = new ArrayList<>();
        LongOpenHashSet remaining = new LongOpenHashSet(all);
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        while (!remaining.isEmpty()) {
            LongIterator it = remaining.iterator();
            long seed = it.nextLong();
            it.remove();
            List<BlockPos> component = new ArrayList<>();
            queue.enqueue(seed);
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            while (!queue.isEmpty()) {
                long p = queue.dequeueLong();
                BlockPos pos = BlockPos.of(p);
                component.add(pos);
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
                for (int[] d : DIRS) {
                    long n = BlockPos.offset(p, d[0], d[1], d[2]);
                    if (remaining.remove(n)) queue.enqueue(n);
                }
            }
            if (component.size() < minCaveSize) continue;
            BlockClusterGeometry.Mesh mesh = BlockClusterGeometry.build(component,
                    (x, y, z) -> all.contains(BlockPos.asLong(x, y, z)));
            caves.add(new Cave(mesh, new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1), component.size()));
        }
        return caves;
    }
}
