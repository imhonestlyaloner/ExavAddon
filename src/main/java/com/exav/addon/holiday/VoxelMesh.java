package com.exav.addon.holiday;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.Arrays;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * A little voxel model: coloured 1x1x1 cubes, built once into the quads of their exposed faces.
 * One model unit = one voxel; the renderer scales it down to hand size.
 */
public final class VoxelMesh {
    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    /** a touch of per-face shading so edges read even in flat light */
    private static final float[] FACE_TONE = {0.9F, 0.9F, 1.0F, 0.7F, 0.95F, 0.85F};

    private final float[] pos;
    private final float[] normal;
    private final int[] color;
    private final int quads;

    private VoxelMesh(float[] pos, float[] normal, int[] color, int quads) {
        this.pos = pos;
        this.normal = normal;
        this.color = color;
        this.quads = quads;
    }

    public void emit(PoseStack.Pose pose, VertexConsumer out, int light) {
        for (int q = 0; q < quads; q++) {
            float nx = normal[q * 3], ny = normal[q * 3 + 1], nz = normal[q * 3 + 2];
            int argb = color[q];
            for (int v = 0; v < 4; v++) {
                int p = q * 12 + v * 3;
                out.addVertex(pose, pos[p], pos[p + 1], pos[p + 2])
                        .setColor(argb)
                        .setUv(0.5F, 0.5F)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, nx, ny, nz);
            }
        }
    }

    /** Collects voxels, then {@link #build()}s the mesh. */
    public static final class Builder {
        private final Long2IntOpenHashMap cells = new Long2IntOpenHashMap();

        private static long key(int x, int y, int z) {
            return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
        }

        public Builder set(int x, int y, int z, int rgb) {
            cells.put(key(x, y, z), 0xFF000000 | rgb);
            return this;
        }

        public Builder clear(int x, int y, int z) {
            cells.remove(key(x, y, z));
            return this;
        }

        public boolean has(int x, int y, int z) {
            return cells.containsKey(key(x, y, z));
        }

        public int get(int x, int y, int z) {
            return cells.get(key(x, y, z));
        }

        /** Removes every voxel for which the test says so (used to take bites out). */
        public Builder carve(Carve test) {
            cells.long2IntEntrySet().removeIf(e -> {
                long k = e.getLongKey();
                int x = (int) (k >>> 40) - 512, y = (int) ((k >>> 20) & 0xFFFFF) - 512, z = (int) (k & 0xFFFFF) - 512;
                return test.remove(x, y, z);
            });
            return this;
        }

        public Builder copy() {
            Builder b = new Builder();
            b.cells.putAll(cells);
            return b;
        }

        public VoxelMesh build() {
            int capacity = 256;
            float[] pos = new float[capacity * 12];
            float[] normal = new float[capacity * 3];
            int[] color = new int[capacity];
            int quads = 0;
            for (var e : cells.long2IntEntrySet()) {
                long k = e.getLongKey();
                int x = (int) (k >>> 40) - 512, y = (int) ((k >>> 20) & 0xFFFFF) - 512, z = (int) (k & 0xFFFFF) - 512;
                for (int f = 0; f < 6; f++) {
                    int[] d = FACES[f];
                    if (has(x + d[0], y + d[1], z + d[2])) continue;
                    if (quads == capacity) {
                        capacity *= 2;
                        pos = Arrays.copyOf(pos, capacity * 12);
                        normal = Arrays.copyOf(normal, capacity * 3);
                        color = Arrays.copyOf(color, capacity);
                    }
                    face(pos, quads * 12, x, y, z, d);
                    normal[quads * 3] = d[0];
                    normal[quads * 3 + 1] = d[1];
                    normal[quads * 3 + 2] = d[2];
                    color[quads] = tone(e.getIntValue(), FACE_TONE[f]);
                    quads++;
                }
            }
            return new VoxelMesh(Arrays.copyOf(pos, quads * 12), Arrays.copyOf(normal, quads * 3), Arrays.copyOf(color, quads), quads);
        }
    }

    @FunctionalInterface
    public interface Carve {
        boolean remove(int x, int y, int z);
    }

    private static int tone(int argb, float k) {
        int r = (int) (((argb >> 16) & 0xFF) * k), g = (int) (((argb >> 8) & 0xFF) * k), b = (int) ((argb & 0xFF) * k);
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    private static void face(float[] out, int at, int x, int y, int z, int[] f) {
        float x0 = x, x1 = x + 1, y0 = y, y1 = y + 1, z0 = z, z1 = z + 1;
        float[] c;
        if (f[0] == 1) c = new float[]{x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1};
        else if (f[0] == -1) c = new float[]{x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0};
        else if (f[1] == 1) c = new float[]{x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0};
        else if (f[1] == -1) c = new float[]{x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1};
        else if (f[2] == 1) c = new float[]{x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1};
        else c = new float[]{x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0};
        System.arraycopy(c, 0, out, at, 12);
    }
}
