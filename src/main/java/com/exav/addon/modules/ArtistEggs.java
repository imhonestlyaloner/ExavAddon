package com.exav.addon.modules;

import com.exav.addon.ExavAddon;
import com.exav.addon.holiday.SmoothMesh;
import com.exav.addon.sky.SkyArt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.impl.client.SpotifyHudModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.WorldQuads;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.anubis.client.util.spotify.NowPlaying;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.SkyRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Easter eggs for what's playing in the Spotify HUD:
 *  - Yeat: the sky turns green and golden bells hang in the sky, ringing in a bell pattern on the beat
 *  - Insane Clown Posse: the sky goes dark red and juggalo stuff floats around (clown faces, hatchet men,
 *    hatchets, Faygo bottles)
 * The beat comes from the song's playback position and the BPM setting (Spotify doesn't tell us the tempo).
 */
public final class ArtistEggs extends ClientModule {
    private static volatile ArtistEggs active;

    private final BooleanSetting yeat = addSetting("yeat", new BooleanSetting("Yeat", true));
    private final BooleanSetting icp = addSetting("icp", new BooleanSetting("Insane Clown Posse", true));
    private final NumberSetting skyTint = addSetting("sky-tint", new NumberSetting("Sky Tint", 0.8F, 0.0F, 1.0F).step(0.05F));
    private final NumberSetting amount = addSetting("amount", new NumberSetting("Amount", 1.0F, 0.3F, 2.0F).step(0.1F));
    private final NumberSetting yeatBpm = addSetting("yeat-bpm", new NumberSetting("Yeat BPM", 150.0F, 60.0F, 200.0F).step(1.0F));
    private final NumberSetting beatOffset = addSetting("beat-offset", new NumberSetting("Beat Offset (ms)", 0.0F, -500.0F, 500.0F).step(10.0F));
    private final BooleanSetting bellSounds = addSetting("bell-sounds", new BooleanSetting("Bell Sounds", false));
    private final NumberSetting bellVolume = addSetting("bell-volume", new NumberSetting("Bell Volume", 0.5F, 0.0F, 1.0F).step(0.05F));
    private final ModeSetting preview = addSetting("preview", new ModeSetting("Preview", "Off", "Off", "Yeat", "Insane Clown Posse"));

    private static final int YEAT_SKY = 0x22D15C, YEAT_FOG = 0x0F6B33, ICP_SKY = 0x5B0A12, ICP_FOG = 0x24060A;

    private float yeatLevel, icpLevel;
    private long lastFrame;
    private long lastEighth = Long.MIN_VALUE;

    private static SmoothMesh bell, greenSparkle;

    public ArtistEggs() {
        super("Artist Easter Eggs", "Yeat turns the sky green with bells on the beat; Insane Clown Posse fills the sky with juggalo pictures.", ExavAddon.CATEGORY);
        yeatBpm.visibleWhen(yeat::get);
        beatOffset.visibleWhen(yeat::get);
        bellSounds.visibleWhen(yeat::get);
        bellVolume.visibleWhen(() -> yeat.get() && bellSounds.get());
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
        yeatLevel = icpLevel = 0;
    }

    // ---------------------------------------------------------------- who's playing

