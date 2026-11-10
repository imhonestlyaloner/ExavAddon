package com.exav.addon.holiday;

import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;

/**
 * The smooth models. Model units are about 1/45 of a block; +Y is "up the item", +Z is its front (toward you),
 * and each model's grip point (where the hand holds it) is given by the constants below.
 */
public final class HolidayModels {
    private HolidayModels() {
    }

    // ---------------------------------------------------------------- candy cane
    // Licked to a point at the top. The hand sits INSIDE the curve of the hook, so the hook wraps around
    // the fist like a sword's hand guard and the straight part sticks out like the blade.
    public static final float CANE_HOOK_R = 7.0F;
    public static final float CANE_GRIP_X = CANE_HOOK_R, CANE_GRIP_Y = -1.5F;
    /** where the bite goes (just below the licked point) */
    public static final float CANE_BITE_X = 0.0F, CANE_BITE_Y = 27.0F;
    private static final float CANE_R = 1.8F, CANE_TOP = 31.0F, LICK_FROM = 21.0F, BITE_CUT = 24.5F;

    public static final int RED = 0xD31F2A, WHITE = 0xF7F4F1, GREEN = 0x1F9A4E;
    private static final int SUGAR = 0xF4E4E2;

    /** The cane's centre line, from the end of the hook to the top, plus where the straight part starts. */
    private static List<Vector3f> canePath(float top) {
        List<Vector3f> p = new ArrayList<>();
        float r = CANE_HOOK_R, tail = 2.5F;
        for (float y = tail; y > 0.0F; y -= 0.3F) p.add(new Vector3f(2 * r, y, 0));
        for (int i = 0; i <= 60; i++) { // half circle from (2r,0) down through (r,-r) to (0,0)
            double a = Math.PI * 2 - Math.PI * i / 60.0;
            p.add(new Vector3f(r + r * (float) Math.cos(a), r * (float) Math.sin(a), 0));
        }
        for (float y = 0.3F; y < top; y += 0.3F) p.add(new Vector3f(0, y, 0));
        p.add(new Vector3f(0, top, 0));
        return p;
    }

    /** distance along the path where the straight part starts */
    private static final float SHAFT_START = 2.5F + (float) Math.PI * CANE_HOOK_R;

    private static SmoothMesh cane(boolean redGreen, boolean bitten) {
        float top = bitten ? BITE_CUT : CANE_TOP;
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        Shapes.tube(b, canePath(top), s -> {
            float r = CANE_R;
            if (s < CANE_R) r *= (float) Math.sqrt(1 - Math.pow(1 - s / CANE_R, 2)); // rounded end of the hook
            float y = s - SHAFT_START; // height up the straight part
            if (y > LICK_FROM) {
                // licked to a point: thins smoothly, a little lopsided like it's been licked on one side
                float u = Math.min(1.0F, (y - LICK_FROM) / (CANE_TOP - LICK_FROM));
                r *= (float) Math.pow(1 - u, 0.8);
            }
            return r;
        }, (s, a) -> {
            // spiral stripes
            float phase = s / 5.5F + a / (float) (Math.PI * 2);
            float f = phase - (float) Math.floor(phase);
            if (redGreen) return f < 0.5F ? RED : GREEN;
            return f < 0.4F ? RED : WHITE;
        }, 20);
        if (bitten) {
            // the cut end: sugary inside with a ragged bite edge
            for (int k = 0; k < 14; k++) {
                double a = Math.PI * 2 * k / 14;
                float jag = (k % 2 == 0) ? 0.5F : 0.0F;
                float r = CANE_R * 0.6F;
                Shapes.sphere(b, r * (float) Math.cos(a), top + jag, r * (float) Math.sin(a), 0.55F, SUGAR, 3);
            }
        }
        return b.build();
    }

    public static SmoothMesh cane(boolean redGreen) {
        return cane(redGreen, false);
    }

