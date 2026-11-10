package com.exav.addon.sky;

import com.exav.addon.holiday.Shapes;
import com.exav.addon.holiday.SmoothMesh;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;

/**
 * Models for the Christmas sky. Units are roughly half a block; everything faces +X (the way it flies),
 * with +Y up and +Z to the side.
 */
public final class ChristmasArt {
    private ChristmasArt() {
    }

    static final int RED = 0xC8102E, DARK_RED = 0x8E0B20, GOLD = 0xE8B83A, WHITE = 0xF7F7F7, SKIN = 0xF2C9A0,
            BLACK = 0x1E1E1E, BROWN = 0x7A4A24, LIGHT_BROWN = 0xB98552, ANTLER = 0xD9C29A, SACK = 0x8C6239;

    private static List<Vector3f> line(float... xyz) {
        List<Vector3f> p = new ArrayList<>();
        for (int i = 0; i < xyz.length; i += 3) p.add(new Vector3f(xyz[i], xyz[i + 1], xyz[i + 2]));
        return p;
    }

    /** a smooth path through the given points (Catmull-Rom), for curvy tubes */
    private static List<Vector3f> curve(float... xyz) {
        List<Vector3f> pts = line(xyz), out = new ArrayList<>();
        for (int i = 0; i < pts.size() - 1; i++) {
            Vector3f p0 = pts.get(Math.max(0, i - 1)), p1 = pts.get(i), p2 = pts.get(i + 1), p3 = pts.get(Math.min(pts.size() - 1, i + 2));
            for (int k = 0; k < 8; k++) {
                float t = k / 8.0F, t2 = t * t, t3 = t2 * t;
                out.add(new Vector3f(
                        0.5F * (2 * p1.x + (-p0.x + p2.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3),
                        0.5F * (2 * p1.y + (-p0.y + p2.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3),
                        0.5F * (2 * p1.z + (-p0.z + p2.z) * t + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2 + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3)));
            }
        }
        out.add(pts.get(pts.size() - 1));
        return out;
    }

    private static float box(float x, float y, float x0, float y0, float x1, float y1, float r) {
        float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, hx = (x1 - x0) / 2 - r, hy = (y1 - y0) / 2 - r;
        float dx = Math.abs(x - cx) - hx, dy = Math.abs(y - cy) - hy;
        return (float) Math.hypot(Math.max(dx, 0), Math.max(dy, 0)) + Math.min(Math.max(dx, dy), 0) - r;
    }

    /** Santa's sleigh with Santa and his sack in it. Sleigh floor at y = 0.4, front at +X. */
    public static SmoothMesh sleigh() {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        // sleigh body: side profile (seat box, tall back, curled front scroll), puffed out to its width
        Shapes.Field side = (x, y) -> {
            float d = box(x, y, -3.2F, 0.4F, 2.4F, 2.3F, 0.4F);
            d = Math.min(d, box(x, y, -3.2F, 0.4F, -1.9F, 3.6F, 0.5F));
            float ring = Math.abs((float) Math.hypot(x - 2.75F, y - 1.85F) - 0.75F) - 0.32F;
            return Shapes.smin(d, ring, 0.3F);
        };
        Shapes.puffy(b, side, -3.8F, 0.0F, 3.9F, 4.0F, 0.12F, 1.7F, 0.9F,
                (x, y, depth) -> depth < 0.22F ? GOLD : RED, (x, y, depth) -> depth < 0.22F ? GOLD : DARK_RED, null);
        // golden runners curling up at the front
        for (int s = -1; s <= 1; s += 2) {
            float z = s * 1.25F;
            Shapes.tube(b, curve(-3.6F, 0.15F, z, -1.0F, -0.05F, z, 2.2F, -0.05F, z, 3.4F, 0.5F, z, 3.5F, 1.3F, z, 3.0F, 1.5F, z), t -> 0.17F, (t, a) -> GOLD, 8);
            Shapes.tube(b, line(-2.2F, 0.0F, z, -2.2F, 0.5F, z), t -> 0.12F, (t, a) -> GOLD, 6);
            Shapes.tube(b, line(1.4F, -0.05F, z, 1.4F, 0.5F, z), t -> 0.12F, (t, a) -> GOLD, 6);
        }
        // Santa
        Shapes.sphere(b, -0.6F, 2.9F, 0, 1.25F, RED, 10);                                   // body
        Shapes.tube(b, ringPath(-0.6F, 2.6F, 1.23F), t -> 0.16F, (t, a) -> BLACK, 6);        // belt
        Shapes.sphere(b, 0.6F, 2.6F, 0, 0.18F, GOLD, 5);                                     // buckle
        Shapes.sphere(b, -0.5F, 4.45F, 0, 0.62F, SKIN, 10);                                  // head
        Shapes.sphere(b, -0.1F, 4.1F, 0, 0.52F, WHITE, 8);                                   // beard
        Shapes.sphere(b, 0.05F, 4.45F, 0, 0.13F, 0xE89A88, 5);                               // nose
        Shapes.sphere(b, 0.0F, 4.65F, 0.22F, 0.07F, BLACK, 4);                               // eyes
        Shapes.sphere(b, 0.0F, 4.65F, -0.22F, 0.07F, BLACK, 4);
        Shapes.tube(b, ringPath(-0.5F, 4.85F, 0.6F), t -> 0.16F, (t, a) -> WHITE, 6);        // hat trim
        Shapes.tube(b, curve(-0.5F, 4.85F, 0, -0.55F, 5.5F, 0, -0.85F, 6.0F, 0, -1.3F, 5.9F, 0),
                t -> 0.58F * (1.0F - Math.min(1.0F, t / 1.75F)) + 0.04F, (t, a) -> RED, 10);       // hat, flopping back
        Shapes.sphere(b, -1.35F, 5.85F, 0, 0.22F, WHITE, 6);                                 // pom-pom
        for (int s = -1; s <= 1; s += 2) {                                                   // arms holding the reins
            float z = s * 1.0F;
            Shapes.tube(b, curve(-0.6F, 3.5F, z, 0.0F, 3.3F, z * 1.05F, 0.6F, 3.0F, z * 0.8F), t -> 0.3F, (t, a) -> RED, 8);
            Shapes.tube(b, ringPath3(0.55F, 3.02F, z * 0.82F, 0.3F), t -> 0.08F, (t, a) -> WHITE, 5);
            Shapes.sphere(b, 0.75F, 2.95F, z * 0.78F, 0.24F, BLACK, 6);
        }
        // sack of presents behind him, presents peeking out
        Shapes.sphere(b, -2.45F, 3.0F, 0, 1.0F, SACK, 10);
        Shapes.sphere(b, -2.45F, 3.85F, 0, 0.55F, SACK, 8);
        Shapes.tube(b, ringPath(-2.45F, 3.75F, 0.5F), t -> 0.08F, (t, a) -> GOLD, 5);
        Shapes.sphere(b, -2.6F, 4.35F, 0.25F, 0.28F, 0x2E7D32, 5);
        Shapes.sphere(b, -2.25F, 4.3F, -0.3F, 0.25F, 0x1565C0, 5);
        return b.build();
    }

    /** horizontal ring (round the Y axis) */
    private static List<Vector3f> ringPath(float cx, float cy, float r) {
        List<Vector3f> p = new ArrayList<>();
        for (int i = 0; i <= 24; i++) {
            double a = Math.PI * 2 * i / 24;
            p.add(new Vector3f(cx + r * (float) Math.cos(a), cy, r * (float) Math.sin(a)));
        }
        return p;
    }

    /** ring round the X axis (a cuff) */
    private static List<Vector3f> ringPath3(float cx, float cy, float cz, float r) {
        List<Vector3f> p = new ArrayList<>();
        for (int i = 0; i <= 16; i++) {
            double a = Math.PI * 2 * i / 16;
            p.add(new Vector3f(cx, cy + r * (float) Math.cos(a), cz + r * (float) Math.sin(a)));
        }
        return p;
    }

    /** a reindeer body (no legs), facing +X; legs attach at y = -0.25 */
    public static SmoothMesh reindeer() {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        Shapes.tube(b, curve(-1.15F, 0.05F, 0, 0, 0, 0, 1.0F, 0.1F, 0), t -> 0.42F + 0.16F * (float) Math.sin(Math.PI * Math.min(1, t / 2.15F)),
                (t, a) -> Math.sin(a) < -0.55 ? LIGHT_BROWN : BROWN, 12);                        // body, lighter belly
        Shapes.tube(b, curve(0.75F, 0.25F, 0, 1.1F, 0.75F, 0, 1.35F, 1.05F, 0), t -> 0.27F, (t, a) -> BROWN, 10); // neck
        Shapes.sphere(b, 1.48F, 1.15F, 0, 0.36F, BROWN, 10);                                 // head
        Shapes.tube(b, line(1.5F, 1.08F, 0, 1.95F, 0.98F, 0), t -> 0.2F, (t, a) -> LIGHT_BROWN, 8); // snout
        Shapes.sphere(b, 2.0F, 0.98F, 0, 0.12F, BLACK, 5);                                   // nose
        Shapes.sphere(b, 1.65F, 1.3F, 0.22F, 0.06F, BLACK, 4);                               // eyes
        Shapes.sphere(b, 1.65F, 1.3F, -0.22F, 0.06F, BLACK, 4);
        for (int s = -1; s <= 1; s += 2) {
            float z = s * 0.18F;
            Shapes.tube(b, curve(1.38F, 1.42F, z, 1.25F, 1.9F, z * 2.2F, 1.0F, 2.35F, z * 3.0F, 0.75F, 2.55F, z * 3.4F), t -> 0.07F, (t, a) -> ANTLER, 5);
            Shapes.tube(b, line(1.22F, 1.95F, z * 2.4F, 1.5F, 2.3F, z * 2.6F), t -> 0.055F, (t, a) -> ANTLER, 5);
            Shapes.tube(b, line(1.0F, 2.32F, z * 3.0F, 1.2F, 2.7F, z * 3.1F), t -> 0.05F, (t, a) -> ANTLER, 5);
            Shapes.sphere(b, 1.32F, 1.4F, s * 0.36F, 0.1F, BROWN, 4);                       // ears
        }
        Shapes.sphere(b, -1.25F, 0.25F, 0, 0.16F, WHITE, 5);                                 // tail
        Shapes.tube(b, ringPath3(0.6F, 0.05F, 0, 0.6F), t -> 0.07F, (t, a) -> DARK_RED, 6);  // harness strap
        Shapes.sphere(b, 0.6F, 0.65F, 0, 0.1F, GOLD, 4);                                     // little bell
        return b.build();
    }

    /** one leg, hanging from (0,0,0) */
    public static SmoothMesh leg() {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        Shapes.tube(b, line(0, 0, 0, 0.05F, -0.65F, 0, 0, -1.25F, 0), t -> 0.12F, (t, a) -> BROWN, 6);
        Shapes.sphere(b, 0, -1.3F, 0, 0.12F, 0x3A2A1A, 4);
        return b.build();
    }

    /** a round glowing ball (Rudolph's nose, glows), centred */
    public static SmoothMesh ball(int rgb) {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        Shapes.sphere(b, 0, 0, 0, 1.0F, rgb, 8);
        return b.build();
    }

    /** a wrapped present, unit cube centred at the origin, with a ribbon cross and a bow */
    public static SmoothMesh present(int paper, int ribbon) {
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        float h = 0.5F;
        box(b, -h, -h, -h, h, h, h, paper);
        float r = 0.09F, e = 0.012F;
        box(b, -r, -h - e, -h - e, r, h + e, h + e, ribbon);   // ribbon round one way
        box(b, -h - e, -h - e, -r, h + e, h + e, r, ribbon);   // and the other
        Shapes.sphere(b, 0.17F, h + 0.13F, 0, 0.16F, ribbon, 5);  // bow
        Shapes.sphere(b, -0.17F, h + 0.13F, 0, 0.16F, ribbon, 5);
        return b.build();
    }

    private static void box(SmoothMesh.Builder b, float x0, float y0, float z0, float x1, float y1, float z1, int c) {
        quad(b, c, 1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
        quad(b, c, -1, 0, 0, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0);
        quad(b, c, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(b, c, 0, -1, 0, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
        quad(b, c, 0, 0, 1, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1);
        quad(b, c, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
    }

    private static void quad(SmoothMesh.Builder b, int c, float nx, float ny, float nz, float... p) {
        b.quad(new SmoothMesh.V(p[0], p[1], p[2], nx, ny, nz, c), new SmoothMesh.V(p[3], p[4], p[5], nx, ny, nz, c),
                new SmoothMesh.V(p[6], p[7], p[8], nx, ny, nz, c), new SmoothMesh.V(p[9], p[10], p[11], nx, ny, nz, c));
    }
}
