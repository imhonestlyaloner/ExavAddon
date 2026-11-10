package com.exav.addon.scan;

import net.minecraft.world.phys.AABB;

/**
 * A straight dug tunnel (3x3, or 1x2 for branch mines). axis 0 runs along X, axis 1 along Z.
 * y is the floor-level air block, lateral is the lowest Z (axis 0) or X (axis 1) of the interior,
 * start/end are the inclusive coordinates along the axis.
 */
public record Tunnel(int axis, int y, int lateral, int start, int end, int width, int height) {
    public AABB box() {
        if (axis == 0) {
            return new AABB(start, y, lateral, end + 1, y + height, lateral + width);
        }
        return new AABB(lateral, y, start, lateral + width, y + height, end + 1);
    }

    public boolean contains(int x, int yy, int z) {
        if (yy < y || yy >= y + height) return false;
        int along = axis == 0 ? x : z;
        int side = axis == 0 ? z : x;
        return along >= start && along <= end && side >= lateral && side < lateral + width;
    }

    public int length() {
        return end - start + 1;
    }
}
