package com.exav.addon.modules;

import com.exav.addon.ExavAddon;
import com.exav.addon.scan.BaseEvidence;
import com.exav.addon.scan.BlockReader;
import com.exav.addon.scan.CaveScanner;
import com.exav.addon.scan.CaveSurfaceScanner;
import com.exav.addon.scan.Evidence;
import com.exav.addon.scan.LightScanner;
import com.exav.addon.scan.Tunnel;
import com.exav.addon.scan.LiveEdits;
import com.exav.addon.scan.TunnelSet;
import com.exav.addon.util.MyDrops;
import com.exav.addon.util.MyEdits;
import com.exav.addon.util.SusChunks;
import imgui.ImDrawList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.BlockUpdateEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.gui.WorldLabel;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.HudOverlayProvider;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Puts a "Base xx%" label over every flagged sus chunk, estimated from player-made stuff found under it,
 * in the caves around it, and from blocks being broken/placed near it while you watch.
 */
public final class BaseFinder extends ClientModule implements HudOverlayProvider {
    private static final int CAVE_MIN_SIZE = 30;

    private final ModeSetting source = addSetting("source", new ModeSetting("Source", SusChunks.BOTH, SusChunks.SOURCES));
    private final BooleanSetting undergroundOnly = addSetting("underground-only", new BooleanSetting("Underground Only", true));
    private final BooleanSetting countTunnels = addSetting("count-tunnels", new BooleanSetting("Count Player Tunnels", true));
    private final BooleanSetting scanCaves = addSetting("scan-caves", new BooleanSetting("Scan Nearby Caves", true));
    private final NumberSetting caveRadius = addSetting("cave-radius", new NumberSetting("Cave Radius (chunks)", 1.0F, 0.0F, 3.0F).step(1.0F));
    private final BooleanSetting detectLight = addSetting("detect-light", new BooleanSetting("Detect Hidden Light", true));
    private final BooleanSetting countItems = addSetting("count-items", new BooleanSetting("Count Dropped Items", true));
    private final BooleanSetting liveEdits = addSetting("live-edits", new BooleanSetting("Watch Live Edits", true));
    private final NumberSetting liveWindow = addSetting("live-window", new NumberSetting("Live Edit Window (min)", 10.0F, 1.0F, 60.0F).step(1.0F));
    private final BooleanSetting includeMine = addSetting("include-mine", new BooleanSetting("Count My Own Edits", false));
    private final NumberSetting minPercent = addSetting("min-percent", new NumberSetting("Hide Below %", 0.0F, 0.0F, 90.0F).step(5.0F));
    private final BooleanSetting showDetails = addSetting("show-details", new BooleanSetting("Show Evidence", true));
    private final NumberSetting labelY = addSetting("label-y", new NumberSetting("Label Height", 66.0F, -60.0F, 320.0F).step(1.0F));
    private final NumberSetting maxDistance = addSetting("max-distance", new NumberSetting("Max Label Distance", 512.0F, 32.0F, 2048.0F).step(32.0F));
    private final NumberSetting rescanSeconds = addSetting("rescan-seconds", new NumberSetting("Rescan Every (s)", 15.0F, 5.0F, 120.0F).step(5.0F));
    private final NumberSetting budgetMs = addSetting("budget-ms", new NumberSetting("Scan Budget (ms/tick)", 2.0F, 1.0F, 10.0F).step(1.0F));

    private final Long2ObjectOpenHashMap<Scan> results = new Long2ObjectOpenHashMap<>();
    /** per flagged chunk: times (ms) of player-looking block changes near it */
    private final Long2ObjectOpenHashMap<LongArrayList> edits = new Long2ObjectOpenHashMap<>();
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    private final LongOpenHashSet queued = new LongOpenHashSet();
    private LongOpenHashSet flagged = new LongOpenHashSet();
    /** per flagged chunk: other players' items lying underground in or near it */
    private final Long2IntOpenHashMap items = new Long2IntOpenHashMap();
    private Job current;
    private BlockReader reader;
    private ClientLevel lastLevel;
    private int ticks;
    private boolean settingsChanged;

    /** Result for one flagged chunk: the counts, plus the structure zones (live edits inside them are ignored too). */
    private record Scan(BaseEvidence.Counts counts, Evidence.Zones zones) {
    }

    /** One flagged chunk: chunk scan, 3x3 tunnels, branch mines, nearby caves, cave surfaces, then light. */
    private final class Job {
        final long key;
        final int cx, cz;
        final BaseEvidence evidence;
        final TunnelSet tunnels;
        final CaveScanner caves;
        final boolean surfacesOn;
        final boolean lightOn;
        final int radius;
        CaveSurfaceScanner surfaces;
        LightScanner light;
        int phase;

