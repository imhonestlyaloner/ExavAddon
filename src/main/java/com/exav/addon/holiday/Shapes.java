package com.exav.addon.holiday;

import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;

/** Builders for smooth shapes: tubes along a path, lathe (turned) shapes, spheres and puffy cookie shapes. */
public final class Shapes {
    private Shapes() {
    }

    // ------------------------------------------------------------------ tube

    /** Colour of a tube's surface at distance s along the path and angle (0..2pi) around it. */
    @FunctionalInterface
    public interface TubeColor {
        int rgb(float s, float angle);
    }

    @FunctionalInterface
    public interface Radius {
        float r(float s);
    }

    /**
     * A round tube along a path (points in order). The radius can change along it; let it reach 0 at an end
     * to close that end (rounded or pointed), otherwise the end gets a flat cap.
     */
    public static void tube(SmoothMesh.Builder b, List<Vector3f> path, Radius radius, TubeColor color, int sides) {
        int n = path.size();
        float[] s = new float[n];
        for (int i = 1; i < n; i++) s[i] = s[i - 1] + path.get(i).distance(path.get(i - 1));
        // tangents and a frame carried along the path (parallel transport), so the tube doesn't twist
        Vector3f[] t = new Vector3f[n], nrm = new Vector3f[n], bin = new Vector3f[n];
        for (int i = 0; i < n; i++) {
            Vector3f a = path.get(Math.max(0, i - 1)), c = path.get(Math.min(n - 1, i + 1));
            t[i] = new Vector3f(c).sub(a).normalize();
        }
        Vector3f helper = Math.abs(t[0].z) < 0.9F ? new Vector3f(0, 0, 1) : new Vector3f(1, 0, 0);
        nrm[0] = new Vector3f(helper).cross(t[0]).normalize();
        bin[0] = new Vector3f(t[0]).cross(nrm[0]).normalize();
        for (int i = 1; i < n; i++) {
            Vector3f prev = nrm[i - 1];
            nrm[i] = new Vector3f(prev).fma(-prev.dot(t[i]), t[i]).normalize();
            bin[i] = new Vector3f(t[i]).cross(nrm[i]).normalize();
        }
        Vector3f[][] ring = new Vector3f[n][sides + 1];
        Vector3f[][] ringN = new Vector3f[n][sides + 1];
        float[] r = new float[n];
        for (int i = 0; i < n; i++) {
            r[i] = Math.max(0, radius.r(s[i]));
            // tilt normals where the radius changes (cones, rounded ends)
            float dr = (radius.r(Math.min(s[n - 1], s[i] + 0.05F)) - radius.r(Math.max(0, s[i] - 0.05F))) / 0.1F;
            for (int k = 0; k <= sides; k++) {
                double a = Math.PI * 2 * k / sides;
                Vector3f dir = new Vector3f(nrm[i]).mul((float) Math.cos(a)).fma((float) Math.sin(a), bin[i]);
                ring[i][k] = new Vector3f(path.get(i)).fma(r[i], dir);
                ringN[i][k] = new Vector3f(dir).fma(-dr, t[i]).normalize();
            }
        }
        for (int i = 0; i < n - 1; i++) {
            for (int k = 0; k < sides; k++) {
                float a0 = (float) (Math.PI * 2 * k / sides), a1 = (float) (Math.PI * 2 * (k + 1) / sides);
                b.quad(SmoothMesh.V.of(ring[i][k], ringN[i][k], color.rgb(s[i], a0)),
                        SmoothMesh.V.of(ring[i + 1][k], ringN[i + 1][k], color.rgb(s[i + 1], a0)),
                        SmoothMesh.V.of(ring[i + 1][k + 1], ringN[i + 1][k + 1], color.rgb(s[i + 1], a1)),
                        SmoothMesh.V.of(ring[i][k + 1], ringN[i][k + 1], color.rgb(s[i], a1)));
            }
        }
        // flat caps where the ends are still open
        if (r[0] > 0.01F) cap(b, path.get(0), new Vector3f(t[0]).negate(), ring[0], sides, color.rgb(0, 0));
        if (r[n - 1] > 0.01F) cap(b, path.get(n - 1), t[n - 1], ring[n - 1], sides, color.rgb(s[n - 1], 0));
    }

    private static void cap(SmoothMesh.Builder b, Vector3f centre, Vector3f normal, Vector3f[] ring, int sides, int rgb) {
        SmoothMesh.V c = SmoothMesh.V.of(centre, normal, rgb);
        for (int k = 0; k < sides; k++) {
            b.tri(c, SmoothMesh.V.of(ring[k], normal, rgb), SmoothMesh.V.of(ring[k + 1], normal, rgb));
        }
    }

    // ------------------------------------------------------------------ lathe

