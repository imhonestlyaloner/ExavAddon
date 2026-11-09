package com.exav.addon.modules;

import com.exav.addon.ExavAddon;
import net.anubis.client.module.ClientModule;

public final class ExampleModule extends ClientModule {
    public ExampleModule() {
        super("Example Module", "Does nothing, just turns on and off.", ExavAddon.CATEGORY);
    }
}
