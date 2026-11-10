package com.exav.addon.modules;

import com.exav.addon.ExavAddon;
import com.exav.addon.util.AnubisSus;
import com.exav.addon.util.SusChunks;
import imgui.ImDrawList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.gui.WorldLabel;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.HudOverlayProvider;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.FoglessLines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Makes Anubis's sus chunks more accurate:
 *  - matches Sus Chunk Finder / Better Sus Chunk's Simulation Distance to the server's real one
 *  - gives every flagged chunk a confidence % from how strong its heat is, whether both modules agree,
 *    how long it has stayed flagged, whether the heat around it has the shape an AFK player leaves,
 *    and Base Finder's evidence
 *  - finds the likely AFK spot: the heat-weighted centre of the area, not just the hottest chunk
 *  - optionally feeds only confident chunks (plus their AFK spot chunk) to the other Exav modules
 */
public final class SusConfidence extends ClientModule implements HudOverlayProvider {
    /** a flag that disappears for less than this still counts as continuously flagged */
    private static final long GRACE_MS = 15_000;

    private final BooleanSetting autoSimDistance = addSetting("auto-sim-distance", new BooleanSetting("Auto-Match Simulation Distance", true));
    private final NumberSetting hideBelow = addSetting("hide-below", new NumberSetting("Hide Below %", 0.0F, 0.0F, 90.0F).step(5.0F));
    private final BooleanSetting filterOthers = addSetting("filter-others", new BooleanSetting("Filter Other Exav Modules", true));
    private final NumberSetting filterAbove = addSetting("filter-above", new NumberSetting("Use In Other Modules Above %", 35.0F, 5.0F, 90.0F).step(5.0F));
    private final NumberSetting persistMinutes = addSetting("persist-minutes", new NumberSetting("Full Trust After (min)", 3.0F, 0.5F, 15.0F).step(0.5F));
    private final BooleanSetting showAfkSpot = addSetting("show-afk-spot", new BooleanSetting("Show AFK Spot", true));
    private final BooleanSetting outlines = addSetting("outlines", new BooleanSetting("Chunk Outlines", true));
    private final BooleanSetting labels = addSetting("labels", new BooleanSetting("Labels", true));
    private final NumberSetting labelY = addSetting("label-y", new NumberSetting("Label Height", 74.0F, -60.0F, 320.0F).step(1.0F));
    private final ColorSetting highColor = addSetting("high-color", new ColorSetting("High Color", 0xFFFF3030));
    private final ColorSetting midColor = addSetting("mid-color", new ColorSetting("Medium Color", 0xFFFFC020));
    private final ColorSetting lowColor = addSetting("low-color", new ColorSetting("Low Color", 0xFF909090));
    private final NumberSetting lineWidth = addSetting("line-width", new NumberSetting("Line Width", 2.5F, 1.0F, 5.0F).step(0.5F));

    /** What's known about one flagged chunk. afkX/afkZ: block coords of the heat-weighted centre around it. */
    public record Rating(int percent, double afkX, double afkZ, String details) {
        public long afkChunk() {
            return ChunkPos.asLong((int) Math.floor(afkX) >> 4, (int) Math.floor(afkZ) >> 4);
        }
    }

    private final Long2LongOpenHashMap firstSeen = new Long2LongOpenHashMap();
    private final Long2LongOpenHashMap lastSeen = new Long2LongOpenHashMap();
    private volatile Long2ObjectOpenHashMap<Rating> ratings = new Long2ObjectOpenHashMap<>();
    private static volatile LongSet accepted;
    private ClientLevel lastLevel;
    private int ticks;
    private String simStatus = "";

    public SusConfidence() {
        super("Sus Chunk Confidence", "Rates every sus chunk by how likely it's real, finds the AFK spot, and fixes the simulation distance setting.", ExavAddon.CATEGORY);
        filterAbove.visibleWhen(filterOthers::get);
        labelY.visibleWhen(labels::get);
    }

    /** Chunks the other Exav modules should use, or null when this module isn't filtering. */
    public static LongSet acceptedChunks() {
        return accepted;
    }

    @Override
    protected void onEnable() {
        firstSeen.clear();
        lastSeen.clear();
        ratings = new Long2ObjectOpenHashMap<>();
        accepted = null;
    }

    @Override
    protected void onDisable() {
        accepted = null;
        ratings = new Long2ObjectOpenHashMap<>();
    }

