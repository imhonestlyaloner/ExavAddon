package com.exav.addon.scan;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import com.exav.addon.util.MyEdits;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

/**
 * Collects player-looking blocks (except ones you placed yourself) with their positions, plus structure markers (dungeon spawners,
 * stronghold bricks, ancient city deepslate, trial spawners...). Positions are kept so that, once everything
 * has been scanned, anything sitting inside a structure can be thrown away.
 */
public final class Evidence {
    /** per kind, how many positions are remembered (scores are capped far below this anyway) */
    private static final int CAP = 3000;

    private final LongArrayList positions = new LongArrayList();
    private final ByteArrayList kinds = new ByteArrayList();
    private final int[] stored = new int[BaseEvidence.KINDS];
    private final LongArrayList markers = new LongArrayList();
    private final IntArrayList radii = new IntArrayList();
    private final LongOpenHashSet markerCells = new LongOpenHashSet();
    private final boolean skipMine;

    /** @param skipMine leave out blocks you placed yourself */
    public Evidence(boolean skipMine) {
        this.skipMine = skipMine;
    }

    public void add(int x, int y, int z, Block block) {
        int radius = BaseEvidence.structureRadius(block);
        if (radius > 0) {
            // one marker per 8x8x8 cell is plenty, structures are made of thousands of these blocks
            if (markerCells.add(BlockPos.asLong(x >> 3, y >> 3, z >> 3))) {
                markers.add(BlockPos.asLong(x, y, z));
                radii.add(radius);
            }
            return;
        }
        byte k = BaseEvidence.kind(block);
        if (k == BaseEvidence.NONE || (skipMine && MyEdits.isMine(x, y, z)) || stored[k]++ >= CAP) return;
        positions.add(BlockPos.asLong(x, y, z));
        kinds.add(k);
    }

    /** Structure zones from all the given collectors. */
    public static Zones zones(Evidence... sources) {
        Zones z = new Zones();
        for (Evidence e : sources) {
            if (e == null) continue;
            for (int i = 0; i < e.markers.size(); i++) z.add(e.markers.getLong(i), e.radii.getInt(i));
        }
        return z;
    }

    /** Counts per kind, leaving out anything inside a structure zone. ignored[0] gets how many were left out. */
    public int[] count(Zones zones, int[] ignored) {
        int[] out = new int[BaseEvidence.KINDS];
        for (int i = 0; i < positions.size(); i++) {
            if (zones.contains(positions.getLong(i))) {
                ignored[0]++;
            } else {
                out[kinds.getByte(i)]++;
            }
        }
        return out;
    }

    /** Spheres around structure markers. */
    public static final class Zones {
        private final IntArrayList xs = new IntArrayList();
        private final IntArrayList ys = new IntArrayList();
        private final IntArrayList zs = new IntArrayList();
        private final IntArrayList rs = new IntArrayList();

        void add(long pos, int radius) {
            xs.add(BlockPos.getX(pos));
            ys.add(BlockPos.getY(pos));
            zs.add(BlockPos.getZ(pos));
            rs.add(radius);
        }

        public boolean isEmpty() {
            return xs.isEmpty();
        }

        public boolean contains(long pos) {
            return contains(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos));
        }

        public boolean contains(int x, int y, int z) {
            for (int i = 0; i < xs.size(); i++) {
                int dx = x - xs.getInt(i), dy = y - ys.getInt(i), dz = z - zs.getInt(i), r = rs.getInt(i);
                if (dx * dx + dy * dy + dz * dz <= r * r) return true;
            }
            return false;
        }
    }
}
