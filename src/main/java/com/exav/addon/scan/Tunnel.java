package com.exav.addon.scan;

import net.minecraft.world.phys.AABB;

/**
 * A straight 3x3 tunnel. axis 0 runs along X, axis 1 along Z.
 * y is the floor-level air block, lateral is the lowest Z (axis 0) or X (axis 1) of the 3 wide interior,
 * start/end are the inclusive coordinates along the axis.
 */
public record Tunnel(int axis, int y, int lateral, int start, int end) {
    public AABB box() {
        if (axis == 0) {
            return new AABB(start, y, lateral, end + 1, y + 3, lateral + 3);
        }
        return new AABB(lateral, y, start, lateral + 3, y + 3, end + 1);
    }

    public boolean contains(int x, int yy, int z) {
        if (yy < y || yy >= y + 3) return false;
        int along = axis == 0 ? x : z;
        int side = axis == 0 ? z : x;
        return along >= start && along <= end && side >= lateral && side < lateral + 3;
    }

    public int length() {
        return end - start + 1;
    }
}