    public static SmoothMesh caneBitten(boolean redGreen) {
        return cane(redGreen, true);
    }

    // ---------------------------------------------------------------- hot cocoa mug
    public static final float MUG_GRIP_X = 9.9F, MUG_GRIP_Y = 5.3F;
    public static final float MUG_SURFACE_Y = 8.9F;
    public static final float MUG_RIM_Y = 10.6F, MUG_RIM_R = 5.4F;

    private static final int MUG = 0xC62E2E, MUG_DARK = 0x9E2222, BAND = 0xF4F1EA, COCOA = 0x5B3420, FOAM = 0x9A6A48;

    public static SmoothMesh mug() {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        // one profile from the middle of the bottom, up the outside, over the rounded rim, down the inside
        float[][] profile = {{0, 0}, {4.8F, 0}, {5.45F, 0.25F}, {5.8F, 0.9F}, {5.8F, 3.6F}, {5.8F, 4.0F}, {5.8F, 6.0F},
                {5.8F, 6.4F}, {5.8F, 9.6F}, {5.72F, 10.2F}, {5.45F, 10.55F}, {5.05F, 10.55F}, {4.85F, 10.2F},
                {4.8F, 9.6F}, {4.8F, MUG_SURFACE_Y}};
        Shapes.lathe(b, profile, 32, true, (y, a, i) -> {
            if (i >= 9) return MUG_DARK;
            if (y >= 4.0F && y <= 6.0F) {
                // white band with little red dots
                double dot = Math.cos(a * 10) * Math.cos((y - 5.0F) * 3.0F);
                return dot > 0.75 ? MUG : BAND;
            }
            return MUG;
        });
        // cocoa with a swirl of foam
        Shapes.disc(b, MUG_SURFACE_Y, 4.8F, true, 32, 6, (x, z) -> {
            double r = Math.sqrt(x * x + z * z), a = Math.atan2(z, x);
            return Math.sin(a * 2 + r * 1.3) > 0.55 ? FOAM : COCOA;
        });
        // handle: a loop on the +X side
        List<Vector3f> path = new ArrayList<>();
        for (int i = 0; i <= 40; i++) {
            double a = Math.toRadians(125 - 250.0 * i / 40);
            path.add(new Vector3f(7.0F + 2.9F * (float) Math.cos(a), 5.3F + 2.9F * (float) Math.sin(a), 0));
        }
        Shapes.tube(b, path, s -> 0.85F, (s, a) -> MUG, 12);
        return b.build();
    }

    private static final int MALLOW = 0xFFFBF5, MALLOW_SIDE = 0xF7E8E4;

    /** a soft cylinder marshmallow, centred on its own origin */
    public static SmoothMesh marshmallow() {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        float[][] profile = {{0, -1.1F}, {1.2F, -1.1F}, {1.45F, -0.85F}, {1.52F, 0}, {1.45F, 0.85F}, {1.2F, 1.1F}, {0, 1.1F}};
        Shapes.lathe(b, profile, 14, true, (y, a, i) -> (i == 0 || i == 6 || i == 5) ? MALLOW : MALLOW_SIDE);
        return b.build();
    }

    // ---------------------------------------------------------------- gingerbread man
    public static final float GINGER_GRIP_X = 0.0F, GINGER_GRIP_Y = 3.5F;
    /** where the waving arm's shoulder is, how long the upper arm is */
    public static final float GINGER_SHOULDER_X = 4.4F, GINGER_SHOULDER_Y = 13.6F, GINGER_UPPER_LEN = 4.0F;
    public static final float GINGER_BITE_X = 3.0F, GINGER_BITE_Y = 22.5F;

    private static final int COOKIE = 0xC0712F, COOKIE_EDGE = 0x8E4B1C, ICING = 0xFBF7F0, EYE = 0x2B1A10,
            BUTTON_RED = 0xD32F2F, BUTTON_GREEN = 0x2E9E58;
    private static final float THICK = 1.3F, PUFF = 2.2F, STEP = 0.28F;

