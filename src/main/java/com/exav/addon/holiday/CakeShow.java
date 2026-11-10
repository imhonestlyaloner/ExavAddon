package com.exav.addon.holiday;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import imgui.ImColor;
import imgui.ImDrawList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Everything around the Birthday Cake inspect besides the cake in your hand: the confetti that spells out the
 * message, the picture popping up, the dynamite, the explosion and the cake splatted on the screen.
 * All positions are in hand space (see {@link HolidayAnimation}).
 */
public final class CakeShow {
    // timeline (seconds)
    public static final float POP = 0.45F, DYNAMITE = 2.9F, LAND = 3.6F, BOOM = 4.45F, BACK = 6.5F, END = 7.1F;
    private static final float FLIGHT = 0.75F;

    static final Vector3f CAMERA = new Vector3f(-0.56F, 0.52F, 0.72F);
    /** where the cake is held up during the show (grip point) and how it's tilted (top toward you a bit) */
    public static final Vector3f SHOW = new Vector3f(-0.47F, 0.0F, -0.24F);
    public static final Quaternionf SHOW_ROT = new Quaternionf().rotationX((float) Math.toRadians(16));
    private static final Vector3f TEXT_CENTRE = new Vector3f(-0.54F, 0.9F, -0.44F);
    private static final Vector3f IMAGE_CENTRE = new Vector3f(-0.53F, 0.52F, -0.36F);
    private static final float IMAGE_BASE = 0.34F;

    public static final Identifier IMAGE = Identifier.fromNamespaceAndPath("exav-addon", "textures/misc/birthday_hat.png");
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("anubis", "textures/misc/knife_base.png");

    public record Config(String text, float textSize, float textOpacity, float opacity, float size, int density, float flutter,
                         String palette, boolean showImage, float imageSize, float imageOpacity, boolean splat) {
    }

    private static final int[][] PALETTES = {
            {0xE53935, 0xFB8C00, 0xFDD835, 0x43A047, 0x00ACC1, 0x1E88E5, 0x8E24AA, 0xEC407A}, // Rainbow
            {0xF8BBD0, 0xB3E5FC, 0xC8E6C9, 0xFFF9C4, 0xD1C4E9, 0xFFE0B2},                     // Pastel
            {0xFFD54F, 0xFFC107, 0xE0E0E0, 0xFFFFFF, 0xFFE082},                               // Gold
            {0xD32F2F, 0x388E3C, 0xFFFFFF, 0xFFC107},                                         // Christmas
    };
    public static final String[] PALETTE_NAMES = {"Rainbow", "Pastel", "Gold", "Christmas"};

    // confetti, rebuilt when the settings change
    private String layoutKey = "";
    private int count;
    private float[] start, velocity, axis, phase, delay, blow, drift;
    private int[] colors;

    // ------------------------------------------------------------------ the hand / cake pose

    /** Where the hand and cake are during the show. */
    public static HolidayAnimation.Frame frame(float t, float size) {
        HolidayAnimation.Skin skin = HolidayAnimation.Skin.BIRTHDAY_CAKE;
        HolidayAnimation.Frame f = new HolidayAnimation.Frame();
        Quaternionf rest = skin.restRotation();
        f.rotation.set(SHOW_ROT);
        if (t < POP) { // lift it up in front of you
            float u = HolidayAnimation.smooth(t / POP);
            f.hand.set(HolidayAnimation.FIST).lerp(SHOW, u);
            f.rotation.set(rest).slerp(SHOW_ROT, u);
        } else if (t < BOOM) {
            f.hand.set(SHOW);
            float since = t - POP;
            if (since < 0.25F) f.hand.y += 0.03F * (float) Math.sin(Math.PI * since / 0.25F); // the pop
            f.hand.y += 0.006F * (float) Math.sin(t * 3.0F);
            float land = t - LAND;
            if (land >= 0 && land < 0.3F) f.hand.y -= 0.025F * (float) Math.sin(Math.PI * land / 0.3F); // dynamite hits
            if (t > LAND) { // shaking as the fuse burns down
                float shake = HolidayAnimation.smooth((t - LAND) / (BOOM - LAND)) * 0.006F;
                f.hand.x += shake * (float) Math.sin(t * 61);
                f.hand.y += shake * (float) Math.sin(t * 47);
            }
        } else if (t < BACK) { // blown back, cake gone, hand drifts back down
            float u = t - BOOM;
            float recoil = u < 0.35F ? (float) Math.sin(Math.PI * u / 0.35F) : 0.0F;
            Vector3f from = new Vector3f(SHOW).add(0.04F * recoil, -0.08F * recoil, 0.1F * recoil);
            f.hand.set(from).lerp(HolidayAnimation.FIST, HolidayAnimation.smooth((u - 0.2F) / 1.5F));
            f.rotation.set(SHOW_ROT).slerp(rest, HolidayAnimation.smooth(u / 1.2F));
            f.itemScale = 0.0F;
        } else { // a fresh cake pops back into your hand
            float u = Math.min(1.0F, (t - BACK) / (END - BACK));
            f.hand.set(HolidayAnimation.FIST);
            f.rotation.set(rest);
            f.itemScale = backOut(u);
        }
        f.item.set(f.hand);
        return f;
    }