    @FunctionalInterface
    public interface LatheColor {
        int rgb(float y, float angle, int profileIndex);
    }

    /**
     * Turns a profile (pairs of radius, height, in order) around the Y axis. Normals point away from the axis
     * when outward is true, toward it otherwise (inside walls).
     */
    public static void lathe(SmoothMesh.Builder b, float[][] profile, int sides, boolean outward, LatheColor color) {
        int n = profile.length;
        // profile normals from neighbouring segments (perpendicular to the profile line in the r/y plane)
        float[][] pn = new float[n][2];
        for (int i = 0; i < n; i++) {
            float[] a = profile[Math.max(0, i - 1)], c = profile[Math.min(n - 1, i + 1)];
            float dr = c[0] - a[0], dy = c[1] - a[1];
            float len = (float) Math.sqrt(dr * dr + dy * dy);
            pn[i][0] = dy / len;
            pn[i][1] = -dr / len;
            if (!outward) {
                pn[i][0] = -pn[i][0];
                pn[i][1] = -pn[i][1];
            }
        }
        for (int i = 0; i < n - 1; i++) {
            for (int k = 0; k < sides; k++) {
                float a0 = (float) (Math.PI * 2 * k / sides), a1 = (float) (Math.PI * 2 * (k + 1) / sides);
                b.quad(lv(profile[i], pn[i], a0, color.rgb(profile[i][1], a0, i)),
                        lv(profile[i], pn[i], a1, color.rgb(profile[i][1], a1, i)),
                        lv(profile[i + 1], pn[i + 1], a1, color.rgb(profile[i + 1][1], a1, i + 1)),
                        lv(profile[i + 1], pn[i + 1], a0, color.rgb(profile[i + 1][1], a0, i + 1)));
            }
        }
    }

