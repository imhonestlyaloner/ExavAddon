package com.exav.addon.glint;

/**
 * Makes Better Glint's animated looks. Each style has two parts, both seamless tiles and seamless animation loops:
 * <ul>
 *     <li>the <b>cover</b> painted over the item / arm (opaque, 128 px), and</li>
 *     <li>the <b>aura</b> reaching out from the item's outline (64 px, see-through): u runs along the edge, v from the
 *     edge (0) out to the tip (1).</li>
 * </ul>
 * Pixels are ABGR (NativeImage order). Looping in time: noise is sampled while drifting round a small circle, and
 * anything that scrolls moves by whole tiles, so the last frame flows back into the first.
 */
public final class GlintTexture {
    private GlintTexture() {
    }

    public static final String[] STYLES = {"Galaxy", "Flame", "Swirl", "Aurora", "Sparkle"};
    public static final int SIZE = 128, AURA = 64;

    /** each style's own two colours (used unless you pick your own): {main, second} */
    public static int[] palette(String style) {
        return switch (style) {
            case "Flame" -> new int[]{0xFFC93D, 0xFF4A12};
            case "Swirl" -> new int[]{0xFF4FD8, 0x3DE0FF};
            case "Aurora" -> new int[]{0x3DFF9A, 0xA05CFF};
            case "Sparkle" -> new int[]{0xEAF6FF, 0x5FB8FF};
            default -> new int[]{0xB04DFF, 0x3D4DFF};
        };
    }

    // ---------------------------------------------------------------- frames

