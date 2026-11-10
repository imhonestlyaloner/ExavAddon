package com.exav.addon.sky;

import com.exav.addon.holiday.Shapes;
import com.exav.addon.holiday.SmoothMesh;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;

/**
 * The Yeat bells: big, detailed church bells. Built in white (the material colour is applied when drawn), with the
 * hanging point (top of the crown) at (0,0,0) and the mouth at y = -10. The clapper is separate so it can swing.
 */
public final class SkyArt {
    private SkyArt() {
    }

    /** marks the inside of the bell (drawn darker) and the raised bands via the vertex colour */
    public static final int OUTSIDE = 0xFFFFFF, INSIDE = 0x7F7F7F, BAND = 0xE6E6E6;

    public static SmoothMesh bell() {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        // classic bell profile: crown, rounded shoulder with raised bands, waist, flaring sound bow and a beaded lip,
        // then back up the inside
        float[][] outside = {
                {0, -1.6F}, {1.0F, -1.6F}, {1.9F, -1.75F}, {2.4F, -2.0F},          // top
                {2.75F, -2.3F}, {2.85F, -2.45F}, {2.75F, -2.6F},                 // shoulder band
                {2.95F, -3.0F}, {3.1F, -3.6F}, {3.2F, -4.3F}, {3.3F, -5.0F},       // waist
                {3.45F, -5.6F}, {3.6F, -5.75F}, {3.5F, -5.9F},                   // waist band
                {3.7F, -6.4F}, {4.0F, -7.1F}, {4.45F, -7.8F}, {4.95F, -8.45F},     // flare
                {5.25F, -8.75F}, {5.45F, -8.9F}, {5.35F, -9.05F},                // band above the lip
                {5.55F, -9.35F}, {5.75F, -9.65F}, {5.82F, -9.85F}, {5.7F, -10.0F}, // beaded lip
                {5.35F, -10.02F}, {5.0F, -9.9F},
        };
        float[][] inside = {
                {4.75F, -9.6F}, {4.3F, -8.8F}, {3.75F, -7.7F}, {3.3F, -6.3F}, {3.0F, -4.6F}, {2.7F, -3.2F},
                {2.2F, -2.5F}, {1.2F, -2.25F}, {0, -2.2F},
        };
        float[][] profile = new float[outside.length + inside.length][];
        System.arraycopy(outside, 0, profile, 0, outside.length);
        System.arraycopy(inside, 0, profile, outside.length, inside.length);
        int insideFrom = outside.length - 1;
        Shapes.lathe(b, profile, 48, true, (y, a, i) -> {
            if (i > insideFrom) return INSIDE;
            if ((y < -2.25F && y > -2.65F) || (y < -5.55F && y > -5.95F) || (y < -8.65F && y > -9.1F)) return BAND;
            return OUTSIDE;
        });
        // crown: six loops (canons) on top, like a real hanging bell
        for (int k = 0; k < 6; k++) {
            double a = Math.PI * k / 6;
            List<Vector3f> loop = new ArrayList<>();
            for (int i = 0; i <= 16; i++) {
                double t = Math.PI * i / 16;
                float r = 0.9F * (float) Math.cos(t);
                loop.add(new Vector3f((float) Math.cos(a) * r, -1.6F + 1.1F * (float) Math.sin(t), (float) Math.sin(a) * r));
            }
            Shapes.tube(b, loop, s -> 0.22F, (s, ang) -> OUTSIDE, 6);
        }
        Shapes.sphere(b, 0, -0.35F, 0, 0.45F, OUTSIDE, 5);
        return b.build();
    }

    /** the clapper: hangs from (0,0,0) inside the bell, rod down to a heavy ball */
    public static SmoothMesh clapper() {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        List<Vector3f> rod = List.of(new Vector3f(0, 0, 0), new Vector3f(0, -6.2F, 0));
        Shapes.tube(b, rod, s -> 0.22F, (s, a) -> INSIDE, 8);
        Shapes.lathe(b, new float[][]{{0, -6.1F}, {0.55F, -6.3F}, {0.95F, -6.9F}, {1.05F, -7.5F}, {0.9F, -8.1F}, {0.5F, -8.5F}, {0, -8.6F}},
                12, true, (y, a, i) -> INSIDE);
        return b.build();
    }
}