    private static SmoothMesh.V lv(float[] p, float[] n, float a, int rgb) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new SmoothMesh.V(p[0] * c, p[1], p[0] * s, n[0] * c, n[1], n[0] * s, rgb);
    }

    /** A flat disc at height y facing up (or down), coloured per point. */
    public static void disc(SmoothMesh.Builder b, float y, float radius, boolean up, int sides, int rings, DiscColor color) {
        float ny = up ? 1 : -1;
        for (int ri = 0; ri < rings; ri++) {
            float r0 = radius * ri / rings, r1 = radius * (ri + 1) / rings;
            for (int k = 0; k < sides; k++) {
                float a0 = (float) (Math.PI * 2 * k / sides), a1 = (float) (Math.PI * 2 * (k + 1) / sides);
                b.quad(dv(r0, a0, y, ny, color), dv(r1, a0, y, ny, color), dv(r1, a1, y, ny, color), dv(r0, a1, y, ny, color));
            }
        }
    }

    @FunctionalInterface
    public interface DiscColor {
        int rgb(float x, float z);
    }

    private static SmoothMesh.V dv(float r, float a, float y, float ny, DiscColor color) {
        float x = r * (float) Math.cos(a), z = r * (float) Math.sin(a);
        return new SmoothMesh.V(x, y, z, 0, ny, 0, color.rgb(x, z));
    }

    // ------------------------------------------------------------------ sphere

    public static void sphere(SmoothMesh.Builder b, float cx, float cy, float cz, float r, int rgb, int seg) {
        for (int i = 0; i < seg; i++) {
            double p0 = Math.PI * i / seg - Math.PI / 2, p1 = Math.PI * (i + 1) / seg - Math.PI / 2;
            for (int k = 0; k < seg * 2; k++) {
                double a0 = Math.PI * k / seg, a1 = Math.PI * (k + 1) / seg;
                b.quad(sv(cx, cy, cz, r, p0, a0, rgb), sv(cx, cy, cz, r, p1, a0, rgb), sv(cx, cy, cz, r, p1, a1, rgb), sv(cx, cy, cz, r, p0, a1, rgb));
            }
        }
    }

    private static SmoothMesh.V sv(float cx, float cy, float cz, float r, double p, double a, int rgb) {
        float x = (float) (Math.cos(p) * Math.cos(a)), y = (float) Math.sin(p), z = (float) (Math.cos(p) * Math.sin(a));
        return new SmoothMesh.V(cx + r * x, cy + r * y, cz + r * z, x, y, z, rgb);
    }

    // ------------------------------------------------------------------ puffy cookie (2D outline, rounded both sides)

    /** Signed distance to a 2D shape: negative inside. */
    @FunctionalInterface
    public interface Field {
        float d(float x, float y);
    }

    /** Front-face colour at a point (back face uses the cookie colour from {@code back}). */
    @FunctionalInterface
    public interface Paint {
        int rgb(float x, float y, float depth);
    }

    /** Extra height on the front face (icing sits a bit proud). */
    @FunctionalInterface
    public interface Bump {
        float h(float x, float y);
    }

    /**
     * A flat shape with puffy, rounded edges, like a cookie: the outline comes from the field, the thickness rises
     * smoothly from 0 at the edge to {@code thick} once {@code edge} units inside. Both sides are built.
     */
    public static void puffy(SmoothMesh.Builder b, Field field, float x0, float y0, float x1, float y1, float step,
                             float thick, float edge, Paint front, Paint back, Bump bump) {
        int nx = (int) Math.ceil((x1 - x0) / step), ny = (int) Math.ceil((y1 - y0) / step);
        float[][] v = new float[nx + 1][ny + 1];
        for (int i = 0; i <= nx; i++) {
            for (int j = 0; j <= ny; j++) v[i][j] = field.d(x0 + i * step, y0 + j * step);
        }
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
                float[][] corners = {{x0 + i * step, y0 + j * step}, {x0 + (i + 1) * step, y0 + j * step},
                        {x0 + (i + 1) * step, y0 + (j + 1) * step}, {x0 + i * step, y0 + (j + 1) * step}};
                float[] val = {v[i][j], v[i + 1][j], v[i + 1][j + 1], v[i][j + 1]};
                List<float[]> poly = new ArrayList<>(6);
                for (int c = 0; c < 4; c++) {
                    int nc = (c + 1) % 4;
                    if (val[c] < 0) poly.add(corners[c]);
                    if ((val[c] < 0) != (val[nc] < 0)) {
                        float tt = val[c] / (val[c] - val[nc]);
                        poly.add(new float[]{corners[c][0] + (corners[nc][0] - corners[c][0]) * tt,
                                corners[c][1] + (corners[nc][1] - corners[c][1]) * tt});
                    }
                }
                if (poly.size() < 3) continue;
                for (int k = 1; k + 1 < poly.size(); k++) {
                    float[] a = poly.get(0), p = poly.get(k), q = poly.get(k + 1);
                    b.tri(pv(a, field, thick, edge, front, bump, 1), pv(p, field, thick, edge, front, bump, 1), pv(q, field, thick, edge, front, bump, 1));
                    b.tri(pv(a, field, thick, edge, back, null, -1), pv(q, field, thick, edge, back, null, -1), pv(p, field, thick, edge, back, null, -1));
                }
            }
        }
    }

    private static float height(float x, float y, Field field, float thick, float edge, Bump bump) {
        float inside = Math.max(0, -field.d(x, y));
        float h = thick * (float) Math.sqrt(Math.min(1.0F, inside / edge));
        return bump == null ? h : h + bump.h(x, y) * Math.min(1.0F, inside / 0.6F);
    }

    private static SmoothMesh.V pv(float[] p, Field field, float thick, float edge, Paint paint, Bump bump, int side) {
        float x = p[0], y = p[1], e = 0.12F;
        float h = height(x, y, field, thick, edge, bump);
        float dhx = (height(x + e, y, field, thick, edge, bump) - height(x - e, y, field, thick, edge, bump)) / (2 * e);
        float dhy = (height(x, y + e, field, thick, edge, bump) - height(x, y - e, field, thick, edge, bump)) / (2 * e);
        // at the very edge the slope is near infinite: lean the normal outward instead
        if (h < 0.05F) {
            float gx = field.d(x + e, y) - field.d(x - e, y), gy = field.d(x, y + e) - field.d(x, y - e);
            float gl = (float) Math.sqrt(gx * gx + gy * gy);
            if (gl > 1e-5F) {
                dhx = -gx / gl * 4;
                dhy = -gy / gl * 4;
            }
        }
        float depth = Math.max(0, -field.d(x, y));
        return new SmoothMesh.V(x, y, side * h, -dhx * side, -dhy * side, side, paint.rgb(x, y, depth));
    }

    // ------------------------------------------------------------------ 2D distance helpers

    public static float circle(float x, float y, float cx, float cy, float r) {
        return (float) Math.hypot(x - cx, y - cy) - r;
    }

    public static float capsule(float x, float y, float ax, float ay, float bx, float by, float r) {
        float vx = bx - ax, vy = by - ay;
        float t = Math.max(0, Math.min(1, ((x - ax) * vx + (y - ay) * vy) / (vx * vx + vy * vy)));
        return (float) Math.hypot(x - (ax + t * vx), y - (ay + t * vy)) - r;
    }

    /** smooth union (blends shapes together with a rounded seam) */
    public static float smin(float a, float b, float k) {
        float h = Math.max(0, Math.min(1, 0.5F + 0.5F * (b - a) / k));
        return b + (a - b) * h - k * h * (1 - h);
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }
}
