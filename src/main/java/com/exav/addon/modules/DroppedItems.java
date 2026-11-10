package com.exav.addon.modules;

import com.exav.addon.ExavAddon;
import com.exav.addon.util.MyDrops;
import com.exav.addon.util.SusChunks;
import imgui.ImDrawList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.Render3DEvent;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.gui.WorldLabel;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.HudOverlayProvider;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ColorSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.util.FoglessLines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Highlights items lying on the ground that someone else dropped (yours are ignored), with a name/count label.
 */
public final class DroppedItems extends ClientModule implements HudOverlayProvider {
    private final BooleanSetting ignoreMine = addSetting("ignore-mine", new BooleanSetting("Ignore My Drops", true));
    private final BooleanSetting undergroundOnly = addSetting("underground-only", new BooleanSetting("Underground Only", false));
    private final BooleanSetting nearSusOnly = addSetting("near-sus-only", new BooleanSetting("Only Near Sus Chunks", false));
    private final NumberSetting susRadius = addSetting("sus-radius", new NumberSetting("Sus Chunk Radius", 1.0F, 0.0F, 4.0F).step(1.0F));
    private final NumberSetting maxDistance = addSetting("max-distance", new NumberSetting("Max Distance", 128.0F, 16.0F, 512.0F).step(16.0F));
    private final BooleanSetting labels = addSetting("labels", new BooleanSetting("Labels", true));
    private final NumberSetting maxLabels = addSetting("max-labels", new NumberSetting("Max Labels", 40.0F, 5.0F, 200.0F).step(5.0F));
    private final ColorSetting color = addSetting("color", new ColorSetting("Box Color", 0xFFFFD84A));
    private final NumberSetting lineWidth = addSetting("line-width", new NumberSetting("Line Width", 2.0F, 1.0F, 5.0F).step(0.5F));

    private final List<ItemEntity> shown = new ArrayList<>();
    private LongOpenHashSet susArea = new LongOpenHashSet();

    public DroppedItems() {
        super("Dropped Items", "Highlights items on the ground that someone else dropped. Your own drops are ignored.", ExavAddon.CATEGORY);
        susRadius.visibleWhen(nearSusOnly::get);
        maxLabels.visibleWhen(labels::get);
    }

    @Override
    protected void onDisable() {
        shown.clear();
    }

    @EventHandler
    public void onTick(TickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        shown.clear();
        if (level == null || mc.player == null) return;

        if (nearSusOnly.get()) susArea = SusChunks.around(SusChunks.flagged(SusChunks.BOTH), susRadius.getInt());
        double maxSq = maxDistance.get() * (double) maxDistance.get();
        Vec3 me = mc.player.position();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity item) || item.isRemoved()) continue;
            if (item.position().distanceToSqr(me) > maxSq) continue;
            if (ignoreMine.get() && MyDrops.isMine(item)) continue;
            if (undergroundOnly.get() && !underground(level, item)) continue;
            if (nearSusOnly.get() && !susArea.contains(ChunkPos.asLong(item.getBlockX() >> 4, item.getBlockZ() >> 4))) continue;
            shown.add(item);
        }
        shown.sort(Comparator.comparingDouble(e -> e.position().distanceToSqr(me)));
    }

    static boolean underground(ClientLevel level, Entity e) {
        return e.getBlockY() < level.getHeight(Heightmap.Types.WORLD_SURFACE, e.getBlockX(), e.getBlockZ()) - 3;
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (shown.isEmpty()) return;
        FoglessLines lines = FoglessLines.forFrame(event.partialTick);
        int argb = color.argb();
        float width = lineWidth.get();
        for (ItemEntity item : shown) {
            Vec3 at = item.getPosition(event.partialTick);
            AABB box = item.getBoundingBox().move(at.subtract(item.position())).inflate(0.1);
            lines.box(box, argb, width);
        }
    }

    @Override
    public boolean isHudOverlayEnabled() {
        return isEnabled() && labels.get() && !shown.isEmpty();
    }

    @Override
    public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
        Minecraft mc = Minecraft.getInstance();
        int limit = maxLabels.getInt();
        int n = 0;
        for (ItemEntity item : shown) {
            if (n++ >= limit) break;
            Vec3 pos = item.position().add(0.0, item.getBbHeight() + 0.4, 0.0);
            float[] screen = WorldLabel.project(mc, pos, screenW, screenH);
            if (screen == null) continue;
            int count = item.getItem().getCount();
            String name = item.getItem().getHoverName().getString() + (count > 1 ? " x" + count : "");
            String where = (int) Math.round(Math.sqrt(item.distanceToSqr(mc.player))) + "m";
            WorldLabel.drawText(dl, screen[0], screen[1], name, where, 1.0F);
        }
    }
}