    private static float body(float x, float y) {
        float d = Shapes.circle(x, y, 0, 19.6F, 5.0F);
        d = Shapes.smin(d, Shapes.capsule(x, y, 0, 7.5F, 0, 13.5F, 4.5F), 1.4F);
        d = Shapes.smin(d, Shapes.capsule(x, y, -2.4F, 6.5F, -3.6F, 1.4F, 2.3F), 1.2F);
        d = Shapes.smin(d, Shapes.capsule(x, y, 2.4F, 6.5F, 3.6F, 1.4F, 2.3F), 1.2F);
        d = Shapes.smin(d, Shapes.capsule(x, y, -3.5F, 13.2F, -8.4F, 9.6F, 2.0F), 1.2F); // the arm that stays still
        d = Shapes.smin(d, Shapes.capsule(x, y, 3.0F, 13.2F, GINGER_SHOULDER_X, GINGER_SHOULDER_Y, 2.0F), 1.0F); // shoulder
        return d;
    }

    private static float bite(float x, float y) {
        float d = Shapes.circle(x, y, 3.8F, 23.4F, 3.0F);
        d = Math.min(d, Shapes.circle(x, y, 1.4F, 25.0F, 2.0F));
        d = Math.min(d, Shapes.circle(x, y, 5.7F, 21.1F, 1.7F));
        return d;
    }

    private static int icing(float x, float y) {
        if (Shapes.circle(x, y, -1.8F, 20.6F, 0.8F) < 0 || Shapes.circle(x, y, 1.8F, 20.6F, 0.8F) < 0) return EYE;
        if (Math.abs(Shapes.circle(x, y, 0, 20.4F, 2.6F)) < 0.33F && y < 19.4F) return ICING;          // smile
        if (Shapes.circle(x, y, 0, 13.0F, 0.85F) < 0 || Shapes.circle(x, y, 0, 7.4F, 0.85F) < 0) return BUTTON_RED;
        if (Shapes.circle(x, y, 0, 10.2F, 0.85F) < 0) return BUTTON_GREEN;
        if (Math.abs(x) > 1.2F && Math.abs(y - (2.6F + 0.35F * (float) Math.sin(x * 4))) < 0.28F) return ICING; // ankles
        if (Math.abs(Shapes.circle(x, y, -8.4F, 9.6F, 1.5F)) < 0.28F && x > -9.2F) return ICING;     // wrist
        return -1;
    }

    private static SmoothMesh ginger(boolean bitten) {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        Shapes.Field field = bitten ? (x, y) -> Math.max(body(x, y), -bite(x, y)) : HolidayModels::body;
        Shapes.puffy(b, field, -11.5F, -0.5F, 11.5F, 25.5F, STEP, THICK, PUFF,
                (x, y, depth) -> {
                    int ice = icing(x, y);
                    return ice >= 0 ? ice : cookie(depth);
                },
                (x, y, depth) -> cookie(depth),
                (x, y) -> icing(x, y) >= 0 ? 0.25F : 0.0F);
        return b.build();
    }

    private static int cookie(float depth) {
        return Shapes.mix(COOKIE_EDGE, COOKIE, depth / 1.6F);
    }

    public static SmoothMesh gingerWhole() {
        return ginger(false);
    }

    public static SmoothMesh gingerBitten() {
        return ginger(true);
    }

    /** upper part of the waving arm, shoulder at (0,0), pointing along +X */
    public static SmoothMesh gingerUpperArm() {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        Shapes.puffy(b, (x, y) -> Shapes.capsule(x, y, 0, 0, GINGER_UPPER_LEN, 0, 1.9F), -2.5F, -2.5F, 6.5F, 2.5F, STEP, THICK, PUFF,
                (x, y, d) -> cookie(d), (x, y, d) -> cookie(d), null);
        return b.build();
    }