    // ------------------------------------------------------------------ drawing

    public void draw(PoseStack pose, SubmitNodeCollector collector, int light, float t, float size, Config cfg,
                     SmoothMesh dynamite, SmoothMesh puff, SmoothMesh flame, SmoothMesh[] chunks) {
        HolidayAnimation.Skin skin = HolidayAnimation.Skin.BIRTHDAY_CAKE;
        float scale = skin.voxel * size;
        Vector3f cakeTop = HolidayAnimation.pointOf(SHOW, SHOW_ROT, scale, skin, 0, HolidayModels.CAKE_TOP_Y, 0);
        Vector3f cakeMid = HolidayAnimation.pointOf(SHOW, SHOW_ROT, scale, skin, 0, 4.0F, 0);
        layout(cfg, cakeTop);
        text(pose, collector, t, cfg);
        RenderType translucent = RenderTypes.entityTranslucent(WHITE);
        int bright = 0xF000F0;

        // --- confetti
        if (t >= POP && t < BOOM + 1.6F) {
            float alpha = cfg.opacity() * (t > BOOM ? 1.0F - HolidayAnimation.smooth((t - BOOM - 0.5F) / 1.1F) : 1.0F);
            if (alpha > 0.01F) {
                collector.submitCustomGeometry(pose, translucent, (p, out) -> confetti(p, out, t, cfg, cakeMid, alpha, light));
            }
        }

        // --- the picture
        if (cfg.showImage() && t >= POP && t < BOOM + 0.9F) {
            float u = (t - POP) / 0.45F;
            float s = IMAGE_BASE * cfg.imageSize() * backOut(Math.min(1.0F, u));
            Vector3f c = new Vector3f(IMAGE_CENTRE).add(0, 0.012F * (float) Math.sin(t * 2.2F), 0);
            float spin = 0;
            float a = cfg.imageOpacity();
            if (t > BOOM) { // blown up and away, spinning
                float b = t - BOOM;
                c.add(0.25F * b, 0.9F * b - 0.6F * b * b, -0.2F * b);
                spin = b * 540.0F;
                a *= 1.0F - HolidayAnimation.smooth(b / 0.9F);
            }
            if (s > 0.001F && a > 0.01F) {
                Vector3f centre = c;
                float half = s / 2, rot = (float) Math.toRadians(spin), alphaF = a;
                collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(IMAGE), (p, out) -> picture(p, out, centre, half, rot, alphaF, bright));
            }
        }

        // --- candle flames (while the cake is up)
        // drawn by the module with the cake; nothing here