    @EventHandler
    public void onTick(TickEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != lastLevel) {
            lastLevel = level;
            firstSeen.clear();
            lastSeen.clear();
        }
        if (level == null) {
            accepted = null;
            return;
        }
        if (++ticks % 100 == 1 && autoSimDistance.get()) matchSimDistance(level);
        if (ticks % 10 != 0) return;
        rate(level);
    }

    /** The server tells the client its simulation distance; Anubis assumes 5 unless you change it. */
    private void matchSimDistance(ClientLevel level) {
        int server = level.getServerSimulationDistance();
        if (server <= 0) {
            simStatus = "";
            return;
        }
        int target = Math.max(2, Math.min(16, server));
        NumberSetting finder = AnubisSus.finderSimDistance();
        NumberSetting better = AnubisSus.betterSimDistance();
        boolean changed = false;
        if (finder != null && finder.getInt() != target) {
            finder.set(target);
            changed = true;
        }
        if (better != null && better.getInt() != target) {
            better.set(target);
            changed = true;
        }
        simStatus = "server sim distance " + server + (changed ? " (Anubis updated)" : "");
    }

    private void rate(ClientLevel level) {
        long now = System.currentTimeMillis();
        LongSet finderFlags = SusChunks.finder();
        LongSet betterFlags = SusChunks.better();
        LongOpenHashSet flagged = new LongOpenHashSet(finderFlags);
        flagged.addAll(betterFlags);

        Long2IntOpenHashMap heat = AnubisSus.finderHeat();
        Long2IntOpenHashMap support = AnubisSus.betterSupport();
        int finderSens = sens(AnubisSus.finderSensitivity(), 6);
        int betterSens = sens(AnubisSus.betterSensitivity(), 6);
        int radius = Math.max(2, simDistance());
        BaseFinder baseFinder = ModuleRegistry.get(BaseFinder.class);
        double persistMs = persistMinutes.get() * 60_000.0;

        // how long each chunk has been flagged (short gaps forgiven)
        for (LongIterator it = flagged.iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            long last = lastSeen.get(key);
            if (last == 0 || now - last > GRACE_MS) firstSeen.put(key, now);
            lastSeen.put(key, now);
        }
        for (LongIterator it = lastSeen.keySet().iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            if (now - lastSeen.get(key) > GRACE_MS) {
                it.remove();
                firstSeen.remove(key);
            }
        }

        Long2ObjectOpenHashMap<Rating> next = new Long2ObjectOpenHashMap<>();
        LongOpenHashSet ok = new LongOpenHashSet();
        int threshold = filterAbove.getInt();
        for (LongIterator it = flagged.iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
            List<String> why = new ArrayList<>();

            // 1. strength: how far above the flag threshold the heat is (ratio 1 -> 0, 2 -> 0.63, 3 -> 0.86)
            double ratio = 0;
            if (heat.containsKey(key)) ratio = Math.max(ratio, heat.get(key) / (double) finderSens);
            if (support.containsKey(key)) ratio = Math.max(ratio, support.get(key) / (double) betterSens);
            double strength = ratio >= 1 ? 1 - Math.exp(-(ratio - 1)) : 0;
            if (ratio > 0) why.add(String.format("heat x%.1f", ratio));

            // 2. both modules agree (within a chunk of each other)
            boolean agree = nearAny(finderFlags, cx, cz, 1) && nearAny(betterFlags, cx, cz, 1);
            if (agree) why.add("both agree");

            // 3. staying power
            double persist = Math.min(1.0, (now - firstSeen.get(key)) / persistMs);
            why.add((now - firstSeen.get(key)) / 60_000 + "m flagged");

            // 4. shape: an AFK player leaves a pyramid of heat that drops off evenly in every direction
            Long2IntOpenHashMap field = heat.containsKey(key) || support.isEmpty() ? heat : support;
            double shape = shape(field, cx, cz, radius);
            if (shape >= 0) why.add(shape >= 0.75 ? "clean shape" : shape >= 0.5 ? "ok shape" : "messy shape");

            // 5. Base Finder's evidence, if it has scanned this chunk
            double base = -1;
            if (baseFinder != null && baseFinder.isEnabled()) {
                int p = baseFinder.percentFor(key);
                if (p >= 0) {
                    base = Math.max(0, (p - 10) / 89.0);
                    why.add("base " + p + "%");
                }
            }

            double score = 1.4 * strength + 0.8 * (agree ? 1 : 0) + 0.8 * persist
                    + 0.8 * (shape < 0 ? 0.5 : shape) + (base < 0 ? 0 : 1.2 * base);
            int percent = (int) Math.round(100.0 / (1.0 + Math.exp(-(score - 2.0) * 1.5)));
            percent = Math.max(1, Math.min(99, percent));

            double[] centre = centroid(field, cx, cz, radius);
            Rating r = new Rating(percent, centre[0], centre[1], String.join(" · ", why));
            next.put(key, r);
            if (percent >= threshold) {
                ok.add(key);
                ok.add(r.afkChunk());
            }
        }
        ratings = next;
        accepted = filterOthers.get() ? ok : null;
    }

    /**
     * An AFK player leaves a pyramid of heat: the average in each ring of 1..radius chunks around the peak
     * drops by at least a few percent, and the outer ring is clearly lower than the centre. Random growth
     * stays flat. Returns 0..1 (1 = clean pyramid), or -1 if there's no heat data here.
     */
    static double shape(Long2IntOpenHashMap field, int cx, int cz, int radius) {
        if (field.isEmpty() || !field.containsKey(ChunkPos.asLong(cx, cz))) return -1;
        double centre = field.get(ChunkPos.asLong(cx, cz));
        double prev = centre, avg = centre;
        int good = 0;
        for (int r = 1; r <= radius; r++) {
            double sum = 0;
            int n = 0;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    sum += field.get(ChunkPos.asLong(cx + dx, cz + dz));
                    n++;
                }
            }
            avg = sum / n;
            if (avg <= prev * 0.97) good++;
            prev = avg;
        }
        double s = good / (double) radius;
        if (avg > 0.85 * centre) s *= 0.5;
        return s;
    }

    /**
     * Heat-weighted centre (block coords) of the heat around a chunk. The heat of one AFK spot spreads up to
     * 2x simulation distance, so the window is that wide, and it's re-centred a few times so a flag that's a
     * chunk off still finds the real spot. Chunk centre if there's no data.
     */
    static double[] centroid(Long2IntOpenHashMap field, int cx, int cz, int radius) {
        double x = cx * 16.0 + 8.0, z = cz * 16.0 + 8.0;
        int window = 2 * radius;
        for (int iter = 0; iter < 4; iter++) {
            int ccx = (int) Math.floor(x) >> 4, ccz = (int) Math.floor(z) >> 4;
            double sx = 0, sz = 0, w = 0;
            for (int dx = -window; dx <= window; dx++) {
                for (int dz = -window; dz <= window; dz++) {
                    int v = field.get(ChunkPos.asLong(ccx + dx, ccz + dz));
                    if (v <= 0) continue;
                    sx += v * ((ccx + dx) * 16.0 + 8.0);
                    sz += v * ((ccz + dz) * 16.0 + 8.0);
                    w += v;
                }
            }
            if (w == 0) break;
            x = sx / w;
            z = sz / w;
        }
        return new double[]{x, z};
    }

    private static boolean nearAny(LongSet set, int cx, int cz, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (set.contains(ChunkPos.asLong(cx + dx, cz + dz))) return true;
            }
        }
        return false;
    }

    private static int sens(NumberSetting s, int fallback) {
        return s == null ? fallback : Math.max(1, s.getInt());
    }

    private static int simDistance() {
        NumberSetting s = AnubisSus.finderSimDistance();
        if (s == null) s = AnubisSus.betterSimDistance();
        return s == null ? 5 : s.getInt();
    }

    private int colorFor(int percent) {
        return percent >= 70 ? highColor.argb() : percent >= 40 ? midColor.argb() : lowColor.argb();
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        Long2ObjectOpenHashMap<Rating> current = ratings;
        if (current.isEmpty() || Minecraft.getInstance().level == null) return;
        FoglessLines lines = FoglessLines.forFrame(event.partialTick);
        float width = lineWidth.get();
        int hide = hideBelow.getInt();
        LongOpenHashSet spotsDrawn = new LongOpenHashSet();
        for (var e : current.long2ObjectEntrySet()) {
            Rating r = e.getValue();
            if (r.percent() < hide) continue;
            int argb = colorFor(r.percent());
            if (outlines.get()) {
                double x0 = (ChunkPos.getX(e.getLongKey()) << 4) + 0.3, z0 = (ChunkPos.getZ(e.getLongKey()) << 4) + 0.3;
                double x1 = x0 + 15.4, z1 = z0 + 15.4, y = 63.5;
                lines.line(x0, y, z0, x1, y, z0, argb, width);
                lines.line(x1, y, z0, x1, y, z1, argb, width);
                lines.line(x1, y, z1, x0, y, z1, argb, width);
                lines.line(x0, y, z1, x0, y, z0, argb, width);
            }
            // one beam per AFK spot (neighbouring flags usually share it)
            if (showAfkSpot.get() && spotsDrawn.add(((long) Math.round(r.afkX()) << 32) ^ Math.round(r.afkZ()))) {
                lines.line(r.afkX(), -64, r.afkZ(), r.afkX(), 320, r.afkZ(), argb, width * 1.5F);
            }
        }
    }

    @Override
    public boolean isHudOverlayEnabled() {
        return isEnabled() && labels.get() && !ratings.isEmpty();
    }

    @Override
    public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
        Minecraft mc = Minecraft.getInstance();
        int hide = hideBelow.getInt();
        boolean first = true;
        for (var e : ratings.long2ObjectEntrySet()) {
            Rating r = e.getValue();
            if (r.percent() < hide) continue;
            long key = e.getLongKey();
            Vec3 pos = new Vec3((ChunkPos.getX(key) << 4) + 8.0, labelY.get(), (ChunkPos.getZ(key) << 4) + 8.0);
            float[] screen = WorldLabel.project(mc, pos, screenW, screenH);
            if (screen == null) continue;
            String details = r.details();
            if (first && !simStatus.isEmpty()) details = details + " · " + simStatus;
            first = false;
            WorldLabel.drawText(dl, screen[0], screen[1], "Sus " + r.percent() + "%", details, 1.0F);
        }
    }

    public Rating ratingFor(long chunkKey) {
        return ratings.get(chunkKey);
    }
}
