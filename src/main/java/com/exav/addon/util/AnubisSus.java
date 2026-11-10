package com.exav.addon.util;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.lang.reflect.Field;
import net.anubis.client.module.ModuleRegistry;
import net.anubis.client.module.impl.render.BetterSusChunkModule;
import net.anubis.client.module.impl.render.SusChunkFinderModule;
import net.anubis.client.module.setting.NumberSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the scores Anubis's sus chunk modules keep internally (they only expose which chunks are flagged).
 * Everything is read defensively: if a future Anubis renames something, these return empty / null
 * and the confidence module just works with less information.
 *
 * Sus Chunk Finder: "heat" per chunk = grown plants within simulation distance, per plant kind, minus "vetoes".
 * Better Sus Chunk: "support" per chunk.
 */
public final class AnubisSus {
    private static final Logger LOGGER = LoggerFactory.getLogger("Exav Addon");
    private static boolean warned;

    private AnubisSus() {
    }

    public static SusChunkFinderModule finder() {
        SusChunkFinderModule m = ModuleRegistry.get(SusChunkFinderModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    public static BetterSusChunkModule better() {
        BetterSusChunkModule m = ModuleRegistry.get(BetterSusChunkModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    /** Sus Chunk Finder's total heat per chunk (all plant kinds, vetoed kinds left out), the same field it picks peaks from. */
    public static Long2IntOpenHashMap finderHeat() {
        Long2IntOpenHashMap total = new Long2IntOpenHashMap();
        SusChunkFinderModule m = finder();
        if (m == null) return total;
        Object heat = get(m, "heat");
        Object vetoes = get(m, "vetoes");
        if (!(heat instanceof Long2IntOpenHashMap[] heats) || !(vetoes instanceof Long2IntOpenHashMap[] vetos)) return total;
        for (int kind = 0; kind < heats.length; kind++) {
            Long2IntOpenHashMap veto = kind < vetos.length ? vetos[kind] : null;
            for (Long2IntMap.Entry e : heats[kind].long2IntEntrySet()) {
                if (veto == null || !veto.containsKey(e.getLongKey())) total.addTo(e.getLongKey(), e.getIntValue());
            }
        }
        return total;
    }

    /** Better Sus Chunk's support per chunk (copied under its lock). */
    public static Long2IntOpenHashMap betterSupport() {
        BetterSusChunkModule m = better();
        if (m == null) return new Long2IntOpenHashMap();
        Object support = get(m, "support");
        Object lock = get(m, "lock");
        if (!(support instanceof Long2IntOpenHashMap map)) return new Long2IntOpenHashMap();
        if (lock == null) return new Long2IntOpenHashMap(map);
        synchronized (lock) {
            return new Long2IntOpenHashMap(map);
        }
    }

    public static NumberSetting finderSimDistance() {
        SusChunkFinderModule m = ModuleRegistry.get(SusChunkFinderModule.class);
        return m == null ? null : setting(m, "spreadRadius");
    }

    public static NumberSetting finderSensitivity() {
        SusChunkFinderModule m = ModuleRegistry.get(SusChunkFinderModule.class);
        return m == null ? null : setting(m, "sensitivity");
    }

    public static NumberSetting betterSimDistance() {
        BetterSusChunkModule m = ModuleRegistry.get(BetterSusChunkModule.class);
        return m == null ? null : setting(m, "simDistance");
    }

    public static NumberSetting betterSensitivity() {
        BetterSusChunkModule m = ModuleRegistry.get(BetterSusChunkModule.class);
        return m == null ? null : setting(m, "sensitivity");
    }

    private static NumberSetting setting(Object module, String field) {
        return get(module, field) instanceof NumberSetting s ? s : null;
    }

    private static Object get(Object target, String name) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!warned) {
                warned = true;
                LOGGER.warn("Couldn't read {}.{} from Anubis, sus confidence will use less information", target.getClass().getSimpleName(), name, e);
            }
            return null;
        }
    }
}