        Job(long key, LevelChunk chunk) {
            this.key = key;
            this.cx = ChunkPos.getX(key);
            this.cz = ChunkPos.getZ(key);
            this.radius = caveRadius.getInt();
            this.surfacesOn = scanCaves.get();
            this.lightOn = detectLight.get();
            this.evidence = new BaseEvidence(reader, chunk, cx, cz, undergroundOnly.get(), !includeMine.get());
            this.tunnels = countTunnels.get() ? new TunnelSet(reader, cx, cz, true, 8) : null;
            this.caves = surfacesOn || lightOn ? new CaveScanner(reader, cx, cz, radius, CAVE_MIN_SIZE) : null;
        }

        boolean step(long deadline) {
            while (true) {
                boolean done = switch (phase) {
                    case 0 -> evidence.step(deadline);
                    case 1 -> tunnels == null || tunnels.step(deadline);
                    case 2 -> true;
                    case 3 -> caves == null || caves.step(deadline);
                    case 4 -> {
                        if (!surfacesOn) yield true;
                        if (surfaces == null) surfaces = new CaveSurfaceScanner(reader, caves.results(), cx, cz, !includeMine.get());
                        yield surfaces.step(deadline);
                    }
                    case 5 -> {
                        if (!lightOn) yield true;
                        if (light == null) light = new LightScanner(reader, caves.results(), cx, cz, radius);
                        yield light.step(deadline);
                    }
                    default -> true;
                };
                if (!done) return false;
                if (++phase > 5) return true;
                if (System.nanoTime() > deadline) return false;
            }
        }

        Scan finish() {
            Evidence.Zones zones = Evidence.zones(evidence.evidence(), surfaces == null ? null : surfaces.evidence());
            int[] ignored = new int[1];
            int[] chunkCounts = evidence.evidence().count(zones, ignored);
            int[] caveCounts = surfaces == null ? new int[BaseEvidence.KINDS] : surfaces.evidence().count(zones, ignored);
            int tunnelCount = 0, branchCount = 0;
            if (tunnels != null) {
                // stronghold corridors and trial chamber halls are exactly 3 wide, so tunnels inside structures don't count
                for (Tunnel t : includeMine.get() ? tunnels.results() : TunnelSet.withoutMine(tunnels.results())) {
                    AABB b = t.boxes().get(0);
                    if (zones.contains((int) b.getCenter().x, (int) b.minY, (int) b.getCenter().z)) ignored[0]++;
                    else if (t.width() == 1 && t.slope() == 0) branchCount++;
                    else tunnelCount++;
                }
            }
            int hiddenLight = 0;
            if (light != null) {
                for (int i = 0; i < light.results().size(); i++) {
                    if (zones.contains(light.results().getLong(i))) ignored[0]++;
                    else hiddenLight++;
                }
            }
            LongSet finder = SusChunks.finder();
            LongSet better = SusChunks.better();
            BaseEvidence.Counts counts = new BaseEvidence.Counts(chunkCounts, caveCounts, tunnelCount, branchCount,
                    hiddenLight, ignored[0], finder.contains(key) && better.contains(key));
            return new Scan(counts, zones);
        }
    }

    public BaseFinder() {
        super("Base Finder", "Shows the % chance there's a base under each flagged sus chunk, from what's under it, the caves and light around it and live block changes.", ExavAddon.CATEGORY);
        caveRadius.visibleWhen(() -> scanCaves.get() || detectLight.get());
        liveWindow.visibleWhen(liveEdits::get);
        Runnable rescan = () -> settingsChanged = true;
        source.onChange(rescan);
        undergroundOnly.onChange(rescan);
        countTunnels.onChange(rescan);
        includeMine.onChange(rescan);
        scanCaves.onChange(rescan);
        caveRadius.onChange(rescan);
        detectLight.onChange(rescan);
    }

    @Override
    protected void onEnable() {
        reset();
        edits.clear();
    }

    @Override
    protected void onDisable() {
        reset();
        edits.clear();
    }

    private void reset() {
        results.clear();
        queue.clear();
        queued.clear();
        flagged = new LongOpenHashSet();
        current = null;
        reader = null;
        lastLevel = null;
    }

    @EventHandler
    public void onTick(TickEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != lastLevel || settingsChanged) {
            if (level != lastLevel) edits.clear();
            settingsChanged = false;
            reset();
            lastLevel = level;
            reader = level == null ? null : new BlockReader(level);
        }
        if (level == null) return;
        ticks++;

