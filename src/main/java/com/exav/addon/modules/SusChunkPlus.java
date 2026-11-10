package com.exav.addon.modules;

import com.exav.addon.ExavAddon;
import com.exav.addon.scan.BlockReader;
import com.exav.addon.scan.ChunkJob;
import com.exav.addon.scan.Merger;
import com.exav.addon.scan.Tunnel;
import com.exav.addon.scan.TunnelArea;
import com.exav.addon.scan.TunnelCache;
import com.exav.addon.util.SusChunks;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.HudOverlayProvider;
import net.anubis.client.gui.WorldLabel;
import imgui.ImDrawList;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.BlockClusterGeometry;
import net.anubis.client.util.FoglessLines;
import net.anubis.client.util.WorldQuads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Whenever a sus chunk gets flagged (by Sus Chunk Finder and/or Better Sus Chunk), this also labels
 * the 3x3 tunnels running through it and the caves around it.
 */
public final class SusChunkPlus extends ClientModule implements HudOverlayProvider {
    private static final ExecutorService MERGE_THREAD = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Exav SusChunkPlus merge");
        t.setDaemon(true);
        return t;
    });

    private final ModeSetting source = addSetting("source", new ModeSetting("Source", SusChunks.BOTH, SusChunks.SOURCES));

    private final BooleanSetting tunnels = addSetting("tunnels", new BooleanSetting("Tunnels", true));
    private final ModeSetting tunnelShapes = addSetting("tunnel-shapes", new ModeSetting("Tunnel Shapes", "All", "All", "3x3 Only"));
    private final NumberSetting minTunnelLength = addSetting("min-tunnel-length", new NumberSetting("Min Tunnel Length (3x3+)", 8.0F, 4.0F, 32.0F).step(1.0F));
    private final NumberSetting tunnelRadius = addSetting("tunnel-radius", new NumberSetting("Tunnel Radius (chunks)", 1.0F, 0.0F, 3.0F).step(1.0F));
    private final ColorSetting tunnelColor = addSetting("tunnel-color", new ColorSetting("Tunnel Color", 0xFFFF8C00));

    private final BooleanSetting caves = addSetting("caves", new BooleanSetting("Caves", true));
    private final NumberSetting caveRadius = addSetting("cave-radius", new NumberSetting("Cave Radius (chunks)", 1.0F, 0.0F, 3.0F).step(1.0F));
    private final NumberSetting minCaveSize = addSetting("min-cave-size", new NumberSetting("Min Cave Size", 150.0F, 20.0F, 3000.0F).step(10.0F));
    private final BooleanSetting caveFill = addSetting("cave-fill", new BooleanSetting("Fill Caves", true));
    private final ColorSetting caveColor = addSetting("cave-color", new ColorSetting("Cave Color", 0xFF00C8FF));

    private final BooleanSetting includeMine = addSetting("include-mine", new BooleanSetting("Count My Own Edits", false));
    private final BooleanSetting debugInfo = addSetting("debug-info", new BooleanSetting("Debug Info", true));
    private final NumberSetting lineWidth = addSetting("line-width", new NumberSetting("Line Width", 2.0F, 1.0F, 5.0F).step(0.5F));
    private final NumberSetting fillAlpha = addSetting("fill-alpha", new NumberSetting("Fill Opacity", 40.0F, 0.0F, 255.0F).step(5.0F));
    private final NumberSetting rescanSeconds = addSetting("rescan-seconds", new NumberSetting("Rescan Every (s)", 20.0F, 5.0F, 120.0F).step(5.0F));
    private final NumberSetting budgetMs = addSetting("budget-ms", new NumberSetting("Scan Budget (ms/tick)", 3.0F, 1.0F, 10.0F).step(1.0F));

    private final Long2ObjectOpenHashMap<ChunkJob.Result> results = new Long2ObjectOpenHashMap<>();
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    private final LongOpenHashSet queued = new LongOpenHashSet();
    private final TunnelCache tunnelCache = new TunnelCache();
    private ChunkJob current;
    private BlockReader reader;
    private ClientLevel lastLevel;
    private int ticks;
    private boolean settingsChanged;
    private boolean dirty;
    private boolean merging;
    private int generation;
    private volatile Merger.Output output = Merger.Output.EMPTY;

    public SusChunkPlus() {
        super("Exav's Relog Alternative", "When a sus chunk gets flagged, also labels player-dug tunnels through it and caves around it. Ignores your own digging.", ExavAddon.CATEGORY);
        minTunnelLength.visibleWhen(tunnels::get);
        tunnelShapes.visibleWhen(tunnels::get);
        tunnelRadius.visibleWhen(tunnels::get);
        tunnelColor.visibleWhen(tunnels::get);
        caveRadius.visibleWhen(caves::get);
        minCaveSize.visibleWhen(caves::get);
        caveFill.visibleWhen(caves::get);
        caveColor.visibleWhen(caves::get);
        Runnable rescan = () -> settingsChanged = true;
        source.onChange(rescan);
        tunnels.onChange(rescan);
        tunnelShapes.onChange(rescan);
        minTunnelLength.onChange(rescan);
        tunnelRadius.onChange(rescan);
        includeMine.onChange(() -> dirty = true);
        caves.onChange(rescan);
        caveRadius.onChange(rescan);
        minCaveSize.onChange(rescan);
    }

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable() {
        reset();
    }

    private void reset() {
        results.clear();
        queue.clear();
        queued.clear();
        current = null;
        tunnelCache.clear();
        reader = null;
        lastLevel = null;
        dirty = false;
        generation++;
        output = Merger.Output.EMPTY;
    }

    @EventHandler
    public void onTick(TickEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != lastLevel || settingsChanged) {
            settingsChanged = false;
            reset();
            lastLevel = level;
            reader = level == null ? null : new BlockReader(level);
        }
        if (level == null) return;
        ticks++;

        LongOpenHashSet flagged = SusChunks.flagged(source.get());

        // forget chunks that aren't flagged anymore
        for (LongIterator it = results.keySet().iterator(); it.hasNext(); ) {
            if (!flagged.contains(it.nextLong())) {
                it.remove();
                dirty = true;
            }
        }
        if (current != null && !flagged.contains(current.chunkKey())) current = null;

        // newly flagged chunks, plus a periodic rescan of all of them
        boolean rescanAll = ticks % (rescanSeconds.getInt() * 20) == 0;
        for (LongIterator it = flagged.iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            if ((rescanAll || !results.containsKey(key)) && queued.add(key)) queue.enqueue(key);
        }

        runJobs(flagged);

        if (dirty && !merging) submitMerge();
    }

    private void runJobs(LongOpenHashSet flagged) {
        long deadline = System.nanoTime() + (long) (budgetMs.get() * 1_000_000L);
        while (System.nanoTime() < deadline) {
            if (current == null) {
                if (queue.isEmpty()) return;
                long key = queue.dequeueLong();
                queued.remove(key);
                if (!flagged.contains(key)) continue;
                int cx = ChunkPos.getX(key);
                int cz = ChunkPos.getZ(key);
                if (!reader.isLoaded(cx, cz)) continue;
                // cached chunk scans stay valid a bit less than the rescan period, so a rescan really rescans
                long maxAge = rescanSeconds.getInt() * 1000L - 1000L;
                TunnelArea area = new TunnelArea(reader, tunnelCache, maxAge, cx, cz, tunnelRadius.getInt(),
                        tunnelShapes.is("All"), minTunnelLength.getInt());
                current = new ChunkJob(reader, key, cx, cz, results.get(key),
                        tunnels.get(), area, caves.get(), caveRadius.getInt(), minCaveSize.getInt());
            }
            boolean done = current.step(deadline);
            if (done || current.hasProgress()) {
                // show tunnels as each chunk finishes instead of waiting for the whole area
                results.put(current.chunkKey(), current.result());
                dirty = true;
            }
            if (done) current = null;
        }
    }

    private void submitMerge() {
        dirty = false;
        merging = true;
        int gen = generation;
        List<ChunkJob.Result> snapshot = new ArrayList<>(results.values());
        int minSize = minCaveSize.getInt();
        boolean mine = includeMine.get();
        CompletableFuture.supplyAsync(() -> Merger.merge(snapshot, minSize, mine), MERGE_THREAD)
                .whenComplete((out, error) -> Minecraft.getInstance().execute(() -> {
                    merging = false;
                    if (error == null && gen == generation && isEnabled()) output = out;
                }));
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (Minecraft.getInstance().level == null) return;
        Merger.Output out = output;
        if (out.tunnelBoxes().isEmpty() && out.caves().isEmpty()) return;

        FoglessLines lines = FoglessLines.forFrame(event.partialTick);
        WorldQuads quads = WorldQuads.layer(true);
        float width = lineWidth.get();
        int alpha = fillAlpha.getInt();

        if (tunnels.get()) {
            int stroke = tunnelColor.argb();
            int fill = tunnelColor.argbWithAlpha(alpha);
            for (AABB box : out.tunnelBoxes()) {
                if (alpha > 0) fillBox(quads, box, fill);
                lines.box(box, stroke, width);
            }
        }

        if (caves.get()) {
            int stroke = caveColor.argb();
            int fill = caveColor.argbWithAlpha(alpha);
            for (Merger.Cave cave : out.caves()) {
                BlockClusterGeometry.Mesh mesh = cave.mesh();
                if (caveFill.get() && alpha > 0) {
                    for (BlockClusterGeometry.Face f : mesh.faces()) {
                        quad(quads, f.a(), f.b(), f.c(), f.d(), fill);
                    }
                }
                for (BlockClusterGeometry.Segment s : mesh.edges()) {
                    lines.line(s.a(), s.b(), stroke, width);
                }
            }
        }
    }

    @Override
    public boolean isHudOverlayEnabled() {
        return isEnabled() && debugInfo.get() && Minecraft.getInstance().level != null && !results.isEmpty();
    }

    /** "Relog Alt: 2 tunnels · 4 caves" (or scan progress) over each flagged chunk, to see what it's doing. */
    @Override
    public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Merger.Output out = output;
        for (var entry : results.long2ObjectEntrySet()) {
            long key = entry.getLongKey();
            int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
            int tunnelCount = 0, caveCount = 0;
            for (Tunnel t : out.tunnels()) {
                if (near(t.boxes().get(0), cx, cz, Math.max(tunnelRadius.getInt(), 0))) tunnelCount++;
            }
            for (Merger.Cave c : out.caves()) {
                if (near(c.bounds(), cx, cz, caveRadius.getInt())) caveCount++;
            }
            String status = current != null && current.chunkKey() == key ? "scanning " + current.progress()
                    : entry.getValue().complete() ? "scanned" : "queued";
            Vec3 pos = new Vec3((cx << 4) + 8.0, 70.0, (cz << 4) + 8.0);
            float[] screen = WorldLabel.project(mc, pos, screenW, screenH);
            if (screen == null) continue;
            WorldLabel.drawText(dl, screen[0], screen[1], "Relog Alt: " + tunnelCount + " tunnels · " + caveCount + " caves", status, 1.0F);
        }
    }

    private static boolean near(AABB box, int cx, int cz, int radius) {
        int bx0 = ((int) Math.floor(box.minX)) >> 4, bx1 = ((int) Math.floor(box.maxX - 1)) >> 4;
        int bz0 = ((int) Math.floor(box.minZ)) >> 4, bz1 = ((int) Math.floor(box.maxZ - 1)) >> 4;
        return bx1 >= cx - radius && bx0 <= cx + radius && bz1 >= cz - radius && bz0 <= cz + radius;
    }

    private static void quad(WorldQuads quads, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int argb) {
        quads.quad(a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, d.x, d.y, d.z, argb);
    }

    private static void fillBox(WorldQuads q, AABB b, int argb) {
        double x0 = b.minX, y0 = b.minY, z0 = b.minZ, x1 = b.maxX, y1 = b.maxY, z1 = b.maxZ;
        q.quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, argb); // bottom
        q.quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, argb); // top
        q.quad(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, argb); // north
        q.quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, argb); // south
        q.quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, argb); // west
        q.quad(x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, argb); // east
    }
}
