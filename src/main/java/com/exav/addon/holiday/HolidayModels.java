package com.exav.addon.holiday;

/**
 * The voxel models. Each is built so the point the hand holds is at (gripX, gripY, 0); model +Y is "up the item"
 * and model +Z is its front (the side that faces you).
 */
public final class HolidayModels {
    private HolidayModels() {
    }

    // ---------------------------------------------------------------- candy cane
    // The hook is the handle: the hand holds the bottom of the curve, the straight part sticks out like a blade.
    public static final float CANE_GRIP_X = 5.0F, CANE_GRIP_Y = -5.0F;
    /** top end of the straight part, where the bite goes */
    public static final float CANE_TIP_X = 0.0F, CANE_TIP_Y = 34.0F;

    private static final int RED = 0xD31F2A, WHITE = 0xF6F3F0, MINT = 0x2E9E58;

    private static VoxelMesh.Builder cane() {
        VoxelMesh.Builder b = new VoxelMesh.Builder();
        // centre line: hook (half circle radius 5 around (5,0), from (0,0) down through (5,-5) to (10,0)), short tail, shaft up to y=34
        for (int x = -3; x <= 13; x++) {
            for (int y = -8; y <= 35; y++) {
                for (int z = -2; z <= 2; z++) {
                    float cx = x + 0.5F, cy = y + 0.5F, cz = z + 0.5F;
                    float[] near = nearestOnCane(cx, cy);
                    float dx = cx - near[0], dy = cy - near[1];
                    if (dx * dx + dy * dy + cz * cz > 1.75F * 1.75F) continue;
                    // diagonal stripes: along the path plus a twist around it
                    float along = near[2] + (dx + cz) * 0.9F;
                    int band = Math.floorMod((int) Math.floor(along / 2.2F), 5);
                    b.set(x, y, z, band == 0 || band == 1 ? RED : band == 3 ? MINT : WHITE);
                }
            }
        }
        return b;
    }

    /** nearest point on the cane's centre line and how far along it that is: {x, y, s} */
    private static float[] nearestOnCane(float px, float py) {
        float best = Float.MAX_VALUE, bx = 0, by = 0, bs = 0;
        // hook: angle from pi (at (0,0)) to 2pi (at (10,0)), then tail (10,0)->(10,2)
        float s = 0;
        for (int i = 0; i <= 40; i++) {
            double a = Math.PI + Math.PI * i / 40.0;
            float x = 5 + 5 * (float) Math.cos(a), y = 5 * (float) Math.sin(a);
            float d = (px - x) * (px - x) + (py - y) * (py - y);
            if (d < best) {
                best = d;
                bx = x;
                by = y;
                bs = 15.7F - (float) (5 * Math.PI * i / 40.0); // count from the tail end so the shaft continues the pattern
            }
        }
        // tail
        for (float y = 0; y <= 2; y += 0.25F) {
            float d = (px - 10) * (px - 10) + (py - y) * (py - y);
            if (d < best) {
                best = d;
                bx = 10;
                by = y;
                bs = -y;
            }
        }
        // shaft (0,0) -> (0,34)
        float y = Math.max(0, Math.min(34, py));
        float d = px * px + (py - y) * (py - y);
        if (d < best) {
            bx = 0;
            by = y;
            bs = 15.7F + y;
        }
        return new float[]{bx, by, s + bs};
    }

    public static VoxelMesh caneWhole() {
        return cane().build();
    }

    /** a bite out of the top: a few rows gone plus a jagged notch */
    public static VoxelMesh caneBitten() {
        return cane().carve((x, y, z) -> y >= 31 || (y >= 28 && x + z >= 0) || (y == 27 && x >= 1 && z >= 0)).build();
    }

    // ---------------------------------------------------------------- hot cocoa mug
    public static final float MUG_GRIP_X = 8.5F, MUG_GRIP_Y = 5.0F;
    /** centre of the cocoa surface, where marshmallows land */
    public static final float MUG_SURFACE_Y = 9.0F;
    public static final float MUG_RIM_Y = 11.0F;

    private static final int MUG = 0xC62E2E, MUG_DARK = 0xA22424, BAND = 0xF4F1EA, COCOA = 0x5B3420, COCOA_FOAM = 0x8A5A3C;