    public static int[][] cover(String style, int a, int b, boolean rainbow, float intensity, int frames) {
        int[][] out = new int[frames][];
        Stars stars = new Stars(SIZE, 70, 21);
        for (int f = 0; f < frames; f++) {
            float p = f / (float) frames;
            int ca = a, cb = b;
            if (rainbow) { // the colours travel round the rainbow once per loop
                ca = java.awt.Color.HSBtoRGB(p + 0.12F, 0.75F, 1.0F) & 0xFFFFFF;
                cb = java.awt.Color.HSBtoRGB(p, 0.85F, 1.0F) & 0xFFFFFF;
            }
            int[] px = new int[SIZE * SIZE];
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    float u = x / (float) SIZE, v = y / (float) SIZE;
                    int c = switch (style) {
                        case "Flame" -> flame(u, v, p, ca, cb, intensity);
                        case "Swirl" -> swirl(u, v, p, ca, cb, intensity);
                        case "Aurora" -> aurora(u, v, p, ca, cb, intensity);
                        case "Sparkle" -> sparkle(u, v, p, ca, cb, intensity);
                        default -> galaxy(u, v, p, ca, cb, intensity);
                    };
                    float star = stars.at(x, y, p);
                    if (star > 0 && !"Flame".equals(style) && !"Swirl".equals(style)) {
                        c = lerp(c, 0xFFFFFF, Math.min(1, star) * ("Aurora".equals(style) ? 0.5F : 0.95F));
                    }
                    px[y * SIZE + x] = abgr(c, 255);
                }
            }
            out[f] = px;
        }
        return out;
    }

    public static int[][] aura(String style, int a, int b, boolean rainbow, float intensity, int frames) {
        int[][] out = new int[frames][];
        for (int f = 0; f < frames; f++) {
            float p = f / (float) frames;
            int ca = a, cb = b;
            if (rainbow) {
                ca = java.awt.Color.HSBtoRGB(p + 0.12F, 0.75F, 1.0F) & 0xFFFFFF;
                cb = java.awt.Color.HSBtoRGB(p, 0.85F, 1.0F) & 0xFFFFFF;
            }
            int[] px = new int[AURA * AURA];
            for (int y = 0; y < AURA; y++) {
                for (int x = 0; x < AURA; x++) {
                    float u = x / (float) AURA, v = y / (float) AURA;
                    float[] rgba = switch (style) {
                        case "Flame" -> flameAura(u, v, p, ca, cb);
                        case "Swirl" -> swirlAura(u, v, p, ca, cb);
                        case "Aurora" -> auroraAura(u, v, p, ca, cb);
                        case "Sparkle" -> sparkleAura(u, v, p, ca, cb);
                        default -> galaxyAura(u, v, p, ca, cb);
                    };
                    float alpha = Math.max(0, Math.min(1, rgba[1] * intensity));
                    px[y * AURA + x] = abgr((int) rgba[0], Math.round(alpha * 255));
                }
            }
            out[f] = px;
        }
        return out;
    }

    // ---------------------------------------------------------------- covers

    /** deep blue with violet / pink nebula clouds that slowly churn (stars twinkle on top) */
    private static int galaxy(float u, float v, float p, int nebula, int base, float intensity) {
        float cx = cos(p) * 0.07F, sy = sin(p) * 0.07F;
        float wx = fbm(u + cx, v + sy, 4, 61), wy = fbm(u - sy, v + cx, 4, 67);
        float body = fbm(u + 0.45F * wx, v + 0.45F * wy, 5, 71);
        float wisps = fbm(u * 2 + 0.8F * wy + cx, v * 2 + 0.8F * wx - sy, 4, 79);
        int deep = scale(base, 0.38F), pink = lerp(nebula, 0xFFC4F4, 0.45F);
        int c = lerp(deep, base, smooth(0.25F, 0.7F, body));
        c = lerp(c, nebula, smooth(0.62F - 0.1F * intensity, 0.9F, body * 0.55F + wisps * 0.6F) * 0.9F);
        return lerp(c, pink, smooth(0.78F, 1.0F, wisps * 0.7F + body * 0.45F) * Math.min(1, intensity));
    }

    /** living fire: tall tongues licking upward out of dark embers, with sparks rising */
    private static int flame(float u, float v, float p, int hot, int fire, float intensity) {
        float warp = fbm(u, v + p, 3, 11);
        // stretched tall (twice the detail across as up), bent by the slower warp so it doesn't look repeated
        float f = fbm(u * 2 + 0.6F * warp, v + p + 0.3F * warp, 5, 13);
        float lick = fbm(u * 4 + 0.4F * warp, v * 2 + 2 * p, 3, 17);
        float heat = smooth(0.42F, 0.86F, f * 0.8F + lick * 0.35F) * (0.8F + 0.25F * intensity);
        int c = ramp(heat, new float[]{0, 0.35F, 0.6F, 0.85F, 1.0F},
                new int[]{scale(fire, 0.05F), scale(fire, 0.4F), fire, hot, lerp(hot, 0xFFFFFF, 0.65F)});
        float spark = 0; // sparks drifting up
        for (int i = 0; i < 24; i++) {
            float sx = hash(i, 31), sy = frac(hash(i, 32) - p * (1 + (i & 1)));
            float d = len(wrap(u - sx - 0.02F * sin(p * 2 + hash(i, 33))), wrap(v - sy));
            spark = Math.max(spark, Math.max(0, 1 - d / 0.012F));
        }
        return lerp(c, lerp(hot, 0xFFFFFF, 0.5F), spark);
    }

    /** hypnotic candy swirl: ribbons of the two colours twisting round each other, glossy and shaded, flowing along */
    private static int swirl(float u, float v, float p, int a, int b, float intensity) {
        float w = fbm(u + cos(p) * 0.08F, v + sin(p) * 0.08F, 4, 23);
        float w2 = fbm(u * 2 - sin(p) * 0.1F, v * 2 + cos(p) * 0.1F, 3, 27);
        // slopes and the per-loop flow are multiples of 3 (three ribbon colours), so it tiles and loops without a seam
        float t = 3 * u + 3 * v + 3.2F * w + 0.6F * w2 + 3 * p;
        float stripe = frac(t);
        int band = Math.floorMod((int) Math.floor(t), 3);
        int col = band == 0 ? a : band == 1 ? b : lerp(a, 0xFFFFFF, 0.55F);
        float shade = 0.55F + 0.45F * (float) Math.sin(Math.PI * stripe); // rounded, like a ribbon
        int c = scale(col, shade);
        float gloss = smooth(0.18F, 0.0F, Math.abs(stripe - 0.32F)) * 0.6F * intensity; // a shine running along each ribbon
        return lerp(c, 0xFFFFFF, gloss);
    }

    /** a night sky with northern lights: green curtains with purple tops rippling sideways */
    private static int aurora(float u, float v, float p, int green, int purple, float intensity) {
        int c = lerp(0x040A1C, 0x0B1638, fbm(u, v, 3, 41));
        for (int k = 0; k < 2; k++) {
            float line = 0.25F + 0.5F * k + 0.14F * (fbm(u + cos(p) * 0.12F, 0.3F * k + sin(p) * 0.12F, 3, 43 + k) - 0.5F) * 2;
            float d = wrap(v - line);
            float env = d < 0 ? (float) Math.exp(-(d / 0.05F) * (d / 0.05F)) : (float) Math.exp(-(d / 0.2F) * (d / 0.2F));
            float rays = fbm(u * 8 + cos(p) * 0.25F, v + k * 0.37F + sin(p) * 0.25F, 3, 47 + k);
            float glow = env * (0.35F + 0.9F * rays * rays) * (0.8F + 0.4F * intensity);
            int col = d < 0 ? lerp(green, purple, smooth(0, 0.06F, -d)) : lerp(green, lerp(green, 0x3DE0FF, 0.5F), smooth(0.05F, 0.25F, d));
            c = lerp(c, col, Math.min(1, glow));
        }
        return c;
    }

    /** cut diamond: many facets flashing white one after another as the light catches them, with rainbow fire */
    private static int sparkle(float u, float v, float p, int light, int ice, float intensity) {
        float d1 = 9, d2 = 9;
        int id = 0;
        for (int i = 0; i < 40; i++) {
            float d = len(wrap(u - hash(i, 51)), wrap(v - hash(i, 52)));
            if (d < d1) {
                d2 = d1;
                d1 = d;
                id = i;
            } else if (d < d2) {
                d2 = d;
            }
        }
        float shade = 0.15F + 0.5F * hash(id, 54);
        int c = lerp(scale(ice, 0.22F), ice, shade);
        int prism = java.awt.Color.HSBtoRGB(hash(id, 55), 0.35F, 1.0F) & 0xFFFFFF; // diamonds throw colour
        c = lerp(c, prism, 0.18F);
        float flash = (float) Math.pow(0.5 + 0.5 * Math.sin(2 * Math.PI * (p * (1 + (id % 3)) + hash(id, 53))), 6) * intensity;
        c = lerp(c, light, flash * 0.85F);
        float edge = smooth(0.01F, 0.0F, d2 - d1); // bright lines where facets meet
        return lerp(c, light, edge * 0.75F);
    }

    // ---------------------------------------------------------------- auras: {rgb, alpha}

    private static float[] flameAura(float u, float v, float p, int hot, int fire) {
        float warp = fbm(u, v - 2 * p, 3, 61);
        float f = fbm(u * 2 + 0.4F * warp, v - 4 * p, 4, 63);      // flames move outward
        float height = 0.35F + 0.75F * fbm(u * 4, 0.5F - p * 2, 2, 65); // tongues of different lengths
        float a = smooth(0, 0.25F, height - v) * (0.55F + 0.7F * f);
        a *= (float) Math.pow(Math.max(0, 1 - v), 0.6);
        int c = ramp(Math.min(1, v / Math.max(0.1F, height)), new float[]{0, 0.4F, 0.75F, 1},
                new int[]{lerp(hot, 0xFFFFFF, 0.15F), hot, fire, scale(fire, 0.55F)});
        return new float[]{c, a};
    }

    private static float[] galaxyAura(float u, float v, float p, int nebula, int base) {
        float w = fbm(u * 2 + cos(p) * 0.1F, v - p, 3, 71);
        float a = (float) Math.pow(Math.max(0, 1 - v), 1.3) * smooth(0.3F, 0.75F, w) * 1.3F;
        float star = 0;
        for (int i = 0; i < 10; i++) {
            float d = len(wrap(u - hash(i, 72)), wrap(v - frac(hash(i, 73) + p)) * 0.5F);
            star = Math.max(star, Math.max(0, 1 - d / 0.03F) * (1 - v));
        }
        return new float[]{lerp(lerp(base, nebula, w), 0xFFFFFF, star), Math.min(1, a + star)};
    }

    private static float[] swirlAura(float u, float v, float p, int a, int b) {
        float w = fbm(u * 2 + cos(p) * 0.1F, v + sin(p) * 0.1F, 3, 81);
        float s = 0.5F + 0.5F * (float) Math.sin(2 * Math.PI * (2 * u + 2 * v + 2 * w - p));
        float alpha = (float) Math.pow(s, 2) * (float) Math.pow(Math.max(0, 1 - v), 1.3) * 1.4F;
        return new float[]{lerp(b, a, s), alpha};
    }

    private static float[] auroraAura(float u, float v, float p, int green, int purple) {
        float rays = fbm(u * 8 + cos(p) * 0.3F, v * 0.5F + sin(p) * 0.3F, 3, 91);
        float alpha = (float) Math.pow(Math.max(0, 1 - v), 0.9) * (0.15F + rays * rays * 1.2F);
        return new float[]{lerp(green, purple, smooth(0.2F, 0.9F, v)), Math.min(1, alpha)};
    }

    private static float[] sparkleAura(float u, float v, float p, int light, int ice) {
        float a = (float) Math.pow(Math.max(0, 1 - v), 2.5) * 0.6F; // shine at the edge
        float star = 0;
        for (int i = 0; i < 14; i++) {
            float sx = hash(i, 101), sy = 0.1F + 0.75F * hash(i, 102);
            float blink = (float) Math.pow(Math.max(0, Math.sin(2 * Math.PI * (p + hash(i, 103)))), 6);
            float dx = Math.abs(wrap(u - sx)), dy = Math.abs(v - sy);
            float cross = Math.max(Math.max(0, 1 - dx / 0.012F) * Math.max(0, 1 - dy / 0.16F),
                    Math.max(0, 1 - dy / 0.025F) * Math.max(0, 1 - dx / 0.07F));
            cross = Math.max(cross, Math.max(0, 1 - len(dx, dy * 0.5F) / 0.025F)); // bright centre
            star = Math.max(star, cross * blink);
        }
        return new float[]{lerp(ice, light, Math.min(1, star + 0.3F)), Math.min(1, a + star)};
    }

    // ---------------------------------------------------------------- twinkling stars (for the covers)

    private static final class Stars {
        private final int[] id;
        private final float[] glow;

        Stars(int size, int count, int seed) {
            id = new int[size * size];
            glow = new float[size * size];
            java.util.Arrays.fill(id, -1);
            for (int i = 0; i < count; i++) {
                float sx = hash(i, seed), sy = hash(i, seed + 1), b = 0.35F + 0.65F * hash(i, seed + 2), r = hash(i, seed + 3) < 0.15F ? 2.6F : 1.1F;
                for (int dy = -4; dy <= 4; dy++) {
                    for (int dx = -4; dx <= 4; dx++) {
                        float a = b * Math.max(0, 1 - (float) Math.sqrt(dx * dx + dy * dy) / r);
                        int px = Math.floorMod((int) (sx * size) + dx, size), py = Math.floorMod((int) (sy * size) + dy, size);
                        if (a > glow[py * size + px]) {
                            glow[py * size + px] = a;
                            id[py * size + px] = i;
                        }
                    }
                }
            }
        }

        float at(int x, int y, float p) {
            int i = id[y * SIZE + x];
            if (i < 0) return 0;
            float tw = 0.55F + 0.45F * (float) Math.sin(2 * Math.PI * (p * (1 + (i % 2)) + hash(i, 9)));
            return glow[y * SIZE + x] * tw;
        }
    }

    // ---------------------------------------------------------------- helpers

    private static int abgr(int rgb, int alpha) {
        return alpha << 24 | (rgb & 255) << 16 | (rgb & 0xFF00) | (rgb >> 16) & 255;
    }

    private static float cos(float p) {
        return (float) Math.cos(2 * Math.PI * p);
    }

    private static float sin(float p) {
        return (float) Math.sin(2 * Math.PI * p);
    }

    private static float len(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    private static int ramp(float t, float[] at, int[] colors) {
        t = Math.max(0, Math.min(1, t));
        for (int i = 1; i < at.length; i++) {
            if (t <= at[i]) return lerp(colors[i - 1], colors[i], (t - at[i - 1]) / (at[i] - at[i - 1]));
        }
        return colors[colors.length - 1];
    }

    private static int scale(int rgb, float k) {
        return (int) (((rgb >> 16) & 255) * k) << 16 | (int) (((rgb >> 8) & 255) * k) << 8 | (int) ((rgb & 255) * k);
    }

    private static float wrap(float d) {
        return d - Math.round(d);
    }

    private static float smooth(float a, float b, float x) {
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    private static float hash(int i, int k) {
        long h = i * 0x9E3779B97F4A7C15L + k * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }

    /** tiling value noise: period = cells along each side, so the texture wraps seamlessly */
    private static float noise(float x, float y, int period, int seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float a = cell(x0, y0, period, seed), b = cell(x0 + 1, y0, period, seed), c = cell(x0, y0 + 1, period, seed), d = cell(x0 + 1, y0 + 1, period, seed);
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
    }

    private static float cell(int x, int y, int period, int seed) {
        return hash(Math.floorMod(x, period) * 7919 + Math.floorMod(y, period) * 104729 + seed * 31337, seed);
    }

    /** fractal noise over the unit square that tiles (shifting u or v by a whole number gives the same value) */
    private static float fbm(float u, float v, int octaves, int seed) {
        float sum = 0, amp = 0.5F, norm = 0;
        int base = 4;
        for (int o = 0; o < octaves; o++) {
            int period = base << o;
            sum += amp * noise(u * period, v * period, period, seed + o);
            norm += amp;
            amp *= 0.5F;
        }
        return sum / norm;
    }
}
