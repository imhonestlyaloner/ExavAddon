package com.exav.addon.modules;

import com.exav.addon.ExavAddon;
import com.exav.addon.holiday.SmoothMesh;
import com.exav.addon.sky.SkyArt;
import java.util.ArrayList;
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
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Easter eggs for what's playing in the Spotify HUD:
 *  - Yeat ("Get Busy" style): enormous, realistic metal church bells hang in the sky and ring on the beat with
 *    real weight: they swing and settle, the clapper lags inside, the metal shudders, and the hit kicks the camera
 *  - Insane Clown Posse: the sky goes dark red and your juggalo pictures float around
 * The beat comes from the song's playback position and the BPM setting (Spotify doesn't tell us the tempo).
 */
public final class ArtistEggs extends ClientModule {
    private static volatile ArtistEggs active;

    private final BooleanSetting yeat = addSetting("yeat", new BooleanSetting("Yeat", true));
    private final BooleanSetting icp = addSetting("icp", new BooleanSetting("Insane Clown Posse", true));
    private final NumberSetting skyTint = addSetting("sky-tint", new NumberSetting("Sky Tint", 0.6F, 0.0F, 1.0F).step(0.05F));
    private final NumberSetting amount = addSetting("amount", new NumberSetting("Amount", 1.0F, 0.3F, 2.0F).step(0.1F));
    private final NumberSetting yeatBpm = addSetting("yeat-bpm", new NumberSetting("Yeat BPM", 150.0F, 60.0F, 200.0F).step(1.0F));
    private final NumberSetting beatOffset = addSetting("beat-offset", new NumberSetting("Beat Offset (ms)", 0.0F, -500.0F, 500.0F).step(10.0F));
    private final ModeSetting bellMetal = addSetting("bell-metal", new ModeSetting("Bell Metal", "Mixed", "Mixed", "Gold", "Bronze", "Dark Iron"));
    private final NumberSetting bellSize = addSetting("bell-size", new NumberSetting("Bell Size", 1.0F, 0.4F, 2.0F).step(0.05F));
    private final BooleanSetting impact = addSetting("impact", new BooleanSetting("Impact Shake + Zoom", true));
    private final NumberSetting impactStrength = addSetting("impact-strength", new NumberSetting("Impact Strength", 1.0F, 0.1F, 2.0F).step(0.1F));
    private final BooleanSetting bellSounds = addSetting("bell-sounds", new BooleanSetting("Bell Sounds", false));
    private final NumberSetting bellVolume = addSetting("bell-volume", new NumberSetting("Bell Volume", 0.5F, 0.0F, 1.0F).step(0.05F));
    private final ModeSetting preview = addSetting("preview", new ModeSetting("Preview", "Off", "Off", "Yeat", "Insane Clown Posse"));

    /** moody, not neon */
    private static final int YEAT_SKY = 0x5E8F67, YEAT_FOG = 0x2F4A36, ICP_SKY = 0x5B0A12, ICP_FOG = 0x24060A;

    private float yeatLevel, icpLevel;
    private long lastFrame;
    private long lastEighth = Long.MIN_VALUE;

    private static SmoothMesh bellMesh, clapperMesh;

