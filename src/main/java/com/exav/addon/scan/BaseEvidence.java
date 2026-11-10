package com.exav.addon.scan;

import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Counts signs of players in the underground part of one chunk and turns them into a rough base likelihood.
 * Runs one 16x16x16 section per step so it can be spread over ticks.
 */
public final class BaseEvidence {
    // block kinds
    public static final byte NONE = 0;
    static final byte RARE_STORAGE = 1; // shulker boxes, ender chests: never generate naturally underground
    static final byte STORAGE = 2;      // chests, barrels, hoppers, droppers, dispensers
    static final byte UTILITY = 3;      // crafting/furnaces/anvils/enchanting/brewing/beds...
    static final byte SIGN = 4;
    static final byte LIGHT = 5;        // torches, lanterns
    static final byte BUILD = 6;        // wool, concrete, glass, carpet, terracotta
    static final byte REDSTONE = 7;     // repeaters, comparators, pistons, observers, dust
    static final byte PORTAL = 8;       // nether portal blocks
    static final byte OBSIDIAN = 9;
    static final byte STRUCTURE = 10;   // spawners, cobwebs, end portal frames, sculk shriekers, trial spawners, vaults...
    static final byte PLACED = 11;      // cobblestone, cobbled deepslate, netherrack...: what players pillar/patch with
    public static final int KINDS = 12;

    private static final Reference2ByteOpenHashMap<Block> KIND_CACHE = new Reference2ByteOpenHashMap<>();

    private final BlockReader reader;
    private final LevelChunk chunk;
    private final int chunkX;
    private final int chunkZ;
    private final boolean undergroundOnly;
    private final int[] surface = new int[256];
    private final int[] counts = new int[KINDS];
    private int sectionIndex;

