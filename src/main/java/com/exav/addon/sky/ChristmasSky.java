package com.exav.addon.sky;

import com.exav.addon.holiday.SmoothMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Christmas sky: Santa's sleigh flying a big circle round you, eight reindeer with Rudolph at the front
 * (glowing nose), snow falling, and every 5 seconds a present thrown from the sleigh that bursts into confetti
 * with "Made By Exav, Merry Christmas!". Everything is worked out from the time since the song started, and
 * drawn camera-relative alongside the entities.
 */
public final class ChristmasSky {
    private ChristmasSky() {
    }

    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("anubis", "textures/misc/knife_base.png");
    static final float PRESENT_EVERY = 5.0F, FIRST_PRESENT = 2.5F, BURST_AT = 1.4F, TEXT_FOR = 3.2F;
    private static final int BRIGHT = 0xF000F0;
    private static final float GALLOP = 9.0F;

    private static SmoothMesh sleigh, reindeer, leg, redBall, cylinder;
    private static SmoothMesh[] presents;
    private static final int[][] WRAPS = {{0xC62828, 0xF2C230}, {0x2E7D32, 0xE53935}, {0x1565C0, 0xF5F5F5}, {0x6A1B9A, 0xF2C230}, {0xF5F5F5, 0xC62828}};

    /** where the reindeer are in the team (x along the flight, z sideways), Rudolph last */
    private static final float[][] DEER = {{6.2F, 0.95F}, {6.2F, -0.95F}, {9.0F, 0.95F}, {9.0F, -0.95F}, {11.8F, 0.95F}, {11.8F, -0.95F},
            {14.6F, 0.95F}, {14.6F, -0.95F}, {17.6F, 0}};

    private static void build() {
        if (sleigh != null) return;
        sleigh = ChristmasArt.sleigh();
        reindeer = ChristmasArt.reindeer();
        leg = ChristmasArt.leg();
        redBall = ChristmasArt.ball(0xFF1A1A);
        presents = new SmoothMesh[WRAPS.length];
        for (int i = 0; i < WRAPS.length; i++) presents[i] = ChristmasArt.present(WRAPS[i][0], WRAPS[i][1]);
        // a unit cylinder along +X for the harness lines
        SmoothMesh.Builder b = new SmoothMesh.Builder();
        com.exav.addon.holiday.Shapes.tube(b, java.util.List.of(new Vector3f(0, 0, 0), new Vector3f(1, 0, 0)), s -> 1.0F, (s, a) -> 0x5A1A12, 6);
        cylinder = b.build();
    }

    /** how big the circle is: about 40% of your view distance */
    static float radius() {
        Minecraft mc = Minecraft.getInstance();
        return Math.max(30.0F, Math.min(90.0F, mc.options.getEffectiveRenderDistance() * 16 * 0.4F));
    }

    /** the team's position (camera-relative) and facing at time t */
    static Vector3f teamPos(float t, float r) {
        double a = t * 0.11 + 0.6;
        return new Vector3f((float) Math.cos(a) * r, r * 0.38F + (float) Math.sin(t * 0.7) * r * 0.03F, (float) Math.sin(a) * r);
    }

    static Quaternionf teamRot(float t) {
        double a = t * 0.11 + 0.6;
        // flying round: heading is the tangent; lean into the turn a little
        float hx = (float) -Math.sin(a), hz = (float) Math.cos(a);
        return new Quaternionf().rotationY((float) Math.atan2(-hz, hx)).rotateX((float) Math.toRadians(-10));
    }

    static float unit(float r) {
        return r / 36.0F;
    }

    /** sack position (camera-relative) at time t, where presents get thrown from */
    static Vector3f sack(float t, float r) {
        return teamRot(t).transform(new Vector3f(-2.45F, 4.4F, 0).mul(unit(r))).add(teamPos(t, r));
    }