    public ArtistEggs() {
        super("Artist Easter Eggs", "Yeat hangs giant ringing church bells in the sky on the beat; Insane Clown Posse fills the sky with juggalo pictures.", ExavAddon.CATEGORY);
        yeatBpm.visibleWhen(yeat::get);
        beatOffset.visibleWhen(yeat::get);
        bellSounds.visibleWhen(yeat::get);
        bellVolume.visibleWhen(() -> yeat.get() && bellSounds.get());
        bellMetal.visibleWhen(yeat::get);
        bellSize.visibleWhen(yeat::get);
        impact.visibleWhen(yeat::get);
        impactStrength.visibleWhen(() -> yeat.get() && impact.get());
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

    // ---------------------------------------------------------------- Yeat: giant bells

    /**
     * Where each bell hangs: azimuth (deg), elevation (deg), distance (fraction of the view distance), height in
     * blocks, metal (0 gold, 1 bronze, 2 dark iron), how fast it turns. Some are huge and far, some hover oddly close
     * or low behind the hills, so they feel out of place.
     */
    private static final float[][] BELLS = {
            {20, 30, 0.80F, 70, 0, 0.010F},
            {-75, 7, 0.90F, 60, 1, -0.006F},
            {150, 48, 0.62F, 46, 2, 0.008F},
            {-150, 16, 0.42F, 24, 0, -0.012F},
            {85, 22, 0.70F, 38, 1, 0.009F},
            {-30, 60, 0.55F, 30, 2, -0.007F},
    };
    private static final int[] METALS = {0xD9A93C, 0xB0703A, 0x50555C}; // gold, bronze, dark iron
    /** which bell rings on each eighth note of a 2-bar loop (-1 = rest) */
    private static final int[] BELL_PATTERN = {0, -1, 1, -1, 2, 0, -1, 3, 0, -1, 1, 2, -1, 3, 1, -1};

    // per-bell motion: swing angle + speed, clapper angle (relative) + speed, shudder, glint
    private final float[] swing = new float[BELLS.length], swingVel = new float[BELLS.length];
    private final float[] clap = new float[BELLS.length], clapVel = new float[BELLS.length];
    private final float[] shudder = new float[BELLS.length], glint = new float[BELLS.length];
    private final int[] swingDir = new int[BELLS.length];

    private static float shake, punch;
    private static long shakeSeed;

    private int bellCount() {
        return Math.max(1, Math.min(BELLS.length, Math.round(4 * amount.get())));
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        long nowNs = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1F, (nowNs - lastFrame) / 1.0E9F);
        lastFrame = nowNs;
        shake *= (float) Math.exp(-dt * 9);
        punch *= (float) Math.exp(-dt * 7);
        if (yeatLevel <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (bellMesh == null) {
            bellMesh = SkyArt.bell();
            clapperMesh = SkyArt.clapper();
        }
        float time = (nowNs % 3_600_000_000_000L) / 1.0E9F;
        int count = bellCount();
        strikes(time, count);
        physics(dt, count);

        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        Vector3f camera = new Vector3f((float) cam.x, (float) cam.y, (float) cam.z);
        float view = Math.max(64.0F, Math.min(240.0F, mc.options.getEffectiveRenderDistance() * 16 * 0.9F));
        float day = Math.max(0.22F, 1.0F - mc.level.getSkyDarken() / 11.0F);
        float level = HolidayEase.smooth(yeatLevel);
        List<Bell> bells = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            float[] cfg = BELLS[i];
            float dist = view * cfg[2];
            Vector3f pivot = direction((float) Math.toRadians(cfg[0]), (float) Math.toRadians(cfg[1])).mul(dist).add(camera);
            // a little extra hover bob, and the shudder after a hit
            pivot.y += (float) Math.sin(time * 0.35F + i * 1.7F) * cfg[3] * 0.03F;
            float jitter = shudder[i] * 0.012F * (float) Math.sin(time * 95 + i);
            float height = cfg[3] * bellSize.get() * level;
            float scale = height / 10.0F * (1 + jitter);
            float yaw = time * cfg[5] * 6.0F + i;
            Quaternionf rot = new Quaternionf().rotationY(yaw).rotateX(swing[i]).rotateZ(jitter * 0.6F);
            int metal = bellMetal.is("Gold") ? 0 : bellMetal.is("Bronze") ? 1 : bellMetal.is("Dark Iron") ? 2 : (int) cfg[4];
            bells.add(new Bell(pivot, rot, new Quaternionf(rot).rotateX(clap[i]), scale, METALS[metal], glint[i], camera.distance(pivot)));
        }
        bells.sort((a, b) -> Float.compare(b.dist, a.dist)); // far bells first
        WorldQuads quads = WorldQuads.layer(false);
        for (Bell b : bells) drawBell(quads, b, camera, day, skyTint.get() * level);
    }

    private record Bell(Vector3f pivot, Quaternionf rot, Quaternionf clapperRot, float scale, int metal, float glint, float dist) {
    }

