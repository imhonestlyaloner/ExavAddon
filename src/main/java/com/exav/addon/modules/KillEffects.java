package com.exav.addon.modules;

import com.exav.addon.ExavAddon;
import com.exav.addon.audio.OggClip;
import com.exav.addon.holiday.Shapes;
import com.exav.addon.holiday.SmoothMesh;
import com.exav.addon.sky.SkyArt;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.setting.ActionSetting;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.StringSetting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kill effects. "Yeat Bell": when you kill someone, a giant golden bell drops out of the sky onto the spot, landing on
 * the big bell hit of the "Get Busy" clip and ringing on its other bell hits, with green fog rolling out from under it.
 * The clip is read from .minecraft/config/exav-addon/kill_effect.ogg (not shipped with the addon).
 */
public final class KillEffects extends ClientModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("Exav Addon");
    private static volatile KillEffects active;
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("anubis", "textures/misc/knife_base.png");

    /** bell hits in the clip (seconds), found by looking for bell-like (sustained, bright) note starts; LAND is the big one */
    static final float[] HITS = {0.209F, 0.511F, 1.312F, 1.533F, 1.916F, 2.403F, 2.612F, 3.251F, 4.098F};
    static final float LAND = 1.533F, SINK_AT = 5.4F, END = 6.4F, DROP_HEIGHT = 40.0F;

    private final ModeSetting effect = addSetting("effect", new ModeSetting("Effect", "Yeat Bell", "Yeat Bell"));
    private final BooleanSetting playersOnly = addSetting("players-only", new BooleanSetting("Players Only", true));
    private final NumberSetting bellSize = addSetting("bell-size", new NumberSetting("Bell Size", 4.5F, 2.0F, 10.0F).step(0.5F));
    private final NumberSetting volume = addSetting("volume", new NumberSetting("Volume", 0.8F, 0.0F, 1.0F).step(0.05F));
    private final NumberSetting sync = addSetting("sync", new NumberSetting("Sync Offset (ms)", 0.0F, -400.0F, 400.0F).step(10.0F));
    private final BooleanSetting greenFog = addSetting("green-fog", new BooleanSetting("Green Fog", true));
    private final BooleanSetting shake = addSetting("shake", new BooleanSetting("Landing Shake", true));
    private final StringSetting soundFile = addSetting("sound-file", new StringSetting("Sound File", "kill_effect.ogg"));
    private final ActionSetting test = addSetting("test", new ActionSetting("Test Effect", KillEffects::test));
    private final ActionSetting openFolder = addSetting("open-folder", new ActionSetting("Open Sound Folder", KillEffects::openFolder));

    private record Drop(Vec3 pos, long start, int seed) {
    }

    private final List<Drop> drops = new ArrayList<>();
    /** who you hit and when */
    private final Int2LongOpenHashMap hitAt = new Int2LongOpenHashMap();
    private final IntOpenHashSet counted = new IntOpenHashSet();
    private OggClip clip;
    private String clipName = "";

    private static SmoothMesh bell, clapper, puff;

    public KillEffects() {
        super("Kill Effects", "Kill effects: a giant Yeat bell drops on whoever you kill, synced to the Get Busy clip.", ExavAddon.CATEGORY);
    }

    static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("exav-addon");
    }

    private static void openFolder() {
        try {
            Files.createDirectories(folder());
            net.minecraft.util.Util.getPlatform().openPath(folder());
        } catch (Exception e) {
            LOGGER.warn("Couldn't open the folder", e);
        }
    }

    /** drops a bell where you're looking, to try it out */
    private static void test() {
        KillEffects m = active;
        Minecraft mc = Minecraft.getInstance();
        if (m == null || mc.player == null) return;
        Vec3 at = mc.hitResult != null ? mc.hitResult.getLocation() : mc.player.position().add(mc.player.getLookAngle().scale(8));
        m.start(at);
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
        drops.clear();
    }

    /** from the attack mixin */
    public static void attacked(Entity target) {
        KillEffects m = active;
        if (m != null && target != null) m.hitAt.put(target.getId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onTick(TickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            drops.clear();
            hitAt.clear();
            counted.clear();
            return;
        }
        long now = System.currentTimeMillis();
        // someone you hit in the last 6 seconds just died -> that's your kill
        for (var it = hitAt.int2LongEntrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (now - e.getLongValue() > 6000) {
                it.remove();
                continue;
            }
            Entity ent = mc.level.getEntity(e.getIntKey());
            if (!(ent instanceof LivingEntity living)) continue;
            if (playersOnly.get() && !(ent instanceof Player)) continue;
            if ((living.isDeadOrDying() || living.getHealth() <= 0) && counted.add(ent.getId())) {
                start(living.position());
                it.remove();
            }
        }
        if (counted.size() > 256) counted.clear();
        drops.removeIf(d -> (System.nanoTime() - d.start) / 1.0E9F > END + 0.5F);
        if (clip != null) clip.cleanup();
    }

    private void start(Vec3 at) {
        // land on the ground under the spot
        Minecraft mc = Minecraft.getInstance();
        BlockPos p = BlockPos.containing(at);
        int floor = p.getY();
        for (int i = 0; i < 6 && mc.level.getBlockState(p.below()).isAir(); i++) p = p.below();
        floor = p.getY();
        drops.add(new Drop(new Vec3(at.x, floor, at.z), System.nanoTime(), drops.size() * 31 + (int) (System.nanoTime() & 1023)));
        String name = soundFile.get().trim();
        if (clip == null || !name.equals(clipName)) {
            clip = new OggClip(folder().resolve(name));
            clipName = name;
        }
        if (clip.exists()) {
            float master = mc.options.getSoundSourceVolume(SoundSource.MASTER);
            clip.play(volume.get() * master);
        } else {
            LOGGER.info("Kill effect sound not found: put {} in {}", name, folder());
        }
    }

    // ---------------------------------------------------------------- drawing (camera-relative, with the entities)

    public static void submit(PoseStack pose, SubmitNodeCollector collector) {
        KillEffects m = active;
        if (m == null || m.drops.isEmpty()) return;
        if (bell == null) {
            bell = SkyArt.bell();
            clapper = SkyArt.clapper();
            SmoothMesh.Builder b = new SmoothMesh.Builder();
            Shapes.sphere(b, 0, 0, 0, 1, 0xFFFFFF, 8);
            puff = b.build();
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        float offset = m.sync.get() / 1000.0F;
        for (Drop d : m.drops) {
            float t = (System.nanoTime() - d.start) / 1.0E9F + offset;
            if (t < 0) continue;
            m.drawDrop(pose, collector, mc, cam, d, t);
        }
    }

    private void drawDrop(PoseStack pose, SubmitNodeCollector collector, Minecraft mc, Vec3 cam, Drop d, float t) {
        float h = bellSize.get();
        float scale = h / 10.0F;
        RenderType solid = RenderTypes.entityCutoutNoCull(WHITE), glow = RenderTypes.entityTranslucent(WHITE);
        // height of the bell's bottom above the ground: falls (speeding up), lands on the big hit, sinks at the end
        float lift;
        if (t < LAND) {
            float u = t / LAND;
            lift = DROP_HEIGHT * (1 - u * u);
        } else if (t < SINK_AT) {
            float since = t - LAND;
            lift = -0.15F * h * (float) Math.exp(-since * 9) * (float) Math.sin(since * 24); // a small bounce on landing
            lift = Math.max(lift, -0.08F * h);
        } else {
            lift = -h * Math.min(1, (t - SINK_AT) / (END - SINK_AT)); // sinks into the ground
        }
        // swing and glint from every bell hit so far (alternating directions)
        float swing = 0, clap = 0, glint = 0;
        for (int i = 0; i < HITS.length; i++) {
            float since = t - HITS[i];
            if (since < 0) continue;
            float strength = HITS[i] == LAND ? 1.0F : 0.6F;
            float sign = i % 2 == 0 ? 1 : -1;
            swing += sign * 0.22F * strength * (float) Math.exp(-since * 2.5F) * (float) Math.sin(since * 7);
            clap += -sign * 0.5F * strength * (float) Math.exp(-since * 3.5F) * (float) Math.sin(since * 11 + 0.6F);
            glint = Math.max(glint, strength * (float) Math.exp(-since * 6));
        }
        clap = Math.max(-0.42F, Math.min(0.42F, clap));
        float x = (float) (d.pos.x - cam.x), y = (float) (d.pos.y - cam.y), z = (float) (d.pos.z - cam.z);
        int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(d.pos).above());
        int bright = 0xF000F0;
        float yaw = d.seed * 0.7F;

        // the bell (hangs from its top, so put the top at bottom + height)
        pose.pushPose();
        pose.translate(x, y + lift + h, z);
        pose.mulPose(new Quaternionf().rotationY(yaw).rotateX(swing));
        pose.scale(scale, scale, scale);
        int gold = 0xFFD9A93C;
        int tint = glint > 0.02F ? brighten(gold, glint * 0.6F) : gold;
        collector.submitCustomGeometry(pose, solid, (p, out) -> bell.emit(p, out, light, tint));
        pose.pushPose();
        pose.translate(0, -2.2F, 0);
        pose.mulPose(new Quaternionf().rotationX(clap));
        collector.submitCustomGeometry(pose, solid, (p, out) -> clapper.emit(p, out, light, 0xFF8A6A2A));
        pose.popPose();
        if (glint > 0.03F) { // a bright flash off the metal on each ding
            pose.pushPose();
            pose.translate(0, -5, 0);
            float s = 6.5F + 2 * glint;
            pose.scale(s, s, s);
            int a = (int) (110 * glint);
            collector.submitCustomGeometry(pose, glow, (p, out) -> puff.emit(p, out, bright, a << 24 | 0xFFF0B0));
            pose.popPose();
        }
        pose.popPose();

        if (t < LAND) return;
        float since = t - LAND;
        // green shockwave across the ground
        if (since < 0.8F) {
            float r = h * (0.8F + 3.5F * since / 0.8F);
            int a = (int) (150 * (1 - since / 0.8F));
            pose.pushPose();
            pose.translate(x, y + 0.05F, z);
            pose.scale(r, 0.08F, r);
            collector.submitCustomGeometry(pose, glow, (p, out) -> puff.emit(p, out, bright, a << 24 | 0x3BFF6A));
            pose.popPose();
        }
        // green fog rolling out from underneath and drifting up
        if (greenFog.get() && since < SINK_AT - LAND + 1.0F) {
            Random r = new Random(d.seed);
            float fade = 1 - Math.max(0, Math.min(1, (t - SINK_AT) / 1.0F));
            for (int i = 0; i < 26; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                float speed = 0.6F + 1.4F * r.nextFloat();
                float out = (1 - (float) Math.exp(-since * 1.4F)) * speed * h * 0.6F + h * 0.45F;
                float rise = since * (0.25F + 0.35F * r.nextFloat()) * h * 0.25F;
                float px = x + (float) Math.cos(ang) * out, pz = z + (float) Math.sin(ang) * out, py = y + 0.3F + rise;
                float s = h * (0.25F + 0.25F * r.nextFloat()) * (0.6F + Math.min(1, since * 1.5F));
                int a = (int) (95 * fade * Math.min(1, since * 4) * (0.6F + 0.4F * r.nextFloat()));
                int col = r.nextBoolean() ? 0x2FE05A : 0x7CFF9A;
                pose.pushPose();
                pose.translate(px, py, pz);
                pose.scale(s, s * 0.7F, s);
                collector.submitCustomGeometry(pose, glow, (p, o) -> puff.emit(p, o, bright, a << 24 | col));
                pose.popPose();
            }
        }
        if (shake.get() && since < 0.05F) ArtistEggs.kick(0.9F);
    }

    private static int brighten(int argb, float k) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        r += (int) ((255 - r) * k);
        g += (int) ((255 - g) * k);
        b += (int) ((255 - b) * k);
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }
}
