package com.exav.addon.modules;

import com.exav.addon.ExavAddon;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.impl.render.SkyLyrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes the Spotify HUD's Sky Lyrics show up in the End. Everything about how they look (size, height, colours,
 * glow, title, emojis...) still comes from the Spotify HUD's own sky lyrics settings; turn those on there.
 */
public final class EndSkyLyrics extends ClientModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("Exav Addon");
    private static volatile EndSkyLyrics active;
    private static boolean failed;

    public EndSkyLyrics() {
        super("End Sky Lyrics", "Shows the Spotify HUD's sky lyrics in the End too (set them up in Spotify HUD).", ExavAddon.CATEGORY);
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    protected void onEnable() {
        active = this;
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    /** Called from the sky pass right after the End sky is drawn. */
    public static void draw() {
        if (active == null || failed) return;
        try {
            SkyLyrics.render();
        } catch (Throwable t) {
            // never take the game down over lyrics
            failed = true;
            LOGGER.error("End sky lyrics stopped drawing", t);
        }
    }
}