    public static void draw(PoseStack pose, SubmitNodeCollector collector, float t, float level) {
        build();
        Minecraft mc = Minecraft.getInstance();
        float r = radius();
        float s = unit(r) * level;
        RenderType solid = RenderTypes.entityCutoutNoCull(WHITE);
        RenderType glow = RenderTypes.entityTranslucent(WHITE);

        // --- Santa and the team
        Vector3f pos = teamPos(t, r);
        Quaternionf rot = teamRot(t);
        pose.pushPose();
        pose.translate(pos.x, pos.y, pos.z);
        pose.mulPose(rot);
        pose.scale(s, s, s);
        submit(pose, collector, solid, sleigh, BRIGHT);
        for (int i = 0; i < DEER.length; i++) {
            float phase = i * 0.7F;
            float bob = 0.22F * (float) Math.sin(t * GALLOP + phase);
            boolean rudolph = i == DEER.length - 1;
            pose.pushPose();
            pose.translate(DEER[i][0], 1.7F + (rudolph ? 0.2F : 0) + bob, DEER[i][1]);
            pose.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(6 * Math.sin(t * GALLOP + phase + 1))));
            submit(pose, collector, solid, reindeer, BRIGHT);
            // galloping legs
            float swing = (float) Math.toRadians(40 * Math.sin(t * GALLOP + phase));
            for (int l = 0; l < 4; l++) {
                boolean front = l < 2;
                pose.pushPose();
                pose.translate(front ? 0.75F : -0.8F, -0.25F, l % 2 == 0 ? 0.28F : -0.28F);
                pose.mulPose(new Quaternionf().rotationZ(front ? swing : -swing * 0.9F + 0.3F));
                submit(pose, collector, solid, leg, BRIGHT);
                pose.popPose();
            }
            if (rudolph) { // the nose so bright
                float pulse = 1.0F + 0.25F * (float) Math.sin(t * 6);
                pose.pushPose();
                pose.translate(2.02F, 0.98F, 0);
                pose.scale(0.14F, 0.14F, 0.14F);
                submit(pose, collector, solid, redBall, BRIGHT);
                pose.scale(2.6F * pulse, 2.6F * pulse, 2.6F * pulse);
                collector.submitCustomGeometry(pose, glow, (p, out) -> redBall.emit(p, out, BRIGHT, 0x80FF2020));
                pose.scale(1.8F, 1.8F, 1.8F);
                collector.submitCustomGeometry(pose, glow, (p, out) -> redBall.emit(p, out, BRIGHT, 0x30FF3030));
                pose.popPose();
            }
            pose.popPose();
        }
        // harness: two traces from the sleigh to the last pair, then one to Rudolph, with a bar across each pair
        for (int side = -1; side <= 1; side += 2) harness(pose, collector, solid, 3.3F, 1.5F, side * 0.95F, 14.6F, 1.75F, side * 0.95F);
        harness(pose, collector, solid, 14.6F, 1.75F, 0, 17.4F, 1.95F, 0);
        for (int pair = 0; pair < 4; pair++) harness(pose, collector, solid, 6.2F + pair * 2.8F, 1.75F, -0.95F, 6.2F + pair * 2.8F, 1.75F, 0.95F);
        pose.popPose();

        // --- presents: thrown every 5s, burst after a moment
        float since = t - FIRST_PRESENT;
        if (since >= 0) {
            int newest = (int) Math.floor(since / PRESENT_EVERY);
            for (int k = Math.max(0, newest - 1); k <= newest; k++) {
                float thrownAt = FIRST_PRESENT + k * PRESENT_EVERY;
                float age = t - thrownAt;
                if (age < 0 || age > BURST_AT + TEXT_FOR + 1.0F) continue;
                present(pose, collector, mc, k, thrownAt, age, r, s, solid, glow);
            }
        }

        // --- snow
        snow(pose, collector, mc, t, glow);
    }

    private static void harness(PoseStack pose, SubmitNodeCollector collector, RenderType type, float x0, float y0, float z0, float x1, float y1, float z1) {
        Vector3f d = new Vector3f(x1 - x0, y1 - y0, z1 - z0);
        float len = d.length();
        pose.pushPose();
        pose.translate(x0, y0, z0);
        pose.mulPose(new Quaternionf().rotationTo(1, 0, 0, d.x / len, d.y / len, d.z / len));
        pose.scale(len, 0.05F, 0.05F);
        submit(pose, collector, type, cylinder, BRIGHT);
        pose.popPose();
    }

    /** where present k is: thrown out sideways from the sack, arcs, then bursts */
    static Vector3f presentPos(float thrownAt, float age, float r) {
        Vector3f start = sack(thrownAt, r);
        Vector3f out = new Vector3f(start.x, 0, start.z).normalize();
        Vector3f v = out.mul(r * 0.11F).add(0, r * 0.07F, 0);
        float g = r * 0.16F;
        float a = Math.min(age, BURST_AT);
        return new Vector3f(start).fma(a, v).add(0, -0.5F * g * a * a, 0);
    }

    private static void present(PoseStack pose, SubmitNodeCollector collector, Minecraft mc, int k, float thrownAt, float age, float r, float s,
                                RenderType solid, RenderType glow) {
        Vector3f p = presentPos(thrownAt, age, r);
        if (age < BURST_AT) { // tumbling through the air
            pose.pushPose();
            pose.translate(p.x, p.y, p.z);
            pose.mulPose(new Quaternionf().rotationXYZ(age * 5 + k, age * 3.5F, age * 4 + k * 2));
            float size = s * 1.8F;
            pose.scale(size, size, size);
            submit(pose, collector, solid, presents[k % presents.length], BRIGHT);
            pose.popPose();
            return;
        }
        float b = age - BURST_AT;
        // flash
        if (b < 0.35F) {
            float f = 1 - b / 0.35F;
            pose.pushPose();
            pose.translate(p.x, p.y, p.z);
            float size = s * (1.5F + 5.0F * (1 - f));
            pose.scale(size, size, size);
            int a = (int) (200 * f);
            collector.submitCustomGeometry(pose, glow, (pp, out) -> redBall.emit(pp, out, BRIGHT, a << 24 | 0xFFF4C0));
            pose.popPose();
        }
        // confetti and ribbon bits flying out
        Random rnd = new Random(k * 7919L);
        float fade = 1 - Math.max(0, Math.min(1, (b - 1.6F) / 0.8F));
        if (fade > 0) {
            int a = (int) (255 * fade);
            Quaternionf cam = mc.gameRenderer.getMainCamera().rotation();
            float[][] bits = new float[70][];
            for (int i = 0; i < bits.length; i++) {
                Vector3f dir = new Vector3f(rnd.nextFloat() - 0.5F, rnd.nextFloat() - 0.35F, rnd.nextFloat() - 0.5F).normalize();
                float speed = r * (0.12F + 0.12F * rnd.nextFloat());
                float travel = (1 - (float) Math.exp(-2.2F * b)) / 2.2F;
                Vector3f q = new Vector3f(p).fma(speed * travel, dir).add(0, -r * 0.02F * b * b, 0);
                int[] wrap = WRAPS[rnd.nextInt(WRAPS.length)];
                bits[i] = new float[]{q.x, q.y, q.z, rnd.nextBoolean() ? wrap[0] : wrap[1], rnd.nextFloat() * 6.28F};
            }
            float bitSize = s * 0.35F;
            collector.submitCustomGeometry(pose, glow, (pp, out) -> {
                for (float[] bit : bits) flake(pp, out, cam, bit[0], bit[1], bit[2], bitSize, bitSize * 0.6F, bit[4] + b * 8, (int) bit[3], a);
            });
        }
        // the message
        if (b < TEXT_FOR + 1.0F) {
            float pop = backOut(Math.min(1, b / 0.35F));
            float alpha = 1 - Math.max(0, Math.min(1, (b - TEXT_FOR) / 1.0F));
            int a = (int) (255 * alpha);
            if (a > 4) {
                Font font = mc.font;
                MutableComponent msg = Component.literal("Made ").withStyle(ChatFormatting.RED)
                        .append(Component.literal("By ").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal("Exav").withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(", ").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("Merry ").withStyle(ChatFormatting.RED))
                        .append(Component.literal("Christmas!").withStyle(ChatFormatting.GREEN));
                float textScale = r * 0.075F / 9.0F * pop;
                pose.pushPose();
                pose.translate(p.x, p.y + r * 0.05F * Math.min(1, b), p.z);
                pose.mulPose(mc.gameRenderer.getMainCamera().rotation());
                pose.scale(textScale, -textScale, textScale);
                collector.submitText(pose, -font.width(msg) / 2.0F, -4.5F, msg.getVisualOrderText(), true, Font.DisplayMode.NORMAL, BRIGHT,
                        a << 24 | 0xFFFFFF, 0, 0);
                pose.popPose();
            }
        }
    }

    /** snow falling all around you (anchored to the world, so it doesn't follow your head) */
    private static void snow(PoseStack pose, SubmitNodeCollector collector, Minecraft mc, float t, RenderType glow) {
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        Quaternionf rot = mc.gameRenderer.getMainCamera().rotation();
        final int flakes = 1100;
        final float w = 56, h = 34;
        collector.submitCustomGeometry(pose, glow, (p, out) -> {
            for (int i = 0; i < flakes; i++) {
                float hx = hash(i, 1), hy = hash(i, 2), hz = hash(i, 3), hs = hash(i, 4);
                float fall = 1.2F + 1.3F * hs;
                float wx = hx * w + 0.6F * (float) Math.sin(t * (0.8F + hs) + i);
                float wy = hy * h - t * fall;
                float wz = hz * w + 0.6F * (float) Math.cos(t * (0.7F + hs) + i * 1.3F);
                // wrap into a box round the camera
                float x = wrap(wx - (float) cam.x, w), y = wrap(wy - (float) cam.y, h), z = wrap(wz - (float) cam.z, w);
                float size = 0.05F + 0.06F * hs;
                flake(p, out, rot, x, y + 4, z, size, size, i, 0xFFFFFF, 225);
            }
        });
    }

    private static float wrap(float v, float size) {
        float m = v - (float) Math.floor(v / size) * size;
        return m - size / 2;
    }

    private static float hash(int i, int k) {
        long h = i * 0x9E3779B97F4A7C15L + k * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }

    /** a small camera-facing quad (snowflake / confetti bit), turned by spin */
    private static void flake(PoseStack.Pose p, VertexConsumer out, Quaternionf cam, float x, float y, float z, float w, float h, float spin, int rgb, int a) {
        float c = (float) Math.cos(spin), sn = (float) Math.sin(spin);
        Vector3f right = cam.transform(new Vector3f(c, sn, 0)), up = cam.transform(new Vector3f(-sn, c, 0)), n = cam.transform(new Vector3f(0, 0, 1));
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        float[][] k = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (float[] q : k) {
            out.addVertex(p, x + right.x * w * q[0] + up.x * h * q[1], y + right.y * w * q[0] + up.y * h * q[1], z + right.z * w * q[0] + up.z * h * q[1])
                    .setColor(r, g, b, a).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(BRIGHT).setNormal(p, n.x, n.y, n.z);
        }
    }

    private static void submit(PoseStack pose, SubmitNodeCollector collector, RenderType type, SmoothMesh mesh, int light) {
        collector.submitCustomGeometry(pose, type, (p, out) -> mesh.emit(p, out, light));
    }

    private static float backOut(float u) {
        u = Math.max(0, Math.min(1, u));
        float c = 1.9F, x = u - 1;
        return 1 + (c + 1) * x * x * x + c * x * x;
    }
}
