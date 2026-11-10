package com.exav.addon.sky;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Draws a textured sky during Minecraft's sky pass (behind all terrain, no fog): a dark gradient dome with stars,
 * and a ring of screens playing the cat video. Same approach Anubis uses for its skies: its own pipeline,
 * position/uv/colour quads on a sphere around the camera, one draw call per texture.
 */
public final class VideoSky {
    private VideoSky() {
    }

    private static final float RADIUS = 90.0F;
    public static final int FRAMES = 132, PER_SHEET = 55, COLS = 11;
    public static final float FPS = 12.0F, LENGTH = FRAMES / FPS;
    private static final float FRAME_W = 176.0F / 2048.0F, FRAME_H = 384.0F / 2048.0F, ASPECT = 176.0F / 384.0F;

    private static final Identifier[] SHEETS = {
            Identifier.fromNamespaceAndPath("exav-addon", "textures/sky/cat_video_0.png"),
            Identifier.fromNamespaceAndPath("exav-addon", "textures/sky/cat_video_1.png"),
            Identifier.fromNamespaceAndPath("exav-addon", "textures/sky/cat_video_2.png"),
    };
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("anubis", "textures/misc/knife_base.png");

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("exav-addon", "pipeline/video_sky"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader("core/position_tex_color")
            .withSampler("Sampler0")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .build();

    private static ByteBufferBuilder bytes;

    /** what to draw this frame */
    public record Look(int screens, float screenHeightDeg, float raiseDeg, float spinDeg, float brightness, boolean background,
                       int topColor, int horizonColor, float alpha) {
    }

    /** horizon colour of the background, for matching the fog */
    public static final int TOP = 0x0B0716, HORIZON = 0x2A1838;

    public static void draw(float videoTime, Look look) {
        int frame = Math.floorMod((int) Math.floor(videoTime * FPS), FRAMES);
        int sheet = frame / PER_SHEET, index = frame % PER_SHEET;
        float u0 = (index % COLS) * FRAME_W, v0 = (index / COLS) * FRAME_H;

        if (look.background()) {
            quads(WHITE, out -> {
                dome(out, look);
                stars(out, look.alpha());
            });
        }
        quads(SHEETS[sheet], out -> {
            for (int i = 0; i < look.screens(); i++) {
                float centre = look.spinDeg() + 360.0F * i / look.screens();
                screen(out, centre, look, u0, v0);
            }
        });
    }

    // ---------------------------------------------------------------- geometry on the sky sphere

    private static Vector3f dir(float azimuthDeg, float elevationDeg) {
        double a = Math.toRadians(azimuthDeg), e = Math.toRadians(elevationDeg);
        return new Vector3f((float) (Math.cos(e) * Math.sin(a)), (float) Math.sin(e), (float) (Math.cos(e) * Math.cos(a))).mul(RADIUS);
    }

    private static void vertex(BufferBuilder out, Vector3f p, float u, float v, int argb) {
        out.addVertex(p.x, p.y, p.z).setUv(u, v).setColor(argb);
    }

    private static int argb(int rgb, float alpha, float bright) {
        int a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        int r = Math.min(255, Math.round(((rgb >> 16) & 255) * bright)), g = Math.min(255, Math.round(((rgb >> 8) & 255) * bright)),
                b = Math.min(255, Math.round((rgb & 255) * bright));
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** dark gradient sphere: darkest overhead, a soft purple glow at the horizon */
    private static void dome(BufferBuilder out, Look look) {
        int bands = 18, slices = 32;
        for (int b = 0; b < bands; b++) {
            float e0 = -90 + 180.0F * b / bands, e1 = -90 + 180.0F * (b + 1) / bands;
            int c0 = gradient(e0, look), c1 = gradient(e1, look);
            for (int s = 0; s < slices; s++) {
                float a0 = 360.0F * s / slices, a1 = 360.0F * (s + 1) / slices;
                vertex(out, dir(a0, e0), 0.5F, 0.5F, c0);
                vertex(out, dir(a1, e0), 0.5F, 0.5F, c0);
                vertex(out, dir(a1, e1), 0.5F, 0.5F, c1);
                vertex(out, dir(a0, e1), 0.5F, 0.5F, c1);
            }
        }
    }

    private static int gradient(float elevation, Look look) {
        float t = Math.max(0, Math.min(1, Math.abs(elevation) / 70.0F));
        t = (float) Math.sqrt(t);
        int top = look.topColor(), hor = look.horizonColor();
        int r = (int) (((hor >> 16) & 255) * (1 - t) + ((top >> 16) & 255) * t);
        int g = (int) (((hor >> 8) & 255) * (1 - t) + ((top >> 8) & 255) * t);
        int b = (int) ((hor & 255) * (1 - t) + (top & 255) * t);
        return argb(r << 16 | g << 8 | b, look.alpha(), 1.0F);
    }

    /** a few hundred faint stars (fixed pattern) */
    private static void stars(BufferBuilder out, float alpha) {
        java.util.Random r = new java.util.Random(31);
        for (int i = 0; i < 320; i++) {
            float az = r.nextFloat() * 360, el = 8 + r.nextFloat() * 80;
            float size = 0.12F + r.nextFloat() * 0.25F;
            int c = argb(0xFFFFFF, alpha * (0.35F + 0.65F * r.nextFloat()), 1);
            Vector3f centre = dir(az, el);
            Vector3f right = new Vector3f(centre).cross(0, 1, 0).normalize().mul(size);
            Vector3f up = new Vector3f(right).cross(centre).normalize().mul(size);
            vertex(out, new Vector3f(centre).sub(right).sub(up), 0.5F, 0.5F, c);
            vertex(out, new Vector3f(centre).add(right).sub(up), 0.5F, 0.5F, c);
            vertex(out, new Vector3f(centre).add(right).add(up), 0.5F, 0.5F, c);
            vertex(out, new Vector3f(centre).sub(right).add(up), 0.5F, 0.5F, c);
        }
    }

    /** one curved screen on the inside of the sky sphere, playing the current frame */
    private static void screen(BufferBuilder out, float centreAz, Look look, float u0, float v0) {
        float h = look.screenHeightDeg();
        float bottom = look.raiseDeg() - h * 0.12F, top = bottom + h;
        // keep the video's shape: width in degrees grows with how far the screen is from the horizon
        float midEl = (bottom + top) / 2;
        float w = h * ASPECT / (float) Math.max(0.35, Math.cos(Math.toRadians(midEl)));
        int cols = 10, rows = 16;
        int c = argb(0xFFFFFF, look.alpha(), look.brightness());
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                float fx0 = x / (float) cols, fx1 = (x + 1) / (float) cols, fy0 = y / (float) rows, fy1 = (y + 1) / (float) rows;
                float az0 = centreAz - w / 2 + w * fx0, az1 = centreAz - w / 2 + w * fx1;
                float el0 = top - h * fy0, el1 = top - h * fy1; // v grows downward in the image
                vertex(out, dir(az0, el0), u0 + FRAME_W * fx0, v0 + FRAME_H * fy0, c);
                vertex(out, dir(az1, el0), u0 + FRAME_W * fx1, v0 + FRAME_H * fy0, c);
                vertex(out, dir(az1, el1), u0 + FRAME_W * fx1, v0 + FRAME_H * fy1, c);
                vertex(out, dir(az0, el1), u0 + FRAME_W * fx0, v0 + FRAME_H * fy1, c);
            }
        }
    }

    // ---------------------------------------------------------------- the draw call

    private static void quads(Identifier texture, java.util.function.Consumer<BufferBuilder> build) {
        if (bytes == null) bytes = new ByteBufferBuilder(1 << 18);
        BufferBuilder out = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        build.accept(out);
        MeshData mesh = out.build();
        if (mesh == null) return;
        try {
            int quads = mesh.drawState().indexCount() / 6;
            GpuBuffer vertices = DefaultVertexFormat.POSITION_TEX_COLOR.uploadImmediateVertexBuffer(mesh.vertexBuffer());
            RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
            GpuBuffer indexBuffer = indices.getBuffer(quads * 6);
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                    .writeTransform(RenderSystem.getModelViewStack(), new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f());
            AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(texture);
            RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Exav video sky",
                    target.getColorTextureView(), OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
                pass.setPipeline(PIPELINE);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                pass.setVertexBuffer(0, vertices);
                pass.setIndexBuffer(indexBuffer, indices.type());
                pass.bindTexture("Sampler0", tex.getTextureView(), tex.getSampler());
                pass.drawIndexed(0, 0, quads * 6, 1);
            }
        } finally {
            mesh.close();
        }
    }
}