    public static VoxelMesh mug() {
        VoxelMesh.Builder b = new VoxelMesh.Builder();
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                float cx = x + 0.5F, cz = z + 0.5F;
                float r2 = cx * cx + cz * cz;
                if (r2 > 5.6F * 5.6F) continue;
                boolean wall = r2 > 4.3F * 4.3F;
                for (int y = 0; y <= 10; y++) {
                    if (wall || y <= 1) {
                        int c = (y == 4 || y == 5) ? BAND : (y == 10 ? MUG_DARK : MUG);
                        // little red dots on the white band, like a snowflake pattern
                        if ((y == 4 || y == 5) && Math.floorMod(x * 3 + z * 5 + y, 7) == 0) c = MUG;
                        b.set(x, y, z, c);
                    } else if (y <= 8) {
                        b.set(x, y, z, y == 8 ? (Math.floorMod(x * 7 + z * 3, 5) == 0 ? COCOA_FOAM : COCOA) : COCOA);
                    }
                }
            }
        }
        // handle on +X: a ring around (7,5)
        for (int x = 5; x <= 10; x++) {
            for (int y = 1; y <= 9; y++) {
                for (int z = -1; z <= 0; z++) {
                    float dx = x + 0.5F - 7.0F, dy = y + 0.5F - 5.5F;
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d >= 2.0F && d <= 3.6F && x + 0.5F >= 5.6F) b.set(x, y, z, MUG);
                }
            }
        }
        return b.build();
    }

    private static final int MALLOW = 0xFFFAF2, MALLOW_SIDE = 0xF6E7E2;

    /** a 3x3x3 marshmallow, centred on its own origin */
    public static VoxelMesh marshmallow() {
        VoxelMesh.Builder b = new VoxelMesh.Builder();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (Math.abs(x) + Math.abs(y) + Math.abs(z) == 3) continue; // rounded corners
                    b.set(x, y, z, y == 1 ? MALLOW : MALLOW_SIDE);
                }
            }
        }
        return b.build();
    }

    // ---------------------------------------------------------------- gingerbread man
    public static final float GINGER_GRIP_X = 0.0F, GINGER_GRIP_Y = 3.0F;
    /** shoulder the waving arm turns around */
    public static final float GINGER_SHOULDER_X = 4.0F, GINGER_SHOULDER_Y = 13.5F;
    public static final float GINGER_HEAD_X = 0.0F, GINGER_HEAD_Y = 19.5F;

    private static final int COOKIE = 0xB86A2E, COOKIE_EDGE = 0x95511F, ICING = 0xFBF7F0, EYE = 0x2B1A10,
            BUTTON_RED = 0xD32F2F, BUTTON_GREEN = 0x2E9E58;

    private static VoxelMesh.Builder gingerBody() {
        VoxelMesh.Builder b = new VoxelMesh.Builder();
        for (int x = -10; x <= 10; x++) {
            for (int y = 0; y <= 25; y++) {
                if (!inBody(x + 0.5F, y + 0.5F)) continue;
                boolean edge = !inBody(x + 1.5F, y + 0.5F) || !inBody(x - 0.5F, y + 0.5F)
                        || !inBody(x + 0.5F, y + 1.5F) || !inBody(x + 0.5F, y - 0.5F);
                b.set(x, y, 0, edge ? COOKIE_EDGE : COOKIE);
                b.set(x, y, 1, edge ? COOKIE_EDGE : COOKIE);
            }
        }
        // icing on the front (z = 2)
        b.set(-2, 20, 2, EYE).set(1, 20, 2, EYE);                                // eyes
        b.set(-2, 18, 2, ICING).set(-1, 17, 2, ICING).set(0, 17, 2, ICING).set(1, 18, 2, ICING); // smile
        b.set(0, 12, 2, BUTTON_RED).set(0, 9, 2, BUTTON_GREEN).set(0, 6, 2, BUTTON_RED);         // buttons
        for (int x = -5; x <= -2; x++) b.set(x, 2, 2, ICING);                                    // ankle icing
        for (int x = 2; x <= 5; x++) b.set(x, 2, 2, ICING);
        b.set(-8, 10, 2, ICING).set(-7, 11, 2, ICING);                                          // wrist icing (still arm)
        return b;
    }

    private static boolean inBody(float x, float y) {
        // head
        float hx = x - GINGER_HEAD_X, hy = y - GINGER_HEAD_Y;
        if (hx * hx + hy * hy <= 4.6F * 4.6F) return true;
        // torso
        if (Math.abs(x) <= 4.2F && y >= 5 && y <= 16) return true;
        // legs (rounded feet)
        if (y >= 0 && y < 7 && Math.abs(x) >= 1.2F && Math.abs(x) <= 5.2F) return !(y < 1 && (Math.abs(x) < 1.8F || Math.abs(x) > 4.6F));
        // left arm (the one that stays still), angled down-out
        return distToSegment(x, y, -3.5F, 13.5F, -8.5F, 10.0F) <= 1.7F;
    }

    public static VoxelMesh gingerWhole() {
        return gingerBody().build();
    }

    /** a bite out of the head (top right) */
    public static VoxelMesh gingerBitten() {
        return gingerBody().carve((x, y, z) -> {
            float dx = x + 0.5F - 3.6F, dy = y + 0.5F - 22.6F;
            float dx2 = x + 0.5F - 1.2F, dy2 = y + 0.5F - 24.4F;
            return dx * dx + dy * dy <= 3.3F * 3.3F || dx2 * dx2 + dy2 * dy2 <= 2.0F * 2.0F;
        }).build();
    }

    /** the waving arm, built around its own shoulder pivot at (0,0); points up and out */
    public static VoxelMesh gingerArm() {
        VoxelMesh.Builder b = new VoxelMesh.Builder();
        for (int x = -2; x <= 8; x++) {
            for (int y = -2; y <= 8; y++) {
                float d = distToSegment(x + 0.5F, y + 0.5F, -0.5F, 0.0F, 5.0F, 4.5F);
                if (d > 1.7F) continue;
                boolean edge = distToSegment(x + 0.5F, y + 0.5F, -0.5F, 0.0F, 5.0F, 4.5F) > 1.0F;
                b.set(x, y, 0, edge ? COOKIE_EDGE : COOKIE);
                b.set(x, y, 1, edge ? COOKIE_EDGE : COOKIE);
            }
        }
        b.set(4, 3, 2, ICING).set(5, 4, 2, ICING); // wrist icing
        return b.build();
    }

    private static float distToSegment(float px, float py, float ax, float ay, float bx, float by) {
        float vx = bx - ax, vy = by - ay;
        float t = Math.max(0, Math.min(1, ((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy)));
        float dx = px - (ax + t * vx), dy = py - (ay + t * vy);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ---------------------------------------------------------------- crumbs
    /** a single crumb voxel, centred */
    public static VoxelMesh crumb(int rgb) {
        return new VoxelMesh.Builder().set(0, 0, 0, rgb).build();
    }

    public static final int CRUMB_CANE = RED, CRUMB_CANE_2 = WHITE, CRUMB_COOKIE = COOKIE;
}