        // --- dynamite falling, then stuck in the cake with its fuse burning
        if (t >= DYNAMITE && t < BOOM) {
            Vector3f land = HolidayAnimation.pointOf(SHOW, SHOW_ROT, scale, skin, 1.3F, HolidayModels.CAKE_TOP_Y - 2.2F, -0.6F);
            float u = Math.min(1.0F, (t - DYNAMITE) / (LAND - DYNAMITE));
            Vector3f at = new Vector3f(land).add(0.05F * (1 - u), 1.45F * (1 - u * u), 0);
            float wobble = t > LAND ? 8.0F * (float) Math.exp(-(t - LAND) * 6) * (float) Math.sin((t - LAND) * 30) : 0;
            Quaternionf rot = new Quaternionf(SHOW_ROT).rotateZ((float) Math.toRadians((1 - u) * 230 + 12 + wobble));
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(rot);
            pose.scale(scale, scale, scale);
            collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(WHITE), (p, out) -> dynamite.emit(p, out, light));
            // the spark runs down the fuse once it has landed
            float burn = t < LAND ? 0 : HolidayAnimation.smooth((t - LAND) / (BOOM - LAND));
            float[] spot = alongFuse(1.0F - burn);
            for (int i = 0; i < 5; i++) {
                float j = (float) Math.sin(t * 40 + i * 2.1F) * 0.35F, k = (float) Math.cos(t * 33 + i * 1.7F) * 0.35F;
                pose.pushPose();
                pose.translate(spot[0] + j, spot[1] + k, j * k);
                float sp = 0.25F + 0.2F * (float) Math.abs(Math.sin(t * 50 + i));
                pose.scale(sp, sp, sp);
                int col = i % 2 == 0 ? 0xFFFFE082 : 0xFFFF6F00;
                collector.submitCustomGeometry(pose, translucent, (p, out) -> puff.emit(p, out, bright, col));
                pose.popPose();
            }
            pose.popPose();
        }

        // --- explosion: flash, smoke, cake chunks flying at your face
        if (t >= BOOM && t < BOOM + 1.6F) {
            float b = t - BOOM;
            if (b < 0.3F) { // flash
                float s = 0.06F + 0.5F * HolidayAnimation.smooth(b / 0.3F);
                int a = (int) (255 * (1 - b / 0.3F));
                puffAt(pose, collector, translucent, puff, cakeMid, s, (a << 24) | 0xFFC04A, bright);
                puffAt(pose, collector, translucent, puff, cakeMid, s * 0.6F, (a << 24) | 0xFFFFF0, bright);
            }
            Random r = new Random(5);
            for (int i = 0; i < 9; i++) { // smoke
                Vector3f c = new Vector3f(cakeMid).add((r.nextFloat() - 0.5F) * 0.2F, (r.nextFloat() - 0.3F) * 0.15F + b * 0.25F, (r.nextFloat() - 0.5F) * 0.15F);
                float s = 0.04F + 0.11F * HolidayAnimation.smooth(b / 1.2F) * (0.7F + r.nextFloat() * 0.6F);
                int a = (int) (120 * (1 - HolidayAnimation.smooth(b / 1.3F)));
                int grey = 0x90 + r.nextInt(0x30);
                puffAt(pose, collector, translucent, puff, c, s, (a << 24) | grey << 16 | grey << 8 | grey, light);
            }
            Random cr = new Random(9);
            for (int i = 0; i < 44; i++) { // chunks
                SmoothMesh chunk = chunks[i % chunks.length];
                boolean atFace = i < 28;
                float sz = 0.012F + cr.nextFloat() * 0.022F;
                Vector3f p;
                if (atFace) { // straight at the camera, gone when they hit (the splats take over)
                    float travel = 0.16F + cr.nextFloat() * 0.1F;
                    float u = b / travel;
                    if (u >= 1) continue;
                    Vector3f dest = new Vector3f(CAMERA).add((cr.nextFloat() - 0.5F) * 0.5F, (cr.nextFloat() - 0.55F) * 0.35F, -0.1F);
                    p = new Vector3f(cakeMid).lerp(dest, u).add(0, 0.12F * u * (1 - u), 0);
                } else { // the rest flies everywhere and falls
                    Vector3f v = new Vector3f(cr.nextFloat() - 0.5F, cr.nextFloat() * 0.8F + 0.2F, cr.nextFloat() - 0.5F).normalize().mul(0.9F + cr.nextFloat() * 0.8F);
                    p = new Vector3f(cakeMid).fma(b, v).add(0, -1.8F * b * b, 0);
                }
                pose.pushPose();
                pose.translate(p.x, p.y, p.z);
                pose.mulPose(new Quaternionf().rotationXYZ(b * 9 + i, b * 7 + i * 2, b * 5));
                pose.scale(sz, sz, sz);
                collector.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(WHITE), (pp, out) -> chunk.emit(pp, out, light));
                pose.popPose();
            }
        }
    }

    private static void puffAt(PoseStack pose, SubmitNodeCollector collector, RenderType type, SmoothMesh puff, Vector3f at, float s, int argb, int light) {
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.scale(s, s, s);
        collector.submitCustomGeometry(pose, type, (p, out) -> puff.emit(p, out, light, argb));
        pose.popPose();
    }

    private static float[] alongFuse(float frac) {
        float[][] f = HolidayModels.FUSE;
        float pos = frac * (f.length - 1);
        int i = Math.min(f.length - 2, (int) pos);
        float u = pos - i;
        return new float[]{f[i][0] + (f[i + 1][0] - f[i][0]) * u, f[i][1] + (f[i + 1][1] - f[i][1]) * u};
    }

    /** the picture: a square facing you, both sides */
    private static void picture(PoseStack.Pose p, VertexConsumer out, Vector3f c, float half, float rot, float alpha, int light) {
        float cos = (float) Math.cos(rot), sin = (float) Math.sin(rot);
        float[][] corner = {{-half, -half, 0, 1}, {half, -half, 1, 1}, {half, half, 1, 0}, {-half, half, 0, 0}};
        int a = (int) (alpha * 255);
        for (int side = 0; side < 2; side++) {
            for (int k = 0; k < 4; k++) {
                float[] q = corner[side == 0 ? k : 3 - k];
                float x = q[0] * cos - q[1] * sin, y = q[0] * sin + q[1] * cos;
                out.addVertex(p, c.x + x, c.y + y, c.z)
                        .setColor(255, 255, 255, a)
                        .setUv(q[2], q[3])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(p, 0, 0, side == 0 ? 1 : -1);
            }
        }
    }

    // ------------------------------------------------------------------ the message, in plain Minecraft text

    private static void text(PoseStack pose, SubmitNodeCollector collector, float t, Config cfg) {
        if (t < POP || t > BOOM + 1.0F || cfg.text().isBlank()) return;
        Font font = Minecraft.getInstance().font;
        List<String> lines = wrap(cfg.text(), 22);
        int widest = 1;
        for (String l : lines) widest = Math.max(widest, font.width(l));
        // fit the widest line into about 1.1 blocks, then apply the size setting
        float px = Math.min(0.011F, 1.1F / widest) * cfg.textSize();
        float pop = backOut(Math.min(1.0F, (t - POP - 0.1F) / 0.4F));
        if (pop <= 0.001F) return;
        float alpha = cfg.textOpacity();
        Vector3f c = new Vector3f(TEXT_CENTRE).add(0, 0.01F * (float) Math.sin(t * 2.0F + 1.0F), 0);
        float tilt = 0;
        if (t > BOOM) { // blown up and away
            float b = t - BOOM;
            c.add(-0.15F * b, 0.8F * b - 0.5F * b * b, -0.15F * b);
            tilt = b * 140.0F;
            alpha *= 1.0F - HolidayAnimation.smooth(b / 1.0F);
        }
        int a = (int) (Math.max(0, Math.min(1, alpha)) * 255);
        if (a < 5) return;
        float s = px * pop;
        pose.pushPose();
        pose.translate(c.x, c.y, c.z);
        pose.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(tilt)));
        pose.scale(s, -s, s); // text draws y-down
        float lineH = 10;
        float top = -(lines.size() * lineH - 1) / 2.0F;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            collector.submitText(pose, -font.width(line) / 2.0F, top + i * lineH, Component.literal(line).getVisualOrderText(),
                    true, Font.DisplayMode.NORMAL, 0xF000F0, (a << 24) | 0xFFFFFF, 0, 0);
        }
        pose.popPose();
    }

    /** splits on spaces into lines of at most maxChars, keeping the text's own capitals */
    private static List<String> wrap(String text, int maxChars) {
        List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > maxChars) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    // ------------------------------------------------------------------ confetti: bursts out of the cake and flutters down

    private void layout(Config cfg, Vector3f cakeTop) {
        String key = cfg.density() + "|" + cfg.palette() + "|" + cakeTop.x + "," + cakeTop.y;
        if (key.equals(layoutKey)) return;
        layoutKey = key;
        count = 110 * Math.max(1, cfg.density());
        start = new float[count * 3];
        velocity = new float[count * 3];
        axis = new float[count * 3];
        phase = new float[count];
        delay = new float[count];
        blow = new float[count * 3];
        drift = new float[count];
        colors = new int[count];
        int[] palette = PALETTES[Math.max(0, java.util.Arrays.asList(PALETTE_NAMES).indexOf(cfg.palette()))];
        Random r = new Random(42);
        for (int i = 0; i < count; i++) {
            start[i * 3] = cakeTop.x + (r.nextFloat() - 0.5F) * 0.04F;
            start[i * 3 + 1] = cakeTop.y + 0.03F;
            start[i * 3 + 2] = cakeTop.z + (r.nextFloat() - 0.5F) * 0.04F;
            // a fountain: mostly up, spread out sideways
            double a = r.nextDouble() * Math.PI * 2;
            float out = 0.25F + r.nextFloat() * 0.75F;
            velocity[i * 3] = (float) Math.cos(a) * out * 1.1F;
            velocity[i * 3 + 1] = 1.6F + r.nextFloat() * 1.4F;
            velocity[i * 3 + 2] = (float) Math.sin(a) * out * 0.6F;
            Vector3f ax = new Vector3f(r.nextFloat() - 0.5F, r.nextFloat() - 0.5F, r.nextFloat() - 0.5F).normalize();
            axis[i * 3] = ax.x;
            axis[i * 3 + 1] = ax.y;
            axis[i * 3 + 2] = ax.z;
            phase[i] = r.nextFloat() * 6.283F;
            delay[i] = r.nextFloat() * 0.25F;
            drift[i] = 0.10F + r.nextFloat() * 0.12F;
            Vector3f bv = new Vector3f(r.nextFloat() - 0.5F, r.nextFloat() * 0.6F, r.nextFloat() - 0.5F).normalize().mul(0.6F + r.nextFloat() * 0.9F);
            blow[i * 3] = bv.x;
            blow[i * 3 + 1] = bv.y;
            blow[i * 3 + 2] = bv.z;
            colors[i] = palette[r.nextInt(palette.length)];
        }
    }

    /** where confetti piece i is at time `since` after it launched (air slows it fast, then it drifts down) */
    private void piece(int i, float since, float flutter, Vector3f out) {
        float k = 2.6F; // air drag
        float travel = (1 - (float) Math.exp(-k * since)) / k;
        out.set(start[i * 3] + velocity[i * 3] * travel,
                start[i * 3 + 1] + velocity[i * 3 + 1] * travel - drift[i] * since,
                start[i * 3 + 2] + velocity[i * 3 + 2] * travel);
        float sway = flutter * 0.03F * Math.min(1.0F, since);
        out.add(sway * (float) Math.sin(since * 3.1F + phase[i]), 0, sway * 0.5F * (float) Math.cos(since * 2.3F + phase[i]));
    }

    private void confetti(PoseStack.Pose p, VertexConsumer out, float t, Config cfg, Vector3f boomAt, float alpha, int light) {
        float w = 0.011F * cfg.size() / 2, h = 0.007F * cfg.size() / 2;
        int a = (int) (Math.max(0, Math.min(1, alpha)) * 255);
        Vector3f pos = new Vector3f(), n = new Vector3f();
        Vector3f c0 = new Vector3f(), c1 = new Vector3f(), c2 = new Vector3f(), c3 = new Vector3f();
        Quaternionf q = new Quaternionf();
        for (int i = 0; i < count; i++) {
            float since = t - POP - delay[i];
            if (since < 0) continue;
            float spin;
            if (t < BOOM) {
                piece(i, since, cfg.flutter(), pos);
                spin = phase[i] + since * (3.0F + 3.0F * cfg.flutter());
            } else { // the explosion throws everything outward
                piece(i, BOOM - POP - delay[i], cfg.flutter(), pos);
                float b = t - BOOM;
                pos.add(blow[i * 3] * b, blow[i * 3 + 1] * b - 1.4F * b * b, blow[i * 3 + 2] * b);
                spin = phase[i] + b * 14.0F;
            }
            q.setAngleAxis(spin, axis[i * 3], axis[i * 3 + 1], axis[i * 3 + 2]);
            q.transform(c0.set(-w, -h, 0)).add(pos);
            q.transform(c1.set(w, -h, 0)).add(pos);
            q.transform(c2.set(w, h, 0)).add(pos);
            q.transform(c3.set(-w, h, 0)).add(pos);
            q.transform(n.set(0, 0, 1));
            int col = colors[i];
            int rr = (col >> 16) & 255, gg = (col >> 8) & 255, bb = col & 255;
            v(out, p, c0, n, rr, gg, bb, a, light);
            v(out, p, c1, n, rr, gg, bb, a, light);
            v(out, p, c2, n, rr, gg, bb, a, light);
            v(out, p, c3, n, rr, gg, bb, a, light);
            n.negate();
            v(out, p, c3, n, rr, gg, bb, a, light);
            v(out, p, c2, n, rr, gg, bb, a, light);
            v(out, p, c1, n, rr, gg, bb, a, light);
            v(out, p, c0, n, rr, gg, bb, a, light);
        }
    }

    private static void v(VertexConsumer out, PoseStack.Pose p, Vector3f at, Vector3f n, int r, int g, int b, int a, int light) {
        out.addVertex(p, at.x, at.y, at.z).setColor(r, g, b, a).setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, n.x, n.y, n.z);
    }

    // ------------------------------------------------------------------ cake on your face (screen overlay)

    private static final int[][] SPLAT_COLORS = {{255, 248, 242}, {244, 143, 177}, {179, 229, 252}, {240, 205, 130}, {240, 98, 146}};
    private static final int[][] SPRINKLE = {{229, 57, 53}, {253, 216, 53}, {67, 160, 71}, {30, 136, 229}, {142, 36, 170}};

    public static boolean splatting(float t) {
        return t >= BOOM + 0.15F && t < BACK + 0.35F;
    }

    public static void overlay(ImDrawList dl, float w, float h, float t) {
        float fade = 1.0F - HolidayAnimation.smooth((t - (BACK - 0.7F)) / 1.0F);
        if (fade <= 0) return;
        Random r = new Random(77);
        for (int i = 0; i < 16; i++) {
            float appear = BOOM + 0.15F + i * 0.012F;
            float sx = (0.08F + r.nextFloat() * 0.84F) * w, sy = (0.1F + r.nextFloat() * 0.75F) * h;
            float rad = (0.045F + r.nextFloat() * 0.075F) * h;
            int[] c = SPLAT_COLORS[r.nextInt(SPLAT_COLORS.length)];
            int lobes = 5 + r.nextInt(3);
            float lobeTurn = r.nextFloat() * 6.28F;
            float dripX = (r.nextFloat() - 0.5F) * rad;
            float dripLen = (0.6F + r.nextFloat() * 1.6F) * rad;
            if (t < appear) continue;
            float s = backOut(Math.min(1.0F, (t - appear) / 0.12F));
            int col = ImColor.rgba(c[0], c[1], c[2], (int) (235 * fade));
            int shade = ImColor.rgba((int) (c[0] * 0.85F), (int) (c[1] * 0.85F), (int) (c[2] * 0.85F), (int) (235 * fade));
            float rr = rad * s;
            // drip running down
            float drip = dripLen * HolidayAnimation.smooth((t - appear - 0.25F) / 1.6F);
            if (drip > 1) {
                dl.addRectFilled(sx + dripX - rr * 0.13F, sy, sx + dripX + rr * 0.13F, sy + drip, shade, rr * 0.13F);
                dl.addCircleFilled(sx + dripX, sy + drip, rr * 0.17F, shade);
            }
            // the splat: a blob with lumpy edges
            dl.addCircleFilled(sx, sy, rr, col);
            for (int k = 0; k < lobes; k++) {
                double a = lobeTurn + Math.PI * 2 * k / lobes;
                float d = rr * (0.75F + 0.25F * (float) Math.sin(k * 2.7F));
                dl.addCircleFilled(sx + (float) Math.cos(a) * d, sy + (float) Math.sin(a) * d, rr * (0.32F + 0.12F * (k % 2)), col);
            }
            // little blobs that flew off it
            for (int k = 0; k < 3; k++) {
                double a = lobeTurn + k * 2.2;
                float d = rr * (1.45F + 0.3F * k);
                dl.addCircleFilled(sx + (float) Math.cos(a) * d, sy + (float) Math.sin(a) * d, rr * 0.12F, col);
            }
            // sprinkles on the frosting
            for (int k = 0; k < 4; k++) {
                int[] sc = SPRINKLE[(i + k) % SPRINKLE.length];
                double a = lobeTurn * 1.3 + k * 1.6;
                float d = rr * 0.5F;
                float px = sx + (float) Math.cos(a) * d, py = sy + (float) Math.sin(a) * d;
                dl.addRectFilled(px - rr * 0.07F, py - rr * 0.025F, px + rr * 0.07F, py + rr * 0.025F,
                        ImColor.rgba(sc[0], sc[1], sc[2], (int) (255 * fade)), rr * 0.025F);
            }
        }
    }

    static float backOut(float u) {
        u = Math.max(0, Math.min(1, u));
        float c = 1.9F;
        float x = u - 1;
        return 1 + (c + 1) * x * x * x + c * x * x;
    }
}