    public BaseEvidence(BlockReader reader, LevelChunk chunk, int chunkX, int chunkZ, boolean undergroundOnly) {
        this.reader = reader;
        this.chunk = chunk;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.undergroundOnly = undergroundOnly;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // a few blocks of margin so houses / farms sitting on the surface don't count
                surface[x << 4 | z] = reader.surface((chunkX << 4) + x, (chunkZ << 4) + z) - 3;
            }
        }
    }

    /** @return true once every section has been scanned */
    public boolean step(long deadline) {
        LevelChunkSection[] sections = chunk.getSections();
        while (sectionIndex < sections.length) {
            scanSection(sections[sectionIndex], chunk.getSectionYFromSectionIndex(sectionIndex) << 4);
            sectionIndex++;
            if (System.nanoTime() > deadline) break;
        }
        return sectionIndex >= sections.length;
    }

    private void scanSection(LevelChunkSection section, int baseY) {
        if (section == null || section.hasOnlyAir()) return;
        // palette check: skip sections that can't contain anything we care about (most of them)
        if (!section.getStates().maybeHas(state -> kind(state.getBlock()) != NONE)) return;
        for (int y = 0; y < 16; y++) {
            int wy = baseY + y;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (undergroundOnly && wy >= surface[x << 4 | z]) continue;
                    BlockState state = section.getBlockState(x, y, z);
                    byte k = kind(state.getBlock());
                    if (k != NONE) counts[k]++;
                }
            }
        }
    }

    public int[] counts() {
        return counts.clone();
    }

    /** Everything found for one flagged chunk, before live edits are added in. */
    public record Counts(int[] chunk, int[] caves, int tunnels, int branchMines, boolean flaggedByBoth) {
    }

    public record Result(int percent, String summary) {
    }

    /**
     * Not a calibrated probability: a prior from the sus flag, raised by weighted evidence with diminishing returns.
     * p = 1 - (1 - prior) * e^(-score / 3)
     * Stuff in the nearby caves counts a bit less than stuff right under the chunk.
     */
    public static Result score(Counts c, int liveEdits, int lastEditMinutesAgo) {
        int[] k = new int[KINDS];
        double[] v = new double[KINDS];
        for (int i = 0; i < KINDS; i++) {
            k[i] = c.chunk()[i] + c.caves()[i];
            v[i] = c.chunk()[i] + 0.6 * c.caves()[i];
        }
        boolean structure = k[STRUCTURE] > 0;
        double weak = 1.0 * Math.min(v[STORAGE], 6)
                + 1.5 * Math.min(v[UTILITY], 6)
                + 1.0 * Math.min(v[SIGN], 4)
                + 0.25 * Math.min(v[LIGHT], 16)
                + 0.04 * Math.min(v[BUILD], 100)
                + 0.6 * Math.min(v[REDSTONE], 10)
                + 0.3 * Math.min(v[OBSIDIAN], 10)
                + 0.15 * Math.min(v[PLACED], 30);
        // dungeons, mineshafts, strongholds, ancient cities and trial chambers have chests, torches, wool etc. too
        if (structure) weak *= 0.35;
        double strong = 3.0 * Math.min(v[RARE_STORAGE], 4)
                + 2.5 * Math.min(v[PORTAL], 1)
                + 1.2 * Math.min(c.tunnels(), 5)
                + 1.5 * Math.min(c.branchMines(), 4)
                + 0.4 * Math.min(liveEdits, 12);
        double prior = c.flaggedByBoth() ? 0.18 : 0.10;
        double p = 1.0 - (1.0 - prior) * Math.exp(-(weak + strong) / 3.0);
        int percent = (int) Math.round(Math.max(1.0, Math.min(99.0, p * 100.0)));

        List<String> parts = new ArrayList<>();
        if (liveEdits > 0) parts.add(liveEdits + " live edit" + (liveEdits == 1 ? "" : "s") + " (" + lastEditMinutesAgo + "m ago)");
        add(parts, k[RARE_STORAGE], "shulker/echest");
        add(parts, k[STORAGE], "storage");
        add(parts, k[UTILITY], "station");
        add(parts, k[SIGN], "sign");
        add(parts, c.tunnels(), "3x3 tunnel");
        add(parts, c.branchMines(), "branch mine");
        add(parts, k[PORTAL] > 0 ? 1 : 0, "portal");
        add(parts, k[PLACED], "placed block");
        add(parts, k[REDSTONE], "redstone");
        add(parts, k[LIGHT], "light");
        add(parts, k[BUILD], "build block");
        if (structure) parts.add("near structure");
        if (c.flaggedByBoth()) parts.add("both flagged");
        return new Result(percent, parts.isEmpty() ? "no evidence loaded" : String.join(" · ", parts));
    }

    private static void add(List<String> parts, int n, String what) {
        if (n > 0) parts.add(n + " " + what);
    }

    public static byte kind(Block block) {
        synchronized (KIND_CACHE) {
            if (KIND_CACHE.containsKey(block)) return KIND_CACHE.getByte(block);
            byte k = classify(BuiltInRegistries.BLOCK.getKey(block).getPath());
            KIND_CACHE.put(block, k);
            return k;
        }
    }

    static byte classify(String id) {
        if (id.equals("mossy_cobblestone") || id.startsWith("mossy_cobblestone_")) return STRUCTURE;
        if (id.equals("cobblestone") || id.startsWith("cobblestone_") || id.equals("cobbled_deepslate")
                || id.startsWith("cobbled_deepslate_") || id.equals("netherrack") || id.equals("smooth_stone")
                || id.equals("smooth_stone_slab") || id.equals("bricks") || id.equals("dirt_path")
                || id.equals("farmland") || id.equals("hay_block")) return PLACED;
        if (id.endsWith("shulker_box") || id.equals("ender_chest")) return RARE_STORAGE;
        if (id.equals("chest") || id.equals("trapped_chest") || id.equals("barrel") || id.equals("hopper")
                || id.equals("dropper") || id.equals("dispenser")) return STORAGE;
        if (id.equals("crafting_table") || id.equals("furnace") || id.equals("blast_furnace") || id.equals("smoker")
                || id.equals("enchanting_table") || id.equals("brewing_stand") || id.equals("smithing_table")
                || id.equals("grindstone") || id.endsWith("anvil") || id.endsWith("_bed") || id.equals("respawn_anchor")
                || id.equals("beacon") || id.equals("crafter") || id.equals("jukebox") || id.equals("note_block")
                || id.equals("loom") || id.equals("cartography_table") || id.equals("stonecutter")) return UTILITY;
        if (id.endsWith("_sign") || id.endsWith("_hanging_sign")) return SIGN;
        if (id.endsWith("torch") || id.endsWith("lantern") && !id.equals("sea_lantern") || id.equals("glowstone")
                || id.equals("shroomlight") || id.equals("end_rod")) return LIGHT;
        if (id.endsWith("_wool") || id.endsWith("_concrete") || id.endsWith("_concrete_powder") || id.endsWith("glass")
                || id.endsWith("glass_pane") || id.endsWith("_carpet") || id.endsWith("glazed_terracotta")
                // no planks/fences/rails: mineshafts are full of them
                || id.endsWith("_door") || id.endsWith("_trapdoor") || id.equals("ladder")
                || id.equals("scaffolding") || id.equals("iron_block") || id.equals("gold_block") || id.equals("diamond_block")
                || id.equals("netherite_block") || id.equals("emerald_block")) return BUILD;
        if (id.equals("repeater") || id.equals("comparator") || id.equals("piston") || id.equals("sticky_piston")
                || id.equals("observer") || id.equals("redstone_wire") || id.equals("redstone_block") || id.equals("lever")
                || id.endsWith("_button") || id.endsWith("_pressure_plate") || id.equals("target")) return REDSTONE;
        if (id.equals("nether_portal")) return PORTAL;
        if (id.equals("obsidian") || id.equals("crying_obsidian")) return OBSIDIAN;
        if (id.equals("spawner") || id.equals("trial_spawner") || id.equals("vault") || id.equals("cobweb")
                || id.equals("end_portal_frame") || id.equals("sculk_shrieker") || id.equals("reinforced_deepslate")
                || id.equals("mossy_cobblestone") || id.startsWith("infested_") || id.equals("suspicious_gravel")) return STRUCTURE;
        return NONE;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }
}
