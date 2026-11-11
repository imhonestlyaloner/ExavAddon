package com.exav.addon.modules;

import com.exav.addon.ExavAddon;
import com.exav.addon.audio.OggClip;
import com.exav.addon.holiday.Shapes;
import com.exav.addon.holiday.SmoothMesh;
import com.exav.addon.sky.SkyArt;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
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
 * Kill effects. "Yeat Bell": when you kill someone, a giant golden bell flies in and stops just above where they died,
 * rings (and swings round in a circle) when the bell sound in the "Get Busy" clip hits, then drops and lands with green fog rolling out from under it.
 * The clip is read from .minecraft/config/exav-addon/kill_effect.ogg (not shipped with the addon).
 */
public final class KillEffects extends ClientModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("Exav Addon");
    private static volatile KillEffects active;
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("anubis", "textures/misc/knife_base.png");

    /**
     * The bell sound in the clip (seconds): right after "this song was already turnt but here's a bell" (1:33.08 in
     * the song). Found by its own pitch (~1227 Hz), which is silent before and rings out after it.
     */
    static final float[] HITS = {4.08F};
    /** flies in and stops 7 blocks above where they died, hangs still until the bell sound, then drops */
    static final float FLY_IN = 1.3F, DROP_AT = 4.32F, LAND = 4.72F, SINK_AT = 7.4F, END = 8.4F;
    /** how far off (sideways and up) the bell starts its flight */
    static final float FLY_DISTANCE = 34.0F, FLY_RISE = 22.0F;

    private final ModeSetting effect = addSetting("effect", new ModeSetting("Effect", "Yeat Bell", "Yeat Bell"));
    private final BooleanSetting playersOnly = addSetting("players-only", new BooleanSetting("Players Only", true));
    private final NumberSetting bellSize = addSetting("bell-size", new NumberSetting("Bell Size", 4.5F, 2.0F, 10.0F).step(0.5F));
    private final NumberSetting hangHeight = addSetting("hang-height", new NumberSetting("Hang Height (blocks above them)", 2.5F, 0.0F, 15.0F).step(0.5F));
    private final NumberSetting volume = addSetting("volume", new NumberSetting("Volume", 0.8F, 0.0F, 1.0F).step(0.05F));
    private final NumberSetting sync = addSetting("sync", new NumberSetting("Sync Offset (ms)", 0.0F, -400.0F, 400.0F).step(10.0F));
    private final BooleanSetting greenFog = addSetting("green-fog", new BooleanSetting("Green Fog", true));
    private final BooleanSetting shake = addSetting("shake", new BooleanSetting("Landing Shake", true));
    private final StringSetting soundFile = addSetting("sound-file", new StringSetting("Sound File", "kill_effect.ogg"));
    private final ActionSetting test = addSetting("test", new ActionSetting("Test Effect", KillEffects::test));
    private final ActionSetting openFolder = addSetting("open-folder", new ActionSetting("Open Sound Folder", KillEffects::openFolder));

    /** pos = ground under the spot, hang = height above that ground where the bell stops (7 above the death point) */
    private record Drop(Vec3 pos, float hang, long start, int seed) {
    }

    private final List<Drop> drops = new ArrayList<>();
    /** who you hit and when */
    private final Int2LongOpenHashMap hitAt = new Int2LongOpenHashMap();
    /** when each entity last set off the effect (so one death only counts once, but a respawned player counts again) */
    private final Int2LongOpenHashMap counted = new Int2LongOpenHashMap();
    /** where each player was last seen, for kills that only show up in chat (the body may already be gone) */
    private final java.util.Map<String, Vec3> lastSeen = new java.util.HashMap<>();
    private OggClip clip;
    private int ticks;
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
    /** a kill: start the effect, unless this same death already did */
    private void kill(int entityId, Vec3 at) {
        long now = System.currentTimeMillis();
        if (now - counted.getOrDefault(entityId, 0L) < 5000) return;
        counted.put(entityId, now);
        start(at);
    }

    /**
     * The server tells everyone nearby who caused each bit of damage (also for arrows, tridents, crystals and other
     * explosions you set off), so anything you hurt in any way is watched for dying, not only what you hit by hand.
     * Death messages in chat that name you as the killer count too.
     */
    @EventHandler
    public void onPacket(net.anubis.client.event.impl.PacketAppliedEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (event.packet instanceof net.minecraft.network.protocol.game.ClientboundDamageEventPacket damage) {
            int me = mc.player.getId();
            if (damage.sourceCauseId() == me || damage.sourceDirectId() == me) hitAt.put(damage.entityId(), System.currentTimeMillis());
        } else if (event.packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat && !chat.overlay()) {
            deathMessage(mc, chat.content());
        }
    }

    /** vanilla death messages: "death.attack.<cause>.player" etc. with the victim first and the killer second */
    private void deathMessage(Minecraft mc, net.minecraft.network.chat.Component message) {
        if (!(message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tr)) return;
        if (!tr.getKey().startsWith("death.") || tr.getArgs().length < 2) return;
        String me = mc.player.getGameProfile().name();
        String victim = argText(tr.getArgs()[0]);
        boolean byMe = false;
        for (int i = 1; i < tr.getArgs().length; i++) {
            if (hasName(argText(tr.getArgs()[i]), me)) byMe = true; // display names can carry a rank / team prefix
        }
        if (!byMe || hasName(victim, me)) return;
        Player p = null;
        for (Player candidate : mc.level.players()) {
            if (candidate != mc.player && hasName(victim, candidate.getGameProfile().name())) p = candidate;
        }
        Vec3 at = p != null ? p.position() : null;
        int id = p != null ? p.getId() : 0;
        if (at == null) {
            for (var e : lastSeen.entrySet()) {
                if (hasName(victim, e.getKey())) {
                    at = e.getValue();
                    id = e.getKey().hashCode();
                }
            }
        }
        if (at != null) kill(id, at);
    }

    /** does the text contain this player name as a whole word? */
    private static boolean hasName(String text, String name) {
        return !name.isEmpty() && java.util.regex.Pattern.compile("(?<![A-Za-z0-9_])" + java.util.regex.Pattern.quote(name) + "(?![A-Za-z0-9_])").matcher(text).find();
    }

    private static String argText(Object arg) {
        return arg instanceof net.minecraft.network.chat.Component c ? c.getString() : String.valueOf(arg);
    }

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
            if (living.isDeadOrDying() || living.getHealth() <= 0) {
                kill(ent.getId(), living.position());
                it.remove();
            }
        }
        counted.int2LongEntrySet().removeIf(e -> now - e.getLongValue() > 5000);
        lastSeen.clear();
        for (Player p : mc.level.players()) {
            if (p != mc.player && p.isAlive()) lastSeen.put(p.getGameProfile().name(), p.position());
        }
        drops.removeIf(d -> (System.nanoTime() - d.start) / 1.0E9F > END + 0.5F);
        if (ticks++ % 20 == 0) { // keep the clip decoded and ready, so it starts in sync with the bell
            OggClip c = clip();
            if (c != null) c.preload();
        }
    }

    private boolean warned;

    /** the clip to play: the file named in Sound File, or else the first .ogg in the folder */
    private OggClip clip() {
        String name = soundFile.get().trim();
        java.nio.file.Path file = folder().resolve(name);
        if (!java.nio.file.Files.isRegularFile(file)) {
            try (var list = java.nio.file.Files.list(folder())) {
                file = list.filter(f -> f.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ogg")).sorted().findFirst().orElse(null);
            } catch (Exception e) {
                file = null;
            }
            if (file == null) return null;
        }
        String key = file.toString();
        if (clip == null || !key.equals(clipName)) {
            clip = new OggClip(file);
            clipName = key;
        }
        return clip;
    }

    private void start(Vec3 at) {
        // land on the ground under the spot
        Minecraft mc = Minecraft.getInstance();
        BlockPos p = BlockPos.containing(at);
        int floor = p.getY();
        for (int i = 0; i < 6 && mc.level.getBlockState(p.below()).isAir(); i++) p = p.below();
        floor = p.getY();
        drops.add(new Drop(new Vec3(at.x, floor, at.z), (float) (at.y - floor) + hangHeight.get(), System.nanoTime(), drops.size() * 31 + (int) (System.nanoTime() & 1023)));
        OggClip c = clip();
        if (c != null) {
            c.play(volume.get()); // the game's Master volume is applied on top
        } else if (mc.player != null && !warned) {
            warned = true;
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "\u00a7d[Exav]\u00a7r Kill Effects has no sound: put " + soundFile.get().trim() + " in " + folder().toAbsolutePath()), false);
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
        // where the bell is: flies in from far off and high up, slowing to a stop 7 blocks above the death point,
        // hangs there until the bell sound, then drops (speeding up), lands with a bounce, and sinks at the end
        float lift, offX = 0, offZ = 0, tilt = 0;
        double flyAngle = d.seed * 2.399; // a different direction for each kill
        if (t < FLY_IN) {
            float u = t / FLY_IN, left = (1 - u) * (1 - u) * (1 - u); // eases out to a stop
            lift = d.hang + FLY_RISE * left;
            offX = (float) Math.cos(flyAngle) * FLY_DISTANCE * left;
            offZ = (float) Math.sin(flyAngle) * FLY_DISTANCE * left;
            tilt = 0.35F * (1 - u) * (1 - u); // leans into the flight, then straightens up
        } else if (t < DROP_AT) {
            float since = t - FLY_IN;
            lift = d.hang;
            tilt = -0.12F * (float) Math.exp(-since * 2.2F) * (float) Math.sin(since * 6.5F); // settles after stopping
        } else if (t < LAND) {
            float u = (t - DROP_AT) / (LAND - DROP_AT);
            lift = d.hang * (1 - u * u);
        } else if (t < SINK_AT) {
            float since = t - LAND;
            lift = -0.15F * h * (float) Math.exp(-since * 9) * (float) Math.sin(since * 24); // a small bounce on landing
            lift = Math.max(lift, -0.08F * h);
        } else {
            lift = -h * Math.min(1, (t - SINK_AT) / (END - SINK_AT)); // sinks into the ground
        }
        // the clapper only moves for the bell sound: it swings out and hits the side exactly on the sound, then
        // bounces off and settles; the bell rocks and flashes from the hit
        float swing = tilt, clap = 0, glint = 0, circle = 0, circleAngle = 0;
        final float windUp = 0.14F, wall = 0.42F;
        for (int i = 0; i < HITS.length; i++) {
            float since = t - HITS[i];
            float sign = i % 2 == 0 ? 1 : -1;
            if (since < -windUp) continue;
            if (since < 0) {
                float u = (since + windUp) / windUp;
                clap += sign * wall * u * u; // speeding up toward the wall
                continue;
            }
            clap += sign * wall * (float) Math.exp(-since * 4.5F) * (float) Math.cos(since * 13);
            // the hit sends the bell swinging round in a circle (it leans over and the lean sweeps around), slowly
            // dying down - and it settles fast once it lands
            float fade = (float) Math.exp(-since * 0.55F) * (t > LAND ? (float) Math.exp(-(t - LAND) * 5) : 1);
            circle = Math.max(circle, 0.42F * fade * Math.min(1, since * 6));
            circleAngle = (float) (sign * since * Math.PI * 2 * 1.15) + d.seed;
            glint = Math.max(glint, (float) Math.exp(-since * 6));
        }
        if (t >= LAND) glint = Math.max(glint, 0.6F * (float) Math.exp(-(t - LAND) * 6)); // flash on landing (no clapper)
        clap = Math.max(-0.42F, Math.min(0.42F, clap));
        float x = (float) (d.pos.x - cam.x) + offX, y = (float) (d.pos.y - cam.y), z = (float) (d.pos.z - cam.z) + offZ;
        int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(d.pos).above());
        int bright = 0xF000F0;
        float yaw = d.seed * 0.7F;

        // the bell (hangs from its top, so put the top at bottom + height)
        pose.pushPose();
        pose.translate(x, y + lift + h, z);
        if (circle > 0.001F) pose.mulPose(new Quaternionf().rotationAxis(circle, (float) Math.cos(circleAngle), 0, (float) Math.sin(circleAngle)));
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
