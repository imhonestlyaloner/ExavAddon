package com.exav.addon.scan;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Fast block lookups on the client level, with a one-chunk cache. Unloaded chunks read as UNLOADED. */
public final class BlockReader {
    public static final byte UNLOADED = 0;
    public static final byte OPEN = 1;
    public static final byte SOLID = 2;

    private final ClientLevel level;
    private final int minY;
    private final int maxY; // exclusive
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private long cachedKey = Long.MIN_VALUE;
    private LevelChunk cached;

    public BlockReader(ClientLevel level) {
        this.level = level;
        this.minY = level.getMinY();
        this.maxY = level.getMinY() + level.getHeight();
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public LevelChunk chunk(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        if (key != cachedKey) {
            cachedKey = key;
            LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
            cached = chunk == null || chunk instanceof EmptyLevelChunk ? null : chunk;
        }
        return cached;
    }

    public boolean isLoaded(int chunkX, int chunkZ) {
        return chunk(chunkX, chunkZ) != null;
    }

    /** OPEN = nothing to collide with and no fluid (air, torches, rails...), SOLID = anything else. */
    public byte get(int x, int y, int z) {
        if (y < minY) return SOLID;
        if (y >= maxY) return OPEN;
        LevelChunk chunk = chunk(x >> 4, z >> 4);
        if (chunk == null) return UNLOADED;
        pos.set(x, y, z);
        BlockState state = chunk.getBlockState(pos);
        if (state.isAir()) return OPEN;
        if (state.getFluidState().isEmpty() && state.getCollisionShape(chunk, pos).isEmpty()) return OPEN;
        return SOLID;
    }

    /** The block state, or null if the chunk isn't loaded / outside the world. */
    public BlockState state(int x, int y, int z) {
        if (y < minY || y >= maxY) return null;
        LevelChunk chunk = chunk(x >> 4, z >> 4);
        if (chunk == null) return null;
        pos.set(x, y, z);
        return chunk.getBlockState(pos);
    }

    public boolean open(int x, int y, int z) {
        return get(x, y, z) == OPEN;
    }

    public boolean solid(int x, int y, int z) {
        return get(x, y, z) == SOLID;
    }

    /** Y of the highest non-air block in the column, or minY - 1 if the chunk isn't loaded. */
    public int surface(int x, int z) {
        LevelChunk chunk = chunk(x >> 4, z >> 4);
        if (chunk == null) return minY - 1;
        return chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
    }
}
