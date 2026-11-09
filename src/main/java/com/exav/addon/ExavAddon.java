package com.exav.addon;

import com.exav.addon.modules.ExampleModule;
import net.anubis.client.addon.AnubisAddon;
import net.anubis.client.module.ModuleCategory;
import net.anubis.client.module.ModuleRegistry;

public final class ExavAddon extends AnubisAddon {
    // clickgui category for the addon, gets set in onRegisterCategories (that runs before any module is made)
    public static ModuleCategory CATEGORY;

    @Override
    public void onRegisterCategories() {
        CATEGORY = ModuleCategory.register("EXAV", "Exav");
    }

    @Override
    public void onInitialize() {
        ModuleRegistry.add(new ExampleModule());
    }

    @Override
    public String getPackage() {
        return "com.exav.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("your-name", "exav-addon");
    }
}
