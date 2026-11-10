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

}
