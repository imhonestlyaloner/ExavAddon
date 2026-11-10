package com.exav.addon.scan;

/** What the tunnel/cave scanners need to read from the world (the game's world, or a fake one in tests). */
public interface BlockSource {
    byte UNLOADED = 0;
    byte OPEN = 1;
    byte SOLID = 2;

    int minY();

    int maxY();

    byte get(int x, int y, int z);

    int surface(int x, int z);

    boolean isLoaded(int chunkX, int chunkZ);

    default boolean open(int x, int y, int z) {
        return get(x, y, z) == OPEN;
    }

    default boolean solid(int x, int y, int z) {
        return get(x, y, z) == SOLID;
    }
}
