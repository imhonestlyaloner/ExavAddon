package com.exav.addon.glint;

/**
 * Makes the Better Glint textures: seamless (tiling) patterns, bright where the glint shows and black where it
 * doesn't (the glint is added on top of the item, so black = no change).
 */
public final class GlintTexture {
    private GlintTexture() {
    }

    public static final String[] STYLES = {"Galaxy", "Flame", "Swirl", "Aurora", "Sparkle"};

    /** Galaxy is painted over the item (covers it); the others are a glint added on top */
    public static boolean covers(String style) {
        return "Galaxy".equals(style);
    }
    public static final int SIZE = 128;

    /** ABGR pixels (NativeImage order) */
    public static int[] make(String style, int colorA, int colorB, boolean rainbow, float hueShift, float intensity) {
        if (covers(style)) return galaxy(colorA, colorB, rainbow, hueShift, intensity);
        int[] out = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float u = x / (float) SIZE, v = y / (float) SIZE;
                float strength, mix;
                switch (style) {
                    case "Swirl" -> {
                        float w = fbm(u * 2, v * 2, 4, 3);
                        float f = (float) Math.sin((fbm(u * 2 + w * 1.6F, v * 2 + w * 1.2F, 4, 4) * 7.0 + w * 4.0) * Math.PI);
                        strength = smooth(0.15F, 0.95F, f * 0.5F + 0.5F);
                        mix = w;
                    }
                    case "Aurora" -> {
                        float n = fbm(u * 2, v * 1, 3, 9);
                        float band = (float) Math.sin((v * 2 + n * 0.9) * Math.PI * 2);
                        strength = (float) Math.pow(Math.max(0, band), 3) * (0.6F + 0.4F * n);
                        mix = u;
                    }
                    case "Sparkle" -> {
                        float base = (float) Math.pow(fbm(u * 4, v * 4, 4, 17), 3) * 0.6F;
                        float stars = 0;
                        for (int i = 0; i < 26; i++) {
                            float sx = hash(i, 1), sy = hash(i, 2), r = 0.006F + 0.01F * hash(i, 3);
                            float dx = wrap(u - sx), dy = wrap(v - sy);
                            float d = (float) Math.sqrt(dx * dx + dy * dy);
                            float cross = Math.max(0, 1 - Math.min(Math.abs(dx), Math.abs(dy)) / 0.004F) * Math.max(0, 1 - d / (r * 6));
                            stars = Math.max(stars, Math.max(0, 1 - d / r) + cross * 0.7F);
                        }
                        strength = Math.min(1, base + stars);
                        mix = hash((int) (u * 8) + (int) (v * 8) * 8, 4);
                    }
                    default -> { // Flame: soft tapered tongues of fire that sway, overlap and flicker
                        float f = 0, hue = 0;
                        for (int i = 0; i < 16; i++) {
                            float phase = hash(i, 5), cx = hash(i, 6);
                            float up = 1 - v;                                          // image rows grow downward; flames rise
                            float t = up + phase - (float) Math.floor(up + phase);     // 0 at the base, 1 at the tip
                            float sway = 0.035F * (float) Math.sin((up * 2 + phase * 3) * Math.PI * 2);
                            float dx = wrap(u - cx - sway * t);
                            float width = (0.05F + 0.04F * hash(i, 7)) * (1 - t * 0.85F);
                            float body = (float) Math.exp(-(dx * dx) / (width * width));
                            float along = (float) Math.pow(1 - t, 1.3) * smooth(0.0F, 0.12F, t);
                            float tongue = body * along * (0.6F + 0.4F * hash(i, 8));
                            if (tongue > f) hue = t;
                            f = Math.max(f, tongue) + tongue * 0.25F;
                        }
                        float flicker = 0.8F + 0.4F * fbm(u * 2, v * 2, 3, 41);
                        strength = Math.min(1, f * flicker * 1.15F);
                        mix = Math.min(1, hue * 1.3F);
                    }
                }
                strength = Math.min(1, strength * intensity);
                int rgb;
                if (rainbow) {
                    rgb = java.awt.Color.HSBtoRGB((mix * 0.6F + hueShift) % 1.0F, 0.75F, 1.0F) & 0xFFFFFF;
                } else {
                    rgb = lerp(colorA, colorB, mix);
                }
                int r = (int) (((rgb >> 16) & 255) * strength), g = (int) (((rgb >> 8) & 255) * strength), b = (int) ((rgb & 255) * strength);
                out[y * SIZE + x] = 0xFF000000 | b << 16 | g << 8 | r;
            }
        }
        return out;
    }

    /**
     * Opaque galaxy: a deep blue base (Color 2) with swirling violet / pink nebula clouds (Color 1) and a scatter of
     * small stars. Intensity makes the clouds brighter and bigger.
     */
    private static int[] galaxy(int nebula, int base, boolean rainbow, float hueShift, float intensity) {
        if (rainbow) {
            nebula = java.awt.Color.HSBtoRGB((hueShift + 0.12F) % 1.0F, 0.7F, 1.0F) & 0xFFFFFF;
            base = java.awt.Color.HSBtoRGB(hueShift % 1.0F, 0.8F, 1.0F) & 0xFFFFFF;
        }
        int deep = scale(base, 0.38F), pink = lerp(nebula, 0xFFC4F4, 0.45F);
        float[] glow = new float[SIZE * SIZE];
        for (int i = 0; i < 70; i++) { // stars, a few with a soft halo
            float sx = hash(i, 21), sy = hash(i, 22), b = 0.35F + 0.65F * hash(i, 23), r = hash(i, 24) < 0.15F ? 2.6F : 1.1F;
            for (int dy = -4; dy <= 4; dy++) {
                for (int dx = -4; dx <= 4; dx++) {
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    float a = b * Math.max(0, 1 - d / r);
                    int px = Math.floorMod((int) (sx * SIZE) + dx, SIZE), py = Math.floorMod((int) (sy * SIZE) + dy, SIZE);
                    glow[py * SIZE + px] = Math.max(glow[py * SIZE + px], a);
                }
            }
        }
        int[] out = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float u = x / (float) SIZE, v = y / (float) SIZE;
                // warped fractal noise = smoky, swirling clouds (warping by tiling noise keeps it seamless)
                float wx = fbm(u, v, 4, 61), wy = fbm(u, v, 4, 67);
                float body = fbm(u + 0.45F * wx, v + 0.45F * wy, 5, 71);
                float wisps = fbm(u * 2 + 0.8F * wy, v * 2 + 0.8F * wx, 4, 79);
                int c = lerp(deep, base, smooth(0.25F, 0.7F, body));                    // blue body, darker in the folds
                float cloud = smooth(0.62F - 0.1F * intensity, 0.9F, body * 0.55F + wisps * 0.6F);
                c = lerp(c, nebula, cloud * 0.9F);                                        // violet clouds
                c = lerp(c, pink, smooth(0.78F, 1.0F, wisps * 0.7F + body * 0.45F) * Math.min(1, intensity)); // pink cores
                c = lerp(c, 0xFFFFFF, Math.min(1, glow[y * SIZE + x]) * 0.9F);            // stars
                out[y * SIZE + x] = 0xFF000000 | (c & 255) << 16 | (c & 0xFF00) | (c >> 16) & 255;
            }
        }
        return out;
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

    /** fractal noise over the unit square, scaled so it tiles: (u,v) in 0..1 times base frequency */
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