    /** ring bells on the beat, from the song's playback position */
    private void strikes(float time, int count) {
        NowPlaying now = playing();
        float eighths;
        if (now != null) {
            long pos = now.positionAt(System.currentTimeMillis()) + (long) beatOffset.get();
            eighths = pos / 60000.0F * yeatBpm.get() * 2.0F;
        } else if (preview.is("Yeat")) {
            eighths = time * yeatBpm.get() / 30.0F;
        } else {
            return;
        }
        long e = (long) Math.floor(eighths);
        if (e == lastEighth) return;
        boolean first = lastEighth == Long.MIN_VALUE;
        lastEighth = e;
        if (first) return;
        int idx = BELL_PATTERN[(int) Math.floorMod(e, 16)];
        if (idx < 0) return;
        int bell = idx % count;
        // a push in alternating directions so it rocks back and forth, bigger bells move less
        swingDir[bell] = swingDir[bell] >= 0 ? -1 : 1;
        float heavy = 40.0F / BELLS[bell][3];
        swingVel[bell] += swingDir[bell] * 1.6F * Math.min(1.5F, heavy);
        clapVel[bell] -= swingDir[bell] * 4.0F;   // the clapper is thrown the other way and lags behind
        shudder[bell] = 1;
        glint[bell] = 1;
        if (impact.get() && yeatLevel > 0.5F) {
            // nearer, bigger bells hit harder
            float near = BELLS[bell][3] / (BELLS[bell][2] * 60.0F);
            shake = Math.min(1.5F, shake + 0.55F * near * impactStrength.get());
            punch = Math.min(1.0F, punch + 0.5F * near * impactStrength.get());
            shakeSeed = e;
        }
        if (bellSounds.get() && yeatLevel > 0.5F) {
            float pitch = Math.max(0.5F, Math.min(1.4F, 1.6F - BELLS[bell][3] / 60.0F));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BELL_BLOCK, pitch, bellVolume.get()));
        }
    }

    /** heavy pendulum swing that settles, a clapper that lags and knocks against the rim, decaying shudder/glint */
    private void physics(float dt, int count) {
        for (int i = 0; i < count; i++) {
            float period = 1.4F + BELLS[i][3] / 50.0F;      // big bells swing slower
            float k = (float) Math.pow(2 * Math.PI / period, 2);
            float accel = -k * swing[i] - 0.9F * swingVel[i];
            swingVel[i] += accel * dt;
            swing[i] += swingVel[i] * dt;
            swing[i] = Math.max(-0.5F, Math.min(0.5F, swing[i]));
            // clapper: its own pendulum, shoved by the bell's acceleration, stopped by the rim
            float clapAccel = -k * 1.6F * clap[i] - 1.4F * clapVel[i] - accel * 0.9F;
            clapVel[i] += clapAccel * dt;
            clap[i] += clapVel[i] * dt;
            if (Math.abs(clap[i]) > 0.42F) {
                clap[i] = Math.signum(clap[i]) * 0.42F;
                clapVel[i] *= -0.3F;
            }
            shudder[i] *= (float) Math.exp(-dt * 7);
            glint[i] *= (float) Math.exp(-dt * 5);
        }
    }

    // metal shading: diffuse + sky/ground reflection + sharp sun highlight + bright edges
    private static final Vector3f SUN = new Vector3f(0.35F, 0.85F, 0.4F).normalize();

    private static void drawBell(WorldQuads quads, Bell b, Vector3f camera, float day, float tint) {
        List<float[]> faces = new ArrayList<>(2600);
        collect(faces, bellMesh, b.pivot, b.rot, b.scale, new Vector3f(0, 0, 0), camera);
        // the clapper hangs from just under the crown, inside the bell
        Vector3f clapPivot = b.rot.transform(new Vector3f(0, -2.2F * b.scale, 0)).add(b.pivot);
        collect(faces, clapperMesh, clapPivot, b.clapperRot, b.scale, new Vector3f(), camera);
        faces.sort((x, y) -> Float.compare(y[16], x[16])); // back to front
        float mr = ((b.metal >> 16) & 255) / 255.0F, mg = ((b.metal >> 8) & 255) / 255.0F, mb = (b.metal & 255) / 255.0F;
        // what the metal reflects: sky above (a bit of the green tint), ground below, both darker at night
        float skyR = (0.55F + (0.37F - 0.55F) * tint) * day, skyG = (0.68F + (0.56F - 0.68F) * tint) * day, skyB = (0.88F + (0.40F - 0.88F) * tint) * day;
        float gR = 0.22F * day, gG = 0.21F * day, gB = 0.17F * day;
        Vector3f n = new Vector3f(), v = new Vector3f(), r = new Vector3f();
        for (float[] f : faces) {
            n.set(f[12], f[13], f[14]);
            v.set(camera.x - f[0], camera.y - f[1], camera.z - f[2]).normalize();
            float tone = f[15];                                   // 1 outside, darker inside / on bands
            float nl = Math.max(0, n.dot(SUN));
            float nv = Math.max(0, n.dot(v));
            // reflection direction
            r.set(n).mul(2 * n.dot(v)).sub(v);
            float up = r.y;
            float er, eg, eb;
            if (up > 0) {
                float t = Math.min(1, up * 1.5F);
                er = gR + (skyR - gR) * (0.55F + 0.45F * t);
                eg = gG + (skyG - gG) * (0.55F + 0.45F * t);
                eb = gB + (skyB - gB) * (0.55F + 0.45F * t);
            } else {
                er = gR;
                eg = gG;
                eb = gB;
            }
            float spec = (float) Math.pow(Math.max(0, r.dot(SUN)), 48) * (1.3F + 2.5F * b.glint) * day;
            float fres = (float) Math.pow(1 - nv, 3);
            float diffuse = (0.18F + 0.55F * nl) * day + 0.06F;
            float cr = mr * tone * (diffuse + er * 0.9F) + spec * (0.75F + 0.25F * mr) + fres * er * 0.6F;
            float cg = mg * tone * (diffuse + eg * 0.9F) + spec * (0.75F + 0.25F * mg) + fres * eg * 0.6F;
            float cb = mb * tone * (diffuse + eb * 0.9F) + spec * (0.75F + 0.25F * mb) + fres * eb * 0.6F;
            int argb = 0xFF000000 | clamp(cr * 255) << 16 | clamp(cg * 255) << 8 | clamp(cb * 255);
            quads.quad(f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], f[9], f[10], f[11], argb);
        }
    }

    /** transforms a mesh into world space; keeps faces pointing at the camera: 4 corners, normal, tone, depth */
    private static void collect(List<float[]> out, SmoothMesh mesh, Vector3f pivot, Quaternionf rot, float scale, Vector3f offset, Vector3f camera) {
        float[] pos = mesh.positions(), nrm = mesh.normals();
        int[] col = mesh.colors();
        Vector3f v = new Vector3f(), n = new Vector3f();
        for (int q = 0; q < mesh.quads(); q++) {
            float[] f = new float[17];
            float depth = 0;
            for (int k = 0; k < 4; k++) {
                int i = (q * 4 + k) * 3;
                rot.transform(v.set(pos[i], pos[i + 1], pos[i + 2]).add(offset)).mul(scale).add(pivot);
                f[k * 3] = v.x;
                f[k * 3 + 1] = v.y;
                f[k * 3 + 2] = v.z;
                depth += v.distanceSquared(camera);
            }
            int i0 = q * 4 * 3;
            rot.transform(n.set(nrm[i0], nrm[i0 + 1], nrm[i0 + 2])).normalize();
            if (n.dot(camera.x - f[0], camera.y - f[1], camera.z - f[2]) <= 0) continue; // facing away
            f[12] = n.x;
            f[13] = n.y;
            f[14] = n.z;
            f[15] = (col[q * 4] & 255) / 255.0F;
            f[16] = depth;
            out.add(f);
        }
    }

    private static int clamp(float x) {
        return Math.max(0, Math.min(255, (int) x));
    }

    private static Vector3f direction(float azimuth, float elevation) {
        double c = Math.cos(elevation);
        return new Vector3f((float) (c * Math.sin(azimuth)), (float) Math.sin(elevation), (float) (c * Math.cos(azimuth)));
    }

    // ---------------------------------------------------------------- camera kick on big hits (called from mixins)

    /** a short, sharp camera shake */
    public static void cameraShake(PoseStack pose) {
        if (active == null || shake < 0.005F) return;
        float t = System.nanoTime() / 1.0E9F;
        float a = shake * 1.6F;
        pose.mulPose(new Quaternionf().rotationXYZ(
                (float) Math.toRadians(a * Math.sin(t * 47 + shakeSeed)),
                (float) Math.toRadians(a * 0.6F * Math.sin(t * 53 + shakeSeed * 3)),
                (float) Math.toRadians(a * 0.8F * Math.sin(t * 41 + shakeSeed * 7))));
    }

    /** a quick zoom punch: narrows the field of view for a moment */
    public static float fov(float fov) {
        if (active == null || punch < 0.005F) return fov;
        return fov * (1.0F - 0.07F * punch);
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

    /** tiny easing helper */
    private static final class HolidayEase {
        static float smooth(float u) {
            u = Math.max(0, Math.min(1, u));
            return u * u * (3 - 2 * u);
        }
    }
}
