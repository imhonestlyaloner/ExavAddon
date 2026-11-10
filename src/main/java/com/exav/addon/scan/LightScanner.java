package com.exav.addon.scan;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Finds abnormal light in the caves around a sus chunk: cave air that's lit brighter than any light source
 * you can see nearby could make it. That means the light comes from something you can't see (or that hasn't
 * loaded), like torches in a closed-off room.
 *
 * Step 1 collects every light-emitting block (torches, lava, glow lichen...) in the area plus a chunk of margin.
 * Step 2 checks each cave air block: the brightest a visible source could make it is
 * (source brightness - distance in blocks), ignoring walls, so real light can never be above that.
 * If the client's block light there is 2+ levels higher, it's counted as hidden light.
 */
public final class LightScanner {
    private static final int TOLERANCE = 2;

    private final BlockReader reader;
    private final List<long[]> caves;
    private final int cx0, cz0, cx1, cz1; // chunk bounds for collecting light sources
    /** light sources bucketed by 16x16x16 cell: packed pos and brightness */
    private final Long2ObjectOpenHashMap<LongArrayList> sources = new Long2ObjectOpenHashMap<>();
    private final LongArrayList hidden = new LongArrayList();
    private int scx, scz, section;
    private boolean collected;
    private int cave, index;

    public LightScanner(BlockReader reader, List<long[]> caves, int chunkX, int chunkZ, int radiusChunks) {
        this.reader = reader;
        this.caves = caves;
        this.cx0 = chunkX - radiusChunks - 1;
        this.cz0 = chunkZ - radiusChunks - 1;
        this.cx1 = chunkX + radiusChunks + 1;
        this.cz1 = chunkZ + radiusChunks + 1;
        this.scx = cx0;
        this.scz = cz0;
    }

    /** positions of hidden-light cave blocks */
    public LongArrayList results() {
        return hidden;
    }

    public boolean step(long deadline) {
        if (!collected) {
            if (!collect(deadline)) return false;
            collected = true;
        }
        int ops = 0;
        while (cave < caves.size()) {
            long[] cells = caves.get(cave);
            if (index >= cells.length) {
                cave++;
                index = 0;
                continue;
            }
            check(cells[index++]);
            if (++ops % 256 == 0 && System.nanoTime() > deadline) return false;
        }
        return true;
    }

    private boolean collect(long deadline) {
        while (scx <= cx1) {
            LevelChunk chunk = reader.chunk(scx, scz);
            if (chunk != null) {
                LevelChunkSection[] sections = chunk.getSections();
                while (section < sections.length) {
                    LevelChunkSection s = sections[section];
                    int baseY = chunk.getSectionYFromSectionIndex(section) << 4;
                    section++;
                    if (s == null || s.hasOnlyAir() || !s.getStates().maybeHas(st -> st.getLightEmission() > 0)) continue;
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                BlockState st = s.getBlockState(x, y, z);
                                int emit = st.getLightEmission();
                                if (emit > 0) addSource((scx << 4) + x, baseY + y, (scz << 4) + z, emit);
                            }
                        }
                    }
                    if (System.nanoTime() > deadline) return false;
                }
            }
            section = 0;
            if (++scz > cz1) {
                scz = cz0;
                scx++;
            }
        }
        return true;
    }

    private void addSource(int x, int y, int z, int emit) {
        long cell = BlockPos.asLong(x >> 4, y >> 4, z >> 4);
        // pack brightness into the list next to the position
        LongArrayList list = sources.computeIfAbsent(cell, k -> new LongArrayList());
        list.add(BlockPos.asLong(x, y, z));
        list.add(emit);
    }

    private void check(long p) {
        int x = BlockPos.getX(p), y = BlockPos.getY(p), z = BlockPos.getZ(p);
        int observed = reader.blockLight(x, y, z);
        if (observed < TOLERANCE + 1) return;
        // light sources up to 15 blocks away could reach here; skip if any of that area isn't loaded
        if (!reader.isLoaded((x - 15) >> 4, (z - 15) >> 4) || !reader.isLoaded((x + 15) >> 4, (z + 15) >> 4)
                || !reader.isLoaded((x - 15) >> 4, (z + 15) >> 4) || !reader.isLoaded((x + 15) >> 4, (z - 15) >> 4)) return;
        if (observed - possible(x, y, z, observed) >= TOLERANCE) hidden.add(p);
    }

    /** brightest any known source could make this block (stops early once it explains what we see) */
    private int possible(int x, int y, int z, int observed) {
        int best = 0;
        int bx = x >> 4, by = y >> 4, bz = z >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    LongArrayList list = sources.get(BlockPos.asLong(bx + dx, by + dy, bz + dz));
                    if (list == null) continue;
                    for (int i = 0; i < list.size(); i += 2) {
                        long s = list.getLong(i);
                        int dist = Math.abs(BlockPos.getX(s) - x) + Math.abs(BlockPos.getY(s) - y) + Math.abs(BlockPos.getZ(s) - z);
                        int light = (int) list.getLong(i + 1) - dist;
                        if (light > best) {
                            best = light;
                            if (best > observed - TOLERANCE) return best;
                        }
                    }
                }
            }
        }
        return best;
    }
}
