package com.exav.addon.sky;

import com.exav.addon.modules.ArtistEggs;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.anubis.client.event.EventBus;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.util.spotify.NowPlaying;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.SkyRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The easter eggs that can't be turned off: Insane Clown Posse (any ICP song) and Christmas (any Christmas song).
 * They run whether or not the Artist Easter Eggs module is on. Christmas beats every other sky, including the
 * Yeat/ICP ones and Anubis's Atmosphere (see the Atmosphere mixin).
 */
public final class AlwaysEggs {
    private static final AlwaysEggs INSTANCE = new AlwaysEggs();
    private static float icpLevel, christmasLevel;
    private static long christmasStart;
    private static float lastChristmasT = -1;

    private static final int XMAS_SKY = 0x1B2B4C, XMAS_SUNRISE = 0x34507E, XMAS_FOG = 0x7C8CA6;
    private static final int ICP_SKY = 0x5B0A12, ICP_FOG = 0x24060A;

    private AlwaysEggs() {
    }

    public static void init() {
        EventBus.INSTANCE.register(INSTANCE);
    }

    /** Christmas is on: every other sky (Anubis Atmosphere included) steps aside */
    public static boolean christmas() {
        return christmasLevel > 0;
    }

    public static float christmasLevel() {
        return christmasLevel;
    }

