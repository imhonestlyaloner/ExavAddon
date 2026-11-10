package com.exav.addon.util;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.EventBus;
import net.anubis.client.event.impl.BlockUpdateEvent;
import net.anubis.client.event.impl.TickEvent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remembers every block you broke or placed yourself, per server and dimension, saved to
 * config/exav-addon/my-edits/. Base Finder and Exav's Relog Alternative leave these out, so your own
 * torches, tunnels and block changes never count as someone else's base.
 *
 * Breaks come from the game's own "block destroyed" call, placements from right-clicks on blocks that turn into
 * a block change a moment later. As a backup (for mining modules that skip the normal path) anything that
 * changes within reach right after you swing also counts as yours.
 */
public final class MyEdits {
    private static final Logger LOGGER = LoggerFactory.getLogger("Exav Addon");
    private static final MyEdits INSTANCE = new MyEdits();
    private static final long PLACE_WINDOW_MS = 3000;
    private static final long SWING_WINDOW_MS = 1000;
    private static final double REACH = 6.0;
    private static final long SAVE_EVERY_MS = 60_000;

    private final Long2LongOpenHashMap pendingPlace = new Long2LongOpenHashMap();
    private LongOpenHashSet edits = new LongOpenHashSet();
    private String key;
    private boolean dirty;
    private long lastSave;
    private long lastSwing;

    private MyEdits() {
    }

    /** Called once from the addon's init. */
    public static void init() {
        EventBus.INSTANCE.register(INSTANCE);
    }

    public static synchronized boolean isMine(long pos) {
        return INSTANCE.edits.contains(pos);
    }

    public static boolean isMine(int x, int y, int z) {
        return isMine(BlockPos.asLong(x, y, z));
    }

    /** From the mixin: the game just broke this block for you. */
    public static synchronized void broke(BlockPos pos) {
        INSTANCE.add(pos.asLong());
    }

    /** From the mixin: you right-clicked this face of this block; a placement may follow. */
    public static synchronized void clicked(BlockPos hit, Direction face) {
        long until = System.currentTimeMillis() + PLACE_WINDOW_MS;
        BlockPos placed = hit.relative(face);
        INSTANCE.pendingPlace.put(hit.asLong(), until);          // replaceable blocks (grass, snow) get replaced in place
        INSTANCE.pendingPlace.put(placed.asLong(), until);
        INSTANCE.pendingPlace.put(placed.above().asLong(), until); // doors, tall blocks
        INSTANCE.pendingPlace.put(placed.below().asLong(), until);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            INSTANCE.pendingPlace.put(placed.relative(d).asLong(), until); // beds
        }
    }

    private void add(long pos) {
        if (edits.add(pos)) dirty = true;
    }

    @EventHandler
    public void onBlockUpdate(BlockUpdateEvent event) {
        if (event.pos != null && event.oldState != null && event.newState != null) {
            record(event.pos, event.oldState.isAir(), event.newState.isAir(), event.oldState.getBlock() != event.newState.getBlock());
        }
    }

    /**
     * Checks a block change, remembers it if it's yours, and says whether it was.
     * Safe to call more than once for the same change (listeners run in any order).
     */
    public static boolean record(BlockPos blockPos, boolean wasAir, boolean nowAir, boolean changed) {
        long pos = blockPos.asLong();
        long now = System.currentTimeMillis();
        synchronized (MyEdits.class) {
            MyEdits me = INSTANCE;
            if (me.edits.contains(pos)) return true;
            if (!changed) return false;
            long until = me.pendingPlace.get(pos);
            if (until != 0 && until >= now) {
                me.add(pos);
                return true;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && now - me.lastSwing <= SWING_WINDOW_MS && wasAir != nowAir
                    && mc.player.getEyePosition().distanceToSqr(blockPos.getCenter()) <= REACH * REACH) {
                me.add(pos);
                return true;
            }
            return false;
        }
    }

    @EventHandler
    public void onTick(TickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        synchronized (MyEdits.class) {
            if (mc.player != null && mc.player.swinging) lastSwing = now;
            if (!pendingPlace.isEmpty()) {
                for (LongIterator it = pendingPlace.values().iterator(); it.hasNext(); ) {
                    if (it.nextLong() < now) it.remove();
                }
            }
            String next = worldKey(mc);
            if (next == null ? key != null : !next.equals(key)) {
                save();
                key = next;
                edits = load(key);
                dirty = false;
            } else if (dirty && now - lastSave > SAVE_EVERY_MS) {
                save();
            }
        }
    }

    private static String worldKey(Minecraft mc) {
        if (mc.level == null) return null;
        String server;
        ServerData data = mc.getCurrentServer();
        if (data != null) server = data.ip;
        else if (mc.getSingleplayerServer() != null) server = "singleplayer-" + mc.getSingleplayerServer().getWorldData().getLevelName();
        else server = "unknown";
        String dim = mc.level.dimension().identifier().toString();
        return (server + "__" + dim).replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static Path file(String key) {
        return FabricLoader.getInstance().getConfigDir().resolve("exav-addon").resolve("my-edits").resolve(key + ".bin");
    }

    private void save() {
        lastSave = System.currentTimeMillis();
        if (key == null || !dirty) return;
        dirty = false;
        Path path = file(key);
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            try (OutputStream raw = Files.newOutputStream(tmp); DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(raw))) {
                out.writeInt(edits.size());
                for (LongIterator it = edits.iterator(); it.hasNext(); ) out.writeLong(it.nextLong());
            }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.warn("Couldn't save your block edits to {}", path, e);
        }
    }

    private static LongOpenHashSet load(String key) {
        LongOpenHashSet set = new LongOpenHashSet();
        if (key == null) return set;
        Path path = file(key);
        if (!Files.exists(path)) return set;
        try (InputStream raw = Files.newInputStream(path); DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(raw))) {
            int n = in.readInt();
            for (int i = 0; i < n; i++) set.add(in.readLong());
        } catch (IOException e) {
            LOGGER.warn("Couldn't read your block edits from {}", path, e);
        }
        return set;
    }
}
