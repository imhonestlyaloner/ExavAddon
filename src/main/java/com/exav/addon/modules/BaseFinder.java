package com.exav.addon.modules;

import com.exav.addon.ExavAddon;
import com.exav.addon.scan.BaseEvidence;
import com.exav.addon.scan.BlockReader;
import com.exav.addon.scan.TunnelScanner;
import com.exav.addon.util.SusChunks;
import imgui.ImDrawList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.gui.WorldLabel;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.HudOverlayProvider;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * Puts a "Base xx%" label over every flagged sus chunk, estimated from the player-made stuff found underneath it.
 */
public final class BaseFinder extends ClientModule implements HudOverlayProvider {
    private final ModeSetting source = addSetting("source", new ModeSetting("Source", SusChunks.BOTH, SusChunks.SOURCES));
    private final BooleanSetting undergroundOnly = addSetting("underground-only", new BooleanSetting("Underground Only", true));
    private final BooleanSetting countTunnels = addSetting("count-tunnels", new BooleanSetting("Count 3x3 Tunnels", true));
    private final NumberSetting minPercent = addSetting("min-percent", new NumberSetting("Hide Below %", 0.0F, 0.0F, 90.0F).step(5.0F));
    private final BooleanSetting showDetails = addSetting("show-details", new BooleanSetting("Show Evidence", true));
    private final NumberSetting labelY = addSetting("label-y", new NumberSetting("Label Height", 66.0F, -60.0F, 320.0F).step(1.0F));
    private final NumberSetting maxDistance = addSetting("max-distance", new NumberSetting("Max Label Distance", 512.0F, 32.0F, 2048.0F).step(32.0F));
    private final NumberSetting rescanSeconds = addSetting("rescan-seconds", new NumberSetting("Rescan Every (s)", 15.0F, 5.0F, 120.0F).step(5.0F));
    private final NumberSetting budgetMs = addSetting("budget-ms", new NumberSetting("Scan Budget (ms/tick)", 2.0F, 1.0F, 10.0F).step(1.0F));

    private final Long2ObjectOpenHashMap<BaseEvidence.Result> results = new Long2ObjectOpenHashMap<>();
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    private final LongOpenHashSet queued = new LongOpenHashSet();
    private Job current;
    private BlockReader reader;
    private ClientLevel lastLevel;
    private int ticks;
    private boolean settingsChanged;

    /** evidence scan, then (optionally) a tunnel scan of the same chunk */
    private record Job(long key, BaseEvidence evidence, TunnelScanner tunnels, boolean[] evidenceDone) {
        boolean step(long deadline) {
            if (!evidenceDone[0]) {
                evidenceDone[0] = evidence.step(deadline);
                if (!evidenceDone[0]) return false;
            }
            return tunnels == null || tunnels.step(deadline);
        }
    }

    public BaseFinder() {
        super("Base Finder", "Shows the % chance there's a base under each flagged sus chunk, from what's loaded underneath it.", ExavAddon.CATEGORY);
        Runnable rescan = () -> settingsChanged = true;
        source.onChange(rescan);
        undergroundOnly.onChange(rescan);
        countTunnels.onChange(rescan);
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
        reader = null;
        lastLevel = null;
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
        for (LongIterator it = results.keySet().iterator(); it.hasNext(); ) {
            if (!flagged.contains(it.nextLong())) it.remove();
        }
        if (current != null && !flagged.contains(current.key())) current = null;

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
                int cx = ChunkPos.getX(key);
                int cz = ChunkPos.getZ(key);
                LevelChunk chunk = reader.chunk(cx, cz);
                if (chunk == null) continue;
                current = new Job(key,
                        new BaseEvidence(reader, chunk, cx, cz, undergroundOnly.get()),
                        countTunnels.get() ? new TunnelScanner(reader, cx, cz, 8) : null,
                        new boolean[1]);
            }
            if (current.step(deadline)) {
                int tunnels = current.tunnels() == null ? 0 : current.tunnels().results().size();
                LongSet finder = SusChunks.finder();
                LongSet better = SusChunks.better();
                boolean both = finder.contains(current.key()) && better.contains(current.key());
                results.put(current.key(), current.evidence().result(tunnels, both));
                current = null;
            }
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
        for (var entry : results.long2ObjectEntrySet()) {
            BaseEvidence.Result r = entry.getValue();
            if (r.percent() < hideBelow) continue;
            long key = entry.getLongKey();
            Vec3 pos = new Vec3((ChunkPos.getX(key) << 4) + 8.0, y, (ChunkPos.getZ(key) << 4) + 8.0);
            double dx = pos.x - eye.x, dz = pos.z - eye.z;
            if (dx * dx + dz * dz > maxSq) continue;
            float[] screen = WorldLabel.project(mc, pos, screenW, screenH);
            if (screen == null) continue;
            WorldLabel.drawText(dl, screen[0], screen[1], "Base " + r.percent() + "%", showDetails.get() ? r.summary() : null, 1.0F);
        }
    }
}