    @EventHandler
    public void onTick(TickEvent event) {
        NowPlaying now = SongMatch.playing();
        boolean xmas = SongMatch.christmas(now) || ArtistEggs.previewing("Christmas");
        boolean icp = SongMatch.icp(now) || ArtistEggs.previewing("Insane Clown Posse");
        if (xmas && christmasLevel <= 0) christmasStart = System.nanoTime();
        christmasLevel = xmas ? Math.min(1, christmasLevel + 0.05F / 1.5F) : Math.max(0, christmasLevel - 0.05F / 1.5F);
        icpLevel = icp ? Math.min(1, icpLevel + 0.05F / 2.0F) : Math.max(0, icpLevel - 0.05F / 1.5F);
        if (christmasLevel <= 0) {
            lastChristmasT = -1;
            return;
        }
        // presents: a little jingle when one's thrown (the burst is silent)
        float t = christmasTime();
        float since = t - ChristmasSky.FIRST_PRESENT;
        if (lastChristmasT >= 0 && since >= 0) {
            float prevSince = lastChristmasT - ChristmasSky.FIRST_PRESENT;
            var sounds = Minecraft.getInstance().getSoundManager();
            if (Math.floor(since / ChristmasSky.PRESENT_EVERY) != Math.floor(prevSince / ChristmasSky.PRESENT_EVERY) || (prevSince < 0)) {
                sounds.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2F, 0.5F));
            }
        }
        lastChristmasT = t;
    }

    static float christmasTime() {
        return (System.nanoTime() - christmasStart) / 1.0E9F;
    }

    // ---------------------------------------------------------------- drawing (from the entity submit mixin)

    public static void submit(PoseStack pose, SubmitNodeCollector collector) {
        if (Minecraft.getInstance().level == null) return;
        if (christmasLevel > 0) {
            ChristmasSky.draw(pose, collector, christmasTime(), smooth(christmasLevel));
        } else if (icpLevel > 0) {
            icpImages(pose, collector, smooth(icpLevel));
        }
    }

    // ---------------------------------------------------------------- sky and fog colour (from the mixins)

    public static void tintSky(SkyRenderState state) {
        float yeat = ArtistEggs.yeatTint(), icp = icpLevel * ArtistEggs.skyTintStrength(), xmas = christmasLevel;
        if (yeat > 0) {
            state.skyColor = blend(state.skyColor, ArtistEggs.YEAT_SKY, yeat);
            state.sunriseAndSunsetColor = blend(state.sunriseAndSunsetColor, ArtistEggs.YEAT_SKY, yeat * 0.7F);
        }
        if (icp > 0) {
            state.skyColor = blend(state.skyColor, ICP_SKY, icp);
            state.sunriseAndSunsetColor = blend(state.sunriseAndSunsetColor, 0xB0141E, icp * 0.7F);
        }
        if (xmas > 0) { // a snowy winter night, over everything else
            state.skyColor = blend(state.skyColor, XMAS_SKY, xmas);
            state.sunriseAndSunsetColor = blend(state.sunriseAndSunsetColor, XMAS_SUNRISE, xmas);
            state.rainBrightness = Math.min(state.rainBrightness, 1.0F - 0.3F * xmas);
            state.starBrightness = Math.max(state.starBrightness, 0.8F * xmas);
        }
    }

    public static void tintFog(Vector4f fog) {
        if (fog == null) return;
        float yeat = ArtistEggs.yeatTint(), icp = icpLevel * ArtistEggs.skyTintStrength(), xmas = christmasLevel;
        if (yeat > 0) mixInto(fog, ArtistEggs.YEAT_FOG, yeat);
        if (icp > 0) mixInto(fog, ICP_FOG, icp);
        if (xmas > 0) mixInto(fog, XMAS_FOG, xmas);
    }

    private static void mixInto(Vector4f fog, int rgb, float t) {
        fog.x += (((rgb >> 16) & 255) / 255.0F - fog.x) * t;
        fog.y += (((rgb >> 8) & 255) / 255.0F - fog.y) * t;
        fog.z += ((rgb & 255) / 255.0F - fog.z) * t;
    }

    private static int blend(int argb, int rgb, float t) {
        int a = argb & 0xFF000000;
        int r = (int) (((argb >> 16) & 255) * (1 - t) + ((rgb >> 16) & 255) * t);
        int g = (int) (((argb >> 8) & 255) * (1 - t) + ((rgb >> 8) & 255) * t);
        int b = (int) ((argb & 255) * (1 - t) + (rgb & 255) * t);
        return a | r << 16 | g << 8 | b;
    }

    private static float smooth(float u) {
        u = Math.max(0, Math.min(1, u));
        return u * u * (3 - 2 * u);
    }

    private static Vector3f direction(float azimuth, float elevation) {
        double c = Math.cos(elevation);
        return new Vector3f((float) (c * Math.sin(azimuth)), (float) Math.sin(elevation), (float) (c * Math.cos(azimuth)));
    }

    // ---------------------------------------------------------------- Insane Clown Posse: your pictures floating in the sky

    /** hatchet man logo, Riddle Box clown, the wraith, the two masks */
    private static final Identifier[] ICP_IMAGES = {
            Identifier.fromNamespaceAndPath("exav-addon", "textures/sky/icp_hatchetman.png"),
            Identifier.fromNamespaceAndPath("exav-addon", "textures/sky/icp_clown.png"),
            Identifier.fromNamespaceAndPath("exav-addon", "textures/sky/icp_wraith.png"),
            Identifier.fromNamespaceAndPath("exav-addon", "textures/sky/icp_masks.png"),
    };

    /** draws the floating ICP pictures (camera-relative pose) */
    private static void icpImages(PoseStack pose, SubmitNodeCollector collector, float level) {
        Minecraft mc = Minecraft.getInstance();
        // closer than the Yeat bells so they stay out of the fog
        float distance = Math.max(40.0F, Math.min(120.0F, mc.options.getEffectiveRenderDistance() * 16 * 0.5F));
        float time = (System.nanoTime() % 3_600_000_000_000L) / 1.0E9F;
        int count = (int) (24 * ArtistEggs.amount());
        List<float[]>[] perImage = new List[ICP_IMAGES.length];
        for (int k = 0; k < perImage.length; k++) perImage[k] = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int kind = i % ICP_IMAGES.length;
            float speed = (i % 2 == 0 ? 1 : -1) * (0.012F + 0.008F * (i % 5));
            float az = i * 2.39996F + time * speed;
            float el = (float) Math.toRadians(10 + (i * 41 % 50) + 4 * Math.sin(time * 0.4F + i * 0.9F));
            float dist = distance * (0.85F + 0.15F * ((i * 13) % 7) / 7.0F);
            float size = dist * 0.17F * level * (0.8F + 0.4F * ((i * 7) % 5) / 5.0F);
            float roll, bob;
            switch (kind) {
                case 0 -> { // hatchet man: running bounce
                    roll = 8 * (float) Math.sin(time * 6 + i);
                    bob = 0.04F * Math.abs((float) Math.sin(time * 6 + i));
                }
                case 1 -> { // clown: rocking side to side, grinning at you
                    roll = 14 * (float) Math.sin(time * 1.1F + i);
                    bob = 0.02F * (float) Math.sin(time * 0.9F + i);
                }
                case 2 -> { // wraith: slow eerie float up and down
                    roll = 4 * (float) Math.sin(time * 0.5F + i);
                    bob = 0.08F * (float) Math.sin(time * 0.6F + i);
                }
                default -> { // masks: swaying
                    roll = 10 * (float) Math.sin(time * 0.8F + i * 1.3F);
                    bob = 0.03F * (float) Math.sin(time * 1.2F + i);
                }
            }
            Vector3f dir = direction(az, el);
            Vector3f centre = new Vector3f(dir).mul(dist).add(0, bob * size * 4, 0);
            perImage[kind].add(new float[]{centre.x, centre.y, centre.z, size, (float) Math.toRadians(roll), dist});
        }
        int alpha = (int) (255 * level);
        for (int k = 0; k < ICP_IMAGES.length; k++) {
            List<float[]> items = perImage[k];
            if (items.isEmpty()) continue;
            items.sort((a, b) -> Float.compare(b[5], a[5])); // far first
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(ICP_IMAGES[k]), (p, out) -> {
                for (float[] it : items) billboard(p, out, it[0], it[1], it[2], it[3] / 2, it[4], alpha);
            });
        }
    }

    /** a square picture centred at (x,y,z) relative to the camera, facing the camera, turned by roll */
    private static void billboard(PoseStack.Pose p, VertexConsumer out, float x, float y, float z, float half, float roll, int alpha) {
        Vector3f back = new Vector3f(-x, -y, -z).normalize();
        Vector3f right = new Vector3f(0, 1, 0).cross(back);
        if (right.lengthSquared() < 1e-6F) right.set(1, 0, 0);
        right.normalize();
        Vector3f up = new Vector3f(back).cross(right).normalize();
        float c = (float) Math.cos(roll), s = (float) Math.sin(roll);
        Vector3f r = new Vector3f(right).mul(c).fma(s, up).mul(half);
        Vector3f u = new Vector3f(up).mul(c).fma(-s, right).mul(half);
        float[][] corners = {{-1, -1, 0, 1}, {1, -1, 1, 1}, {1, 1, 1, 0}, {-1, 1, 0, 0}};
        for (float[] k : corners) {
            out.addVertex(p, x + r.x * k[0] + u.x * k[1], y + r.y * k[0] + u.y * k[1], z + r.z * k[0] + u.z * k[1])
                    .setColor(255, 255, 255, alpha)
                    .setUv(k[2], k[3])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(0xF000F0)
                    .setNormal(p, back.x, back.y, back.z);
        }
    }

}