    /** forearm and mitten hand of the waving arm, elbow at (0,0), pointing along +X */
    public static SmoothMesh gingerForearm() {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        Shapes.Field f = (x, y) -> Shapes.smin(Shapes.capsule(x, y, 0, 0, 3.2F, 0, 1.75F), Shapes.circle(x, y, 4.0F, 0, 2.0F), 1.0F);
        Shapes.puffy(b, f, -2.5F, -2.8F, 6.6F, 2.8F, STEP, THICK, PUFF,
                (x, y, d) -> Math.abs(Shapes.circle(x, y, 4.0F, 0, 1.55F)) < 0.28F && x < 3.9F ? ICING : cookie(d),
                (x, y, d) -> cookie(d),
                (x, y) -> Math.abs(Shapes.circle(x, y, 4.0F, 0, 1.55F)) < 0.28F && x < 3.9F ? 0.25F : 0.0F);
        return b.build();
    }

    // ---------------------------------------------------------------- birthday cake
    /** held from under the plate */
    public static final float CAKE_GRIP_X = 0.0F, CAKE_GRIP_Y = -1.6F;
    public static final float CAKE_TOP_Y = 8.7F, CANDLE_TOP_Y = 12.4F;
    /** candle positions on the top tier (x, z) */
    public static final float[][] CANDLES = {{0, 0}, {2.3F, 1.0F}, {-2.2F, 1.2F}, {1.0F, -2.3F}, {-1.3F, -2.1F}};
    private static final int[] CANDLE_COLORS = {0x42A5F5, 0xEC407A, 0xFFCA28, 0x66BB6A, 0xAB47BC};
    private static final int PLATE = 0xF2F2F5, SPONGE_PINK = 0xF48FB1, FROST_WHITE = 0xFFF8F2, TOP_TIER = 0xB3E5FC, DRIP_PINK = 0xF06292;
    private static final int[] SPRINKLES = {0xE53935, 0xFDD835, 0x43A047, 0x1E88E5, 0x8E24AA, 0xFB8C00, 0xFFFFFF};

    public static SmoothMesh cake() {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        // plate
        Shapes.lathe(b, new float[][]{{0, -1.6F}, {6.8F, -1.6F}, {8.2F, -0.95F}, {8.3F, -0.7F}, {7.6F, -0.75F}, {0, -0.75F}}, 36, true, (y, a, i) -> PLATE);
        // bottom tier: pink with white frosting dripping down from the top edge
        Shapes.lathe(b, new float[][]{{0, -0.75F}, {6.0F, -0.75F}, {6.1F, -0.5F}, {6.1F, 1.0F}, {6.1F, 2.0F}, {6.1F, 2.8F}, {6.1F, 3.4F},
                {6.1F, 3.9F}, {5.85F, 4.5F}, {5.2F, 4.65F}, {0, 4.65F}}, 48, true, (y, a, i) -> {
            float drip = 3.0F - 1.4F * (float) Math.max(0, Math.sin(a * 9)) - 0.6F * (float) Math.max(0, Math.sin(a * 23 + 1));
            return y >= drip ? FROST_WHITE : SPONGE_PINK;
        });
        // top tier: light blue with pink drips
        Shapes.lathe(b, new float[][]{{0, 4.65F}, {4.2F, 4.65F}, {4.25F, 5.0F}, {4.25F, 6.2F}, {4.25F, 7.0F}, {4.25F, 7.6F}, {4.25F, 8.1F},
                {4.05F, 8.6F}, {3.6F, CAKE_TOP_Y}, {0, CAKE_TOP_Y}}, 40, true, (y, a, i) -> {
            float drip = 7.3F - 1.2F * (float) Math.max(0, Math.sin(a * 7 + 0.5)) - 0.5F * (float) Math.max(0, Math.sin(a * 17));
            return y >= drip ? DRIP_PINK : TOP_TIER;
        });
        // sprinkles on top and on the ledge of the bottom tier
        java.util.Random r = new java.util.Random(12);
        for (int i = 0; i < 46; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            boolean top = i < 24;
            float rad = top ? 0.6F + r.nextFloat() * 3.0F : 4.55F + r.nextFloat() * 1.0F;
            float x = rad * (float) Math.cos(a), z = rad * (float) Math.sin(a);
            if (top && near(x, z)) continue;
            Shapes.sphere(b, x, top ? CAKE_TOP_Y + 0.1F : 4.75F, z, 0.28F, SPRINKLES[i % SPRINKLES.length], 3);
        }
        // candles with stripes and wicks
        for (int c = 0; c < CANDLES.length; c++) {
            float cx = CANDLES[c][0], cz = CANDLES[c][1];
            int col = CANDLE_COLORS[c];
            java.util.List<Vector3f> path = new java.util.ArrayList<>();
            for (float y = CAKE_TOP_Y - 0.3F; y <= CANDLE_TOP_Y; y += 0.25F) path.add(new Vector3f(cx, y, cz));
            Shapes.tube(b, path, s -> 0.38F, (s, a) -> {
                float f = s / 1.1F + a / (float) (Math.PI * 2);
                return f - (float) Math.floor(f) < 0.5F ? col : 0xFFFFFF;
            }, 10);
            java.util.List<Vector3f> wick = java.util.List.of(new Vector3f(cx, CANDLE_TOP_Y, cz), new Vector3f(cx, CANDLE_TOP_Y + 0.5F, cz));
            Shapes.tube(b, wick, s -> 0.09F, (s, a) -> 0x222222, 6);
        }
        return b.build();
    }

