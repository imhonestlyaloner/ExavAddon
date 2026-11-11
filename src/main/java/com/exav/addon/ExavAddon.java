package com.exav.addon;

import com.exav.addon.modules.ArtistEggs;
import com.exav.addon.modules.BetterGlint;
import com.exav.addon.modules.ChestPreview;
import com.exav.addon.modules.CustomCape;
import com.exav.addon.modules.EndSkyLyrics;
import com.exav.addon.modules.HolidayKnife;
import com.exav.addon.modules.KillEffects;
import com.exav.addon.modules.SusChunkPlus;
import com.exav.addon.modules.VideoPlayer;
import com.exav.addon.modules.VideoSkybox;
import com.exav.addon.sky.AlwaysEggs;
import com.exav.addon.util.MyEdits;
import net.anubis.client.addon.AnubisAddon;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;

public final class ExavAddon extends AnubisAddon {
    // clickgui category for the addon, gets set in onRegisterCategories (that runs before any module is made)
    public static ModuleCategory CATEGORY;

    @Override
    public void onRegisterCategories() {
        CATEGORY = ModuleCategory.register("EXAV", "Exav's Visual Addon");
    }

    @Override
    public void onInitialize() {
        MyEdits.init();
        AlwaysEggs.init();
        ModuleRegistry.add(new SusChunkPlus());
        ModuleRegistry.add(new HolidayKnife());
        ModuleRegistry.add(new EndSkyLyrics());
        ModuleRegistry.add(new ArtistEggs());
        ModuleRegistry.add(new VideoSkybox());
        ModuleRegistry.add(new CustomCape());
        ModuleRegistry.add(new ChestPreview());
        ModuleRegistry.add(new BetterGlint());
        ModuleRegistry.add(new KillEffects());
        ModuleRegistry.add(new VideoPlayer());
    }

    @Override
    public String getPackage() {
        return "com.exav.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("imhonestlyaloner", "ExavAddon");
    }
}
