package com.exav.addon.scan;

import com.exav.addon.util.MyEdits;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;

/**
 * A straight dug tunnel of an exact cross-section (width x height). axis 0 runs along X, axis 1 along Z.
 * slope is 0 for flat tunnels, +1/-1 for staircases (floor goes up/down one block per block along the axis).
 * y is the floor-level air block at {@code start}, lateral is the lowest Z (axis 0) or X (axis 1) of the interior,
 * start/end are the inclusive coordinates along the axis.
 */
public record Tunnel(int axis, int y, int lateral, int start, int end, int width, int height, int slope) {
    public int floorAt(int t) {
        return y + slope * (t - start);
    }

    /** One box for a flat tunnel, one box per step for a staircase. */
    public List<AABB> boxes() {
        if (slope == 0) return List.of(box(start, end, y));
        List<AABB> out = new ArrayList<>(end - start + 1);
        for (int t = start; t <= end; t++) out.add(box(t, t, floorAt(t)));
        return out;
    }

    private AABB box(int from, int to, int floor) {
        if (axis == 0) return new AABB(from, floor, lateral, to + 1, floor + height, lateral + width);
        return new AABB(lateral, floor, from, lateral + width, floor + height, to + 1);
    }

    public boolean contains(int x, int yy, int z) {
        int along = axis == 0 ? x : z;
        int side = axis == 0 ? z : x;
        if (along < start || along > end || side < lateral || side >= lateral + width) return false;
        int f = floorAt(along);
        return yy >= f && yy < f + height;
    }

    public int length() {
        return end - start + 1;
    }

    /** "3x3", "1x2", "1x3 stairs" */
    public String sizeName() {
        return width + "x" + height + (slope != 0 ? " stairs" : "");
    }

    /** Fraction of the tunnel's blocks that you mined yourself. */
    public double mineFraction() {
        int total = 0, mine = 0;
        for (int t = start; t <= end; t++) {
            int f = floorAt(t);
            for (int dy = 0; dy < height; dy++) {
                for (int dl = 0; dl < width; dl++) {
                    int x = axis == 0 ? t : lateral + dl;
                    int z = axis == 0 ? lateral + dl : t;
                    total++;
                    if (MyEdits.isMine(x, f + dy, z)) mine++;
                }
            }
        }
        return total == 0 ? 0 : (double) mine / total;
    }

    /** Tunnels you dug (more than a quarter of it) don't count. */
    public boolean isMine() {
        return mineFraction() > 0.25;
    }
}