    private static boolean near(float x, float z) {
        for (float[] c : CANDLES) {
            if (Math.hypot(x - c[0], z - c[1]) < 0.8) return true;
        }
        return false;
    }

    /** a teardrop flame, base at (0,0,0), drawn on top of each candle wick */
    public static SmoothMesh flame() {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        Shapes.lathe(b, new float[][]{{0, 0}, {0.32F, 0.15F}, {0.42F, 0.45F}, {0.36F, 0.85F}, {0.2F, 1.25F}, {0, 1.6F}}, 10, true,
                (y, a, i) -> Shapes.mix(0xFF8F00, 0xFFF59D, y / 1.4F));
        return b.build();
    }

    /** a stick of dynamite, bottom at (0,0,0), with a curly fuse out of the top */
    public static final float DYNAMITE_LEN = 7.0F;
    public static final float[][] FUSE = {{0, 7.0F}, {0.2F, 7.7F}, {0.8F, 8.3F}, {1.5F, 8.5F}, {2.0F, 9.1F}, {2.1F, 9.8F}};

    public static SmoothMesh dynamite() {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        java.util.List<Vector3f> body = new java.util.ArrayList<>();
        for (float y = 0; y <= DYNAMITE_LEN; y += 0.25F) body.add(new Vector3f(0, y, 0));
        Shapes.tube(b, body, s -> 1.15F, (s, a) -> (s > 0.6F && s < 1.2F) || (s > 5.8F && s < 6.4F) ? 0xE8D3A6 : 0xC62828, 16);
        java.util.List<Vector3f> fuse = new java.util.ArrayList<>();
        for (float[] p : FUSE) fuse.add(new Vector3f(p[0], p[1], 0));
        Shapes.tube(b, fuse, s -> 0.16F, (s, a) -> 0x3E3A36, 6);
        return b.build();
    }

    /** a round puff, for smoke and the explosion flash (coloured when drawn) */
    public static SmoothMesh puff() {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        Shapes.sphere(b, 0, 0, 0, 1.0F, 0xFFFFFF, 8);
        return b.build();
    }

    // ---------------------------------------------------------------- crumbs
    public static SmoothMesh crumb(int rgb) {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        Shapes.sphere(b, 0, 0, 0, 0.6F, rgb, 4);
        return b.build();
    }

    public static final int CRUMB_COOKIE = COOKIE;
}
