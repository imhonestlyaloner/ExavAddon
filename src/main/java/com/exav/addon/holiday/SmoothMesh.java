package com.exav.addon.holiday;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

/**
 * A smooth model: quads (triangles are quads with a repeated corner) with a colour and normal per corner,
 * so curved surfaces get smooth lighting and colours blend across them. Built once by the shape builders.
 */
public final class SmoothMesh {
    final float[] pos;
    final float[] normal;
    final int[] color;
    private final int quads;

    private SmoothMesh(float[] pos, float[] normal, int[] color) {
        this.pos = pos;
        this.normal = normal;
        this.color = color;
        this.quads = color.length / 4;
    }

    public void emit(PoseStack.Pose pose, VertexConsumer out, int light) {
        for (int v = 0; v < quads * 4; v++) {
            int p = v * 3;
            out.addVertex(pose, pos[p], pos[p + 1], pos[p + 2])
                    .setColor(color[v])
                    .setUv(0.5F, 0.5F)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, normal[p], normal[p + 1], normal[p + 2]);
        }
    }

    /** Same, multiplying every colour by {@code tint} (ARGB, so its alpha fades the whole mesh). */
    public void emit(PoseStack.Pose pose, VertexConsumer out, int light, int tint) {
        float ta = ((tint >>> 24) & 255) / 255.0F, tr = ((tint >> 16) & 255) / 255.0F, tg = ((tint >> 8) & 255) / 255.0F, tb = (tint & 255) / 255.0F;
        for (int v = 0; v < quads * 4; v++) {
            int p = v * 3, c = color[v];
            out.addVertex(pose, pos[p], pos[p + 1], pos[p + 2])
                    .setColor((int) (((c >> 16) & 255) * tr), (int) (((c >> 8) & 255) * tg), (int) ((c & 255) * tb), (int) (255 * ta))
                    .setUv(0.5F, 0.5F)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, normal[p], normal[p + 1], normal[p + 2]);
        }
    }

    /** number of quads (4 corners each) */
    public int quads() {
        return quads;
    }

    /** corner positions (x, y, z per corner, 4 corners per quad) */
    public float[] positions() {
        return pos;
    }

    public float[] normals() {
        return normal;
    }

    /** ARGB per corner */
    public int[] colors() {
        return color;
    }

    /** One corner: position, normal, colour (RGB). */
    public record V(float x, float y, float z, float nx, float ny, float nz, int rgb) {
        public static V of(Vector3f p, Vector3f n, int rgb) {
            return new V(p.x, p.y, p.z, n.x, n.y, n.z, rgb);
        }
    }

    public static final class Builder {
        private final FloatArrayList pos = new FloatArrayList();
        private final FloatArrayList normal = new FloatArrayList();
        private final IntArrayList color = new IntArrayList();

        public Builder quad(V a, V b, V c, V d) {
            add(a);
            add(b);
            add(c);
            add(d);
            return this;
        }

        public Builder tri(V a, V b, V c) {
            return quad(a, b, c, c);
        }

        private void add(V v) {
            pos.add(v.x);
            pos.add(v.y);
            pos.add(v.z);
            float len = (float) Math.sqrt(v.nx * v.nx + v.ny * v.ny + v.nz * v.nz);
            if (len < 1e-6F) len = 1;
            normal.add(v.nx / len);
            normal.add(v.ny / len);
            normal.add(v.nz / len);
            color.add(0xFF000000 | v.rgb);
        }

        public SmoothMesh build() {
            return new SmoothMesh(pos.toFloatArray(), normal.toFloatArray(), color.toIntArray());
        }
    }
}
