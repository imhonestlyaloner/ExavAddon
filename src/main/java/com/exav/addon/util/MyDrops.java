package com.exav.addon.util;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayDeque;
import java.util.Iterator;
import net.anubis.client.event.EventBus;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.event.impl.WorldEntityAddEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Works out which dropped items are yours, at the moment they appear:
 * items that show up next to you right after you press drop / throw from your inventory,
 * items that pop out of blocks you just broke, drops of mobs you just hit, and your own death drops.
 */
public final class MyDrops {
    private static final MyDrops INSTANCE = new MyDrops();
    private static final long DROP_WINDOW_MS = 2000;
    private static final long BREAK_WINDOW_MS = 2500;
    private static final long ATTACK_WINDOW_MS = 4000;
    private static final long DEATH_WINDOW_MS = 3000;

    private record Break(Vec3 pos, long time) {
    }

    private final IntOpenHashSet mine = new IntOpenHashSet();
    private final ArrayDeque<Break> breaks = new ArrayDeque<>();
    private final Int2LongOpenHashMap attackedAt = new Int2LongOpenHashMap();
    private final Int2ObjectOpenHashMap<Vec3> attackedPos = new Int2ObjectOpenHashMap<>();
    private long lastDrop;
    private long deathTime;
    private Vec3 deathPos;
    private boolean wasDead;
    private ClientLevel level;

    private MyDrops() {
    }

    public static void init() {
        EventBus.INSTANCE.register(INSTANCE);
    }

    /** Is this item entity one you dropped (or caused to drop)? */
    public static synchronized boolean isMine(ItemEntity item) {
        return INSTANCE.mine.contains(item.getId());
    }

    public static synchronized void dropped() {
        INSTANCE.lastDrop = System.currentTimeMillis();
    }

    public static synchronized void broke(BlockPos pos) {
        INSTANCE.breaks.addLast(new Break(pos.getCenter(), System.currentTimeMillis()));
    }

    public static synchronized void attacked(Entity entity) {
        INSTANCE.attackedAt.put(entity.getId(), System.currentTimeMillis());
        INSTANCE.attackedPos.put(entity.getId(), entity.position());
    }

    @EventHandler
    public void onEntityAdd(WorldEntityAddEvent event) {
        if (!(event.entity instanceof ItemEntity item)) return;
        synchronized (MyDrops.class) {
            if (causedByMe(item.position())) mine.add(item.getId());
        }
    }

    private boolean causedByMe(Vec3 pos) {
        long now = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && now - lastDrop <= DROP_WINDOW_MS && pos.distanceTo(mc.player.getEyePosition()) <= 3.5) return true;
        for (Break b : breaks) {
            if (now - b.time <= BREAK_WINDOW_MS && pos.distanceTo(b.pos) <= 2.5) return true;
        }
        for (var e : attackedAt.int2LongEntrySet()) {
            Vec3 at = attackedPos.get(e.getIntKey());
            if (now - e.getLongValue() <= ATTACK_WINDOW_MS && at != null && pos.distanceTo(at) <= 3.0) return true;
        }
        return deathPos != null && now - deathTime <= DEATH_WINDOW_MS && pos.distanceTo(deathPos) <= 5.0;
    }

    @EventHandler
    public void onTick(TickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        synchronized (MyDrops.class) {
            if (mc.level != level) {
                level = mc.level;
                mine.clear();
                breaks.clear();
                attackedAt.clear();
                attackedPos.clear();
                deathPos = null;
            }
            if (mc.level == null) return;
            while (!breaks.isEmpty() && now - breaks.peekFirst().time > BREAK_WINDOW_MS) breaks.removeFirst();
            // follow the mobs you hit so their drops are recognised where they die
            for (Iterator<Int2LongOpenHashMap.Entry> it = attackedAt.int2LongEntrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                if (now - e.getLongValue() > ATTACK_WINDOW_MS) {
                    attackedPos.remove(e.getIntKey());
                    it.remove();
                    continue;
                }
                Entity target = mc.level.getEntity(e.getIntKey());
                if (target != null) attackedPos.put(e.getIntKey(), target.position());
            }
            boolean dead = mc.player != null && mc.player.isDeadOrDying();
            if (dead && !wasDead) {
                deathTime = now;
                deathPos = mc.player.position();
            }
            wasDead = dead;
            // forget ids of items that are gone
            if (mc.level.getGameTime() % 200 == 0 && !mine.isEmpty()) {
                mine.removeIf(id -> mc.level.getEntity(id) == null);
            }
        }
    }
}