    private static String artistKey(NowPlaying now) {
        return now == null || now.artist() == null ? "" : now.artist().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private NowPlaying playing() {
        SpotifyHudModule spotify = SpotifyHudModule.active();
        NowPlaying now = spotify == null ? null : spotify.nowPlaying();
        return now != null && now.playing() ? now : null;
    }

    private boolean yeatOn() {
        if (preview.is("Yeat")) return true;
        return yeat.get() && artistKey(playing()).contains("yeat");
    }

    private boolean icpOn() {
        if (preview.is("Insane Clown Posse")) return true;
        return icp.get() && artistKey(playing()).contains("insaneclownposse");
    }

    @EventHandler
    public void onTick(TickEvent event) {
        // fade in over 2s, out over 1.5s
        yeatLevel = step(yeatLevel, yeatOn(), 0.05F / 2.0F, 0.05F / 1.5F);
        icpLevel = step(icpLevel, icpOn(), 0.05F / 2.0F, 0.05F / 1.5F);
    }

    private static float step(float level, boolean on, float up, float down) {
        return on ? Math.min(1, level + up) : Math.max(0, level - down);
    }

    // ---------------------------------------------------------------- sky + fog colour (called from mixins)

    public static void tintSky(SkyRenderState state) {
        ArtistEggs m = active;
        if (m == null) return;
        float y = m.yeatLevel * m.skyTint.get(), i = m.icpLevel * m.skyTint.get();
        if (y > 0) {
            state.skyColor = blend(state.skyColor, YEAT_SKY, y);
            state.sunriseAndSunsetColor = blend(state.sunriseAndSunsetColor, YEAT_SKY, y * 0.7F);
        }
        if (i > 0) {
            state.skyColor = blend(state.skyColor, ICP_SKY, i);
            state.sunriseAndSunsetColor = blend(state.sunriseAndSunsetColor, 0xB0141E, i * 0.7F);
        }
    }

    public static void tintFog(Vector4f fog) {
        ArtistEggs m = active;
        if (m == null || fog == null) return;
        float y = m.yeatLevel * m.skyTint.get(), i = m.icpLevel * m.skyTint.get();
        if (y > 0) mixInto(fog, YEAT_FOG, y);
        if (i > 0) mixInto(fog, ICP_FOG, i);
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

    // ---------------------------------------------------------------- the sky objects

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (yeatLevel <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        build();
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        Vector3f camera = new Vector3f((float) cam.x, (float) cam.y, (float) cam.z);
        float distance = Math.max(48.0F, Math.min(220.0F, mc.options.getEffectiveRenderDistance() * 16 * 0.8F));
        float time = (System.nanoTime() % 3_600_000_000_000L) / 1.0E9F;
        List<Placed> list = new ArrayList<>();
        if (yeatLevel > 0) yeatScene(list, time, distance);
        // far ones first so nearer ones draw over them
        list.sort((a, b) -> Float.compare(b.dist, a.dist));
        WorldQuads quads = WorldQuads.layer(false);
        for (Placed p : list) draw(quads, p, camera);
    }

    /** one object in the sky: mesh, where (direction from you, distance), how it's turned and sized */
    private record Placed(SmoothMesh mesh, Vector3f dir, float dist, Quaternionf local, float scale, int glowRgb, float glow, float alpha) {
    }

    private static Vector3f direction(float azimuth, float elevation) {
        double c = Math.cos(elevation);
        return new Vector3f((float) (c * Math.sin(azimuth)), (float) Math.sin(elevation), (float) (c * Math.cos(azimuth)));
    }

    // Yeat: six bells around you, rung in a trap-bell pattern on eighth notes
    private static final int[] BELL_PATTERN = {0, -1, 2, 1, -1, 3, 0, -1, 4, -1, 2, 3, -1, 1, 5, -1};
    private static final int[] BELL_NOTES = {0, 3, 5, 7, 10, 12}; // minor pentatonic, like the bell melodies

    private void yeatScene(List<Placed> list, float time, float distance) {
        float level = HolidayEase.smooth(yeatLevel);
        // beat from the song position
        NowPlaying now = playing();
        float eighths = Float.NaN;
        if (now != null) {
            long pos = now.positionAt(System.currentTimeMillis()) + (long) beatOffset.get();
            eighths = pos / 60000.0F * yeatBpm.get() * 2.0F;
        } else if (preview.is("Yeat")) {
            eighths = time * yeatBpm.get() / 30.0F;
        }
        int step = Float.isNaN(eighths) ? -1 : Math.floorMod((int) Math.floor(eighths), 16);
        float since = Float.isNaN(eighths) ? 9 : (eighths - (float) Math.floor(eighths)) * 30.0F / yeatBpm.get(); // seconds since the eighth
        // bell sound on each struck eighth
        if (!Float.isNaN(eighths) && bellSounds.get() && yeatLevel > 0.6F) {
            long e = (long) Math.floor(eighths);
            if (e != lastEighth) {
                lastEighth = e;
                int bellIndex = BELL_PATTERN[step];
                if (bellIndex >= 0) {
                    float pitch = (float) Math.pow(2, (BELL_NOTES[bellIndex] - 6) / 12.0);
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), pitch, bellVolume.get()));
                }
            }
        }
        int bells = 6;
        for (int i = 0; i < bells; i++) {
            float az = (float) (Math.PI * 2 * i / bells) + time * 0.02F;
            float el = (float) Math.toRadians(24 + 8 * Math.sin(i * 1.7));
            // which bell was hit most recently, and how long ago
            float ring = 0;
            if (step >= 0) {
                for (int back = 0; back < 4; back++) {
                    int idx = BELL_PATTERN[Math.floorMod(step - back, 16)];
                    if (idx == i) {
                        float ago = since + back * 30.0F / yeatBpm.get();
                        ring = (float) Math.exp(-ago * 5.0F);
                        break;
                    }
                }
            }
            float swing = (float) Math.toRadians(4 * Math.sin(time * 1.3F + i) + 28 * ring * Math.sin(ring * 9));
            Quaternionf local = new Quaternionf().rotationZ(swing).rotateY(time * 0.2F + i);
            float size = distance * 0.016F * amount.get() * level;
            list.add(new Placed(bell, direction(az, el), distance, local, size, 0x2BFF6A, ring, 1));
            // green shock ring when it's struck
            if (ring > 0.05F) {
                float grow = 1 - ring;
                list.add(new Placed(ringMesh(), direction(az, el - (float) Math.toRadians(1.5)), distance * 1.01F,
                        new Quaternionf(), size * (1.0F + 4.0F * grow), 0x2BFF6A, 1, ring * 0.8F));
            }
        }
        // drifting green sparkles
        int sparkles = (int) (40 * amount.get());
        for (int i = 0; i < sparkles; i++) {
            float az = i * 2.39996F + time * (0.01F + 0.01F * (i % 3));
            float el = (float) Math.toRadians(8 + (i * 37 % 60) + 3 * Math.sin(time * 0.5F + i));
            float twinkle = 0.6F + 0.4F * (float) Math.sin(time * 3.0F + i * 1.3F);
            list.add(new Placed(greenSparkle, direction(az, el), distance * 0.98F, new Quaternionf().rotationZ(time * 0.5F + i),
                    distance * 0.004F * twinkle * level, 0x2BFF6A, 0.6F, 1));
        }
    }

    private static SmoothMesh ring;

    private static SmoothMesh ringMesh() {
        if (ring == null) {
            SmoothMesh.Builder b = new SmoothMesh.Builder();
            List<Vector3f> path = new ArrayList<>();
            for (int i = 0; i <= 48; i++) {
                double a = Math.PI * 2 * i / 48;
                path.add(new Vector3f((float) Math.cos(a) * 6, (float) Math.sin(a) * 6 - 5, 0));
            }
            com.exav.addon.holiday.Shapes.tube(b, path, s -> 0.35F, (s, a) -> 0x2BFF6A, 6);
            ring = b.build();
        }
        return ring;
    }

    // ---------------------------------------------------------------- Insane Clown Posse: your pictures floating in the sky

    /** hatchet man logo, Riddle Box clown, the wraith, the two masks */
    private static final Identifier[] ICP_IMAGES = {
            Identifier.fromNamespaceAndPath("exav-addon", "textures/sky/icp_hatchetman.png"),
            Identifier.fromNamespaceAndPath("exav-addon", "textures/sky/icp_clown.png"),
            Identifier.fromNamespaceAndPath("exav-addon", "textures/sky/icp_wraith.png"),
            Identifier.fromNamespaceAndPath("exav-addon", "textures/sky/icp_masks.png"),
    };

    /** Called from the world render (camera-relative pose): draws the floating ICP pictures. */
    public static void submitImages(PoseStack pose, SubmitNodeCollector collector) {
        ArtistEggs m = active;
        if (m == null || m.icpLevel <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float level = HolidayEase.smooth(m.icpLevel);
        // closer than the Yeat bells so they stay out of the fog
        float distance = Math.max(40.0F, Math.min(120.0F, mc.options.getEffectiveRenderDistance() * 16 * 0.5F));
        float time = (System.nanoTime() % 3_600_000_000_000L) / 1.0E9F;
        int count = (int) (24 * m.amount.get());
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

    // ---------------------------------------------------------------- drawing with Anubis's world quads

    private static final Vector3f LIGHT = new Vector3f(0.3F, 1.0F, 0.5F).normalize();

    private static void draw(WorldQuads quads, Placed p, Vector3f camera) {
        if (p.scale <= 0.0001F) return;
        // face the camera: the object's +Z points back at you, its +Y stays upright
        Vector3f back = new Vector3f(p.dir).negate();
        Vector3f right = new Vector3f(0, 1, 0).cross(back);
        if (right.lengthSquared() < 1e-6F) right.set(1, 0, 0);
        right.normalize();
        Vector3f up = new Vector3f(back).cross(right).normalize();
        Matrix3f basis = new Matrix3f(right, up, back).rotate(p.local);
        Vector3f centre = new Vector3f(p.dir).mul(p.dist).add(camera);
        float[] pos = p.mesh.positions(), nrm = p.mesh.normals();
        int[] col = p.mesh.colors();
        int n = p.mesh.quads();
        // sort this object's quads back to front (the quad pipeline doesn't write depth)
        float[] depth = new float[n];
        Integer[] order = new Integer[n];
        Vector3f v = new Vector3f(), nn = new Vector3f();
        float[] world = new float[n * 12];
        boolean[] keep = new boolean[n];
        for (int q = 0; q < n; q++) {
            float dsum = 0;
            for (int k = 0; k < 4; k++) {
                int i = (q * 4 + k) * 3;
                basis.transform(v.set(pos[i], pos[i + 1], pos[i + 2])).mul(p.scale).add(centre);
                world[q * 12 + k * 3] = v.x;
                world[q * 12 + k * 3 + 1] = v.y;
                world[q * 12 + k * 3 + 2] = v.z;
                dsum += v.distanceSquared(camera);
            }
            int i0 = q * 4 * 3;
            basis.transform(nn.set(nrm[i0], nrm[i0 + 1], nrm[i0 + 2]));
            // skip faces pointing away from you
            keep[q] = nn.dot(camera.x - world[q * 12], camera.y - world[q * 12 + 1], camera.z - world[q * 12 + 2]) > 0;
            depth[q] = dsum;
            order[q] = q;
        }
        Arrays.sort(order, (a, b) -> Float.compare(depth[b], depth[a]));
        int alpha = (int) (Math.max(0, Math.min(1, p.alpha)) * 255);
        for (int q : order) {
            if (!keep[q]) continue;
            int i0 = q * 4 * 3;
            basis.transform(nn.set(nrm[i0], nrm[i0 + 1], nrm[i0 + 2]));
            float shade = 0.55F + 0.45F * Math.max(0, nn.dot(LIGHT));
            int c = col[q * 4];
            float r = ((c >> 16) & 255) * shade, g = ((c >> 8) & 255) * shade, b = (c & 255) * shade;
            if (p.glow > 0) { // light up toward the glow colour (bells when they ring)
                r += (((p.glowRgb >> 16) & 255) - r) * p.glow * 0.7F;
                g += (((p.glowRgb >> 8) & 255) - g) * p.glow * 0.7F;
                b += ((p.glowRgb & 255) - b) * p.glow * 0.7F;
            }
            int argb = alpha << 24 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
            int w = q * 12;
            quads.quad(world[w], world[w + 1], world[w + 2], world[w + 3], world[w + 4], world[w + 5],
                    world[w + 6], world[w + 7], world[w + 8], world[w + 9], world[w + 10], world[w + 11], argb);
        }
    }

    private static int clamp(float x) {
        return Math.max(0, Math.min(255, (int) x));
    }

    private static void build() {
        if (bell != null) return;
        bell = SkyArt.bell();
        greenSparkle = SkyArt.sparkle(0x7CFFA0);
    }

    /** tiny easing helper */
    private static final class HolidayEase {
        static float smooth(float u) {
            u = Math.max(0, Math.min(1, u));
            return u * u * (3 - 2 * u);
        }
    }
}