        flagged = SusChunks.flagged(source.get());
        for (LongIterator it = results.keySet().iterator(); it.hasNext(); ) {
            if (!flagged.contains(it.nextLong())) it.remove();
        }
        if (current != null && !flagged.contains(current.key)) current = null;
        pruneEdits();
        if (ticks % 20 == 0) countItems(level);

        boolean rescanAll = ticks % (rescanSeconds.getInt() * 20) == 0;
        for (LongIterator it = flagged.iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            if ((rescanAll || !results.containsKey(key)) && queued.add(key)) queue.enqueue(key);
        }

        long deadline = System.nanoTime() + (long) (budgetMs.get() * 1_000_000L);
        while (System.nanoTime() < deadline) {
            if (current == null) {
                if (queue.isEmpty()) break;
                long key = queue.dequeueLong();
                queued.remove(key);
                if (!flagged.contains(key)) continue;
                LevelChunk chunk = reader.chunk(ChunkPos.getX(key), ChunkPos.getZ(key));
                if (chunk == null) continue;
                current = new Job(key, chunk);
            }
            if (current.step(deadline)) {
                results.put(current.key, current.finish());
                current = null;
            }
        }
    }

    @EventHandler
    public void onBlockUpdate(BlockUpdateEvent event) {
        if (!liveEdits.get() || flagged.isEmpty() || reader == null || event.pos == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        BlockPos pos = event.pos;
        if (pos.getY() >= reader.surface(pos.getX(), pos.getZ()) - 3) return; // underground only
        // your own breaking / placing never counts
        if (MyEdits.record(pos, event.oldState.isAir(), event.newState.isAir(), event.oldState.getBlock() != event.newState.getBlock())
                && !includeMine.get()) return;
        if (!LiveEdits.looksPlayerMade(event.oldState, event.newState, pos)) return;

        long now = System.currentTimeMillis();
        int r = caveRadius.getInt();
        int ecx = pos.getX() >> 4, ecz = pos.getZ() >> 4;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                long key = ChunkPos.asLong(ecx + dx, ecz + dz);
                if (!flagged.contains(key)) continue;
                Scan scan = results.get(key);
                if (scan != null && scan.zones().contains(pos.getX(), pos.getY(), pos.getZ())) continue;
                edits.computeIfAbsent(key, k -> new LongArrayList()).add(now);
            }
        }
    }

    private void countItems(ClientLevel level) {
        items.clear();
        if (!countItems.get() || flagged.isEmpty()) return;
        int r = caveRadius.getInt();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity item) || item.isRemoved()) continue;
            if (!includeMine.get() && MyDrops.isMine(item)) continue;
            if (item.getBlockY() >= reader.surface(item.getBlockX(), item.getBlockZ()) - 3) continue;
            int icx = item.getBlockX() >> 4, icz = item.getBlockZ() >> 4;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    long key = ChunkPos.asLong(icx + dx, icz + dz);
                    if (flagged.contains(key)) items.addTo(key, 1);
                }
            }
        }
    }

    private void pruneEdits() {
        long cutoff = System.currentTimeMillis() - liveWindow.getInt() * 60_000L;
        for (var it = edits.long2ObjectEntrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            LongArrayList times = e.getValue();
            times.removeIf(t -> t < cutoff);
            if (times.isEmpty() || !flagged.contains(e.getLongKey())) it.remove();
        }
    }

    @Override
    public boolean isHudOverlayEnabled() {
        return isEnabled() && !results.isEmpty() && Minecraft.getInstance().level != null;
    }

    @Override
    public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition();
        double maxSq = maxDistance.get() * (double) maxDistance.get();
        int hideBelow = minPercent.getInt();
        double y = labelY.get();
        long now = System.currentTimeMillis();
        for (var entry : results.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            Vec3 pos = new Vec3((ChunkPos.getX(key) << 4) + 8.0, y, (ChunkPos.getZ(key) << 4) + 8.0);
            double dx = pos.x - eye.x, dz = pos.z - eye.z;
            if (dx * dx + dz * dz > maxSq) continue;

            LongArrayList times = edits.get(key);
            int liveCount = times == null ? 0 : times.size();
            int ago = liveCount == 0 ? 0 : (int) ((now - times.getLong(liveCount - 1)) / 60_000L);
            BaseEvidence.Result r = BaseEvidence.score(entry.getValue().counts(), liveCount, ago, items.get(key));
            if (r.percent() < hideBelow) continue;

            float[] screen = WorldLabel.project(mc, pos, screenW, screenH);
            if (screen == null) continue;
            WorldLabel.drawText(dl, screen[0], screen[1], "Base " + r.percent() + "%", showDetails.get() ? r.summary() : null, 1.0F);
        }
    }
}
