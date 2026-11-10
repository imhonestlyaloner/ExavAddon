package com.exav.addon.sky;

import com.exav.addon.holiday.Shapes;
import com.exav.addon.holiday.SmoothMesh;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;

/** Smooth models for the artist easter eggs. Each is roughly 10 units tall and centred near its own origin. */
public final class SkyArt {
    private SkyArt() {
    }

    // ---------------------------------------------------------------- Yeat: bells

    private static final int GOLD = 0xF2C14E, GOLD_DARK = 0xB8862B, CLAPPER = 0x7A5A1E, BELL_GREEN = 0x2BD65E;

    /** a church-style bell hanging from (0,0,0) (its loop), opening at y = -10 */
    public static SmoothMesh bell() {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        // bell body, outside then folding into the inside
        float[][] profile = {{0, -1.0F}, {1.2F, -1.0F}, {2.2F, -1.5F}, {3.0F, -2.6F}, {3.4F, -4.2F}, {3.7F, -6.0F},
                {4.3F, -7.6F}, {4.6F, -8.0F}, {4.75F, -8.2F}, {5.0F, -8.55F}, {5.15F, -8.75F}, {5.3F, -8.9F}, {5.8F, -9.6F}, {5.6F, -10.0F}, {4.8F, -9.6F}, {3.9F, -8.6F},
                {3.1F, -6.4F}, {2.6F, -3.6F}, {1.4F, -2.0F}, {0, -1.9F}};
        Shapes.lathe(b, profile, 28, true, (y, a, i) -> {
            if (i >= 14) return GOLD_DARK;               // inside
            if (y <= -8.15F && y >= -8.8F) return BELL_GREEN; // green band round the lip
            return GOLD;
        });
        // clapper
        List<Vector3f> rod = List.of(new Vector3f(0, -2.0F, 0), new Vector3f(0, -8.6F, 0));
        Shapes.tube(b, rod, s -> 0.3F, (s, a) -> CLAPPER, 6);
        Shapes.sphere(b, 0, -9.0F, 0, 1.0F, CLAPPER, 5);
        // hanging loop
        List<Vector3f> loop = new ArrayList<>();
        for (int i = 0; i <= 24; i++) {
            double a = Math.PI * i / 24;
            loop.add(new Vector3f((float) Math.cos(a) * 1.3F, -1.0F + (float) Math.sin(a) * 1.6F, 0));
        }
        Shapes.tube(b, loop, s -> 0.38F, (s, a) -> GOLD_DARK, 8);
        return b.build();
    }

    /** a little four-pointed sparkle, flat, facing +Z */
    public static SmoothMesh sparkle(int rgb) {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        Shapes.puffy(b, (x, y) -> {
            float ax = Math.abs(x), ay = Math.abs(y);
            return Math.min(ax * 3.2F + ay, ay * 3.2F + ax) - 3.0F; // a plus with pointy arms
        }, -3.5F, -3.5F, 3.5F, 3.5F, 0.25F, 0.4F, 0.8F, (x, y, d) -> rgb, (x, y, d) -> rgb, null);
        return b.build();
    }

    // ---------------------------------------------------------------- Insane Clown Posse

    private static final int PAINT_WHITE = 0xF4F1EC, PAINT_BLACK = 0x111111, RED = 0xC4161C;

    /** a white clown face with black juggalo paint (diamond eyes, painted mouth), facing +Z */
    public static SmoothMesh clownFace(int style) {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        Shapes.Field face = (x, y) -> {
            // a slightly long, rounded face
            float dx = x / 4.4F, dy = y / 5.4F;
            return ((float) Math.sqrt(dx * dx + dy * dy) - 1.0F) * 4.6F;
        };
        Shapes.puffy(b, face, -5.0F, -6.0F, 5.0F, 6.0F, 0.22F, 1.4F, 2.4F,
                (x, y, d) -> paint(x, y, style),
                (x, y, d) -> PAINT_WHITE,
                (x, y) -> paint(x, y, style) == PAINT_BLACK ? 0.12F : 0.0F);
        // red nose for some of them
        if (style == 2) Shapes.sphere(b, 0, -0.4F, 1.6F, 0.8F, RED, 6);
        return b.build();
    }

    private static int paint(float x, float y, int style) {
        // tall black diamonds through the eyes
        float ex = Math.abs(x) - 1.7F, ey = y - 1.3F;
        if (Math.abs(ex) / 0.9F + Math.abs(ey) / (style == 1 ? 3.6F : 2.6F) < 1.0F) return PAINT_BLACK;
        // black eye holes
        if (Math.hypot(ex, ey) < 0.45F) return PAINT_BLACK;
        // painted mouth: a wide black grin with spikes down the chin
        if (style == 0) {
            if (Math.abs(y + 2.3F + 0.25F * x * x / 4) < 0.35F && Math.abs(x) < 2.6F) return PAINT_BLACK;
            if (y < -2.3F && y > -4.6F && Math.abs(x) < 1.6F && Math.abs(Math.abs(x) - 0.8F) < 0.3F * (y + 4.6F)) return PAINT_BLACK;
        } else {
            float m = Math.abs(x) / 2.4F + Math.abs(y + 2.6F) / 1.1F;
            if (m < 1.0F && m > 0.55F) return PAINT_BLACK;
        }
        // eyebrows
        if (style != 1 && Math.abs(y - 4.1F + 0.15F * Math.abs(x)) < 0.22F && Math.abs(x) > 0.6F && Math.abs(x) < 3.2F) return PAINT_BLACK;
        return PAINT_WHITE;
    }

