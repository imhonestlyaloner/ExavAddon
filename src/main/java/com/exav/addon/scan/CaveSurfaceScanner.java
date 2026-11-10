package com.exav.addon.scan;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Looks at the caves around a sus chunk for player-made stuff: torches/rails/ladders sitting in the cave air and
 * placed blocks (cobblestone, netherrack, chests...) on the cave walls, floor and ceiling.
 * Those blocks touch air, so anti-xray has to send them as they really are.
 * Blocks inside the sus chunk itself are skipped, the chunk scan already counted them.
 */
public final class CaveSurfaceScanner {
    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final BlockReader reader;
    private final List<long[]> caves;
    private final int chunkX;
    private final int chunkZ;
    private final LongOpenHashSet seen = new LongOpenHashSet();
    private final Evidence evidence = new Evidence();
    private int cave;
    private int index;

    public CaveSurfaceScanner(BlockReader reader, List<long[]> caves, int chunkX, int chunkZ) {
        this.reader = reader;
        this.caves = caves;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public boolean step(long deadline) {
        int ops = 0;
        while (cave < caves.size()) {
            long[] cells = caves.get(cave);
            if (index >= cells.length) {
                cave++;
                index = 0;
                continue;
            }
            long p = cells[index++];
            int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
            check(x, y, z);
            for (int[] d : DIRS) check(x + d[0], y + d[1], z + d[2]);
            if (++ops % 256 == 0 && System.nanoTime() > deadline) return false;
        }
        return true;
    }

    private void check(int x, int y, int z) {
        if (x >> 4 == chunkX && z >> 4 == chunkZ) return;
        if (!seen.add(BlockPos.asLong(x, y, z))) return;
        BlockState state = reader.state(x, y, z);
        if (state == null || !BaseEvidence.interesting(state.getBlock())) return;
        evidence.add(x, y, z, state.getBlock());
    }

    public Evidence evidence() {
        return evidence;
    }
}