    /** the running hatchet man silhouette, black with a red edge, facing +Z */
    public static SmoothMesh hatchetMan() {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        Shapes.Field f = (x, y) -> {
            float d = Shapes.circle(x, y, 0.6F, 4.2F, 1.15F);                                     // head
            d = Shapes.smin(d, Shapes.capsule(x, y, 0.3F, 2.8F, -0.3F, -0.8F, 1.05F), 0.6F);    // body leaning forward
            d = Shapes.smin(d, Shapes.capsule(x, y, -0.3F, -0.8F, 1.6F, -3.0F, 0.6F), 0.4F);    // front leg
            d = Shapes.smin(d, Shapes.capsule(x, y, 1.6F, -3.0F, 1.4F, -5.0F, 0.55F), 0.3F);
            d = Shapes.smin(d, Shapes.capsule(x, y, -0.3F, -0.8F, -2.0F, -2.6F, 0.6F), 0.4F);   // back leg
            d = Shapes.smin(d, Shapes.capsule(x, y, -2.0F, -2.6F, -3.4F, -2.2F, 0.55F), 0.3F);
            d = Shapes.smin(d, Shapes.capsule(x, y, 0.2F, 2.2F, -1.8F, 0.4F, 0.5F), 0.4F);      // back arm
            d = Shapes.smin(d, Shapes.capsule(x, y, 0.3F, 2.3F, 2.0F, 3.6F, 0.5F), 0.4F);       // arm up holding the hatchet
            d = Math.min(d, Shapes.capsule(x, y, 2.0F, 3.0F, 2.6F, 6.0F, 0.25F));                // handle
            d = Math.min(d, hatchetHead(x - 2.6F, y - 5.8F));                                     // blade
            return d;
        };
        Shapes.puffy(b, f, -4.5F, -6.0F, 5.5F, 7.5F, 0.16F, 0.6F, 0.6F,
                (x, y, d) -> d < 0.18F ? RED : PAINT_BLACK, (x, y, d) -> d < 0.18F ? RED : PAINT_BLACK, null);
        return b.build();
    }

    private static float hatchetHead(float x, float y) {
        // a blade sticking out to the right of the handle top, curved edge
        return Math.max(Math.abs(y) - 1.1F - 0.25F * x, Math.max(-x, x - 1.9F));
    }

    /** a hatchet: wooden handle and steel blade, facing +Z */
    public static SmoothMesh hatchet() {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        Shapes.puffy(b, (x, y) -> Shapes.capsule(x, y, 0, -5.0F, 0, 4.0F, 0.55F), -1.0F, -6.0F, 1.0F, 5.0F, 0.18F, 0.5F, 0.5F,
                (x, y, d) -> Shapes.mix(0x5D3A1A, 0x8B5A2B, d / 0.5F), (x, y, d) -> 0x5D3A1A, null);
        Shapes.Field blade = (x, y) -> {
            float d = Math.max(Math.abs(y - 3.2F) - 1.0F - 0.55F * Math.max(0, x - 0.4F), Math.max(-x - 0.6F, x - 4.0F));
            return d;
        };
        Shapes.puffy(b, blade, -1.0F, 0.0F, 4.5F, 7.0F, 0.15F, 0.35F, 0.6F,
                (x, y, d) -> x > 3.4F ? 0xF2F4F6 : Shapes.mix(0x6E7378, 0xB8BEC4, d / 0.6F),
                (x, y, d) -> 0x8A9096, null);
        return b.build();
    }

    /** a Faygo-style soda bottle in the given colour, standing on y = -5 */
    public static SmoothMesh faygo(int soda) {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        float[][] profile = {{0, -5.0F}, {1.6F, -5.0F}, {1.85F, -4.6F}, {1.9F, -3.5F}, {1.9F, -2.45F}, {1.9F, -2.35F}, {1.9F, -2.0F}, {1.9F, -0.5F}, {1.9F, -0.05F}, {1.9F, 0.05F}, {1.9F, 0.5F}, {1.75F, 1.5F},
                {1.1F, 2.8F}, {0.7F, 3.6F}, {0.65F, 4.3F}, {0.78F, 4.4F}, {0.78F, 5.0F}, {0, 5.0F}};
        Shapes.lathe(b, profile, 20, true, (y, a, i) -> {
            if (i >= 14) return 0xE8E8E8;                      // cap
            if (y > -2.4F && y < 0.0F) return label(y, a);       // label
            return soda;
        });
        return b.build();
    }

    private static int label(float y, float a) {
        if (y > -0.5F || y < -2.0F) return 0xFFFFFF;
        // a red stripe band, like the logo label
        return Math.sin(a * 3) > 0.2 ? 0xD8232A : 0xFFFFFF;
    }

    public static final int[] FAYGO_COLORS = {0xD32F2F, 0xFF8F00, 0x7B1FA2, 0x2E7D32, 0xF9A825, 0xC2185B};
}
