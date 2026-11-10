package com.exav.addon.modules;

import com.exav.addon.ExavAddon;
import com.exav.addon.sky.AlwaysEggs;
import com.exav.addon.sky.VideoSky;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A skybox made from the cat video: a dark starry sky with a ring of curved screens around the horizon all
 * playing the video on loop. Drawn in the sky pass, so terrain and clouds stay in front of it. A Christmas song
 * still takes over the sky while it plays.
 */
public final class VideoSkybox extends ClientModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("Exav Addon");
    private static volatile VideoSkybox active;
    private static boolean failed;
    private static final SoundEvent SOUND = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("exav-addon", "cat_video"));

    private final NumberSetting screens = addSetting("screens", new NumberSetting("Screens", 6.0F, 1.0F, 8.0F).step(1.0F));
    private final NumberSetting screenSize = addSetting("screen-size", new NumberSetting("Screen Size", 46.0F, 25.0F, 90.0F).step(1.0F));
    private final NumberSetting height = addSetting("height", new NumberSetting("Screen Height", 4.0F, -10.0F, 40.0F).step(1.0F));
    private final NumberSetting spin = addSetting("spin", new NumberSetting("Spin Speed", 1.5F, 0.0F, 10.0F).step(0.5F));
    private final NumberSetting brightness = addSetting("brightness", new NumberSetting("Brightness", 1.0F, 0.3F, 1.5F).step(0.05F));
    private final NumberSetting speed = addSetting("speed", new NumberSetting("Video Speed", 1.0F, 0.25F, 2.0F).step(0.05F));
    private final BooleanSetting background = addSetting("background", new BooleanSetting("Dark Starry Background", true));
    private final BooleanSetting sound = addSetting("sound", new BooleanSetting("Play Video Sound", false));
    private final NumberSetting volume = addSetting("volume", new NumberSetting("Sound Volume", 0.6F, 0.0F, 1.0F).step(0.05F));

    private long startedAt;
    private long lastLoop = -1;
    private float fade;
    private SoundInstance playingSound;

    public VideoSkybox() {
        super("Video Skybox", "Turns the sky into a starry night with screens playing the cat video all around you.", ExavAddon.CATEGORY);
        volume.visibleWhen(sound::get);
    }

    @Override
    protected void onEnable() {
        active = this;
        startedAt = System.nanoTime();
        lastLoop = -1;
        fade = 0;
    }

    @Override
    protected void onDisable() {
        active = null;
        stopSound();
    }

    private float videoTime() {
        return (System.nanoTime() - startedAt) / 1.0E9F * speed.get();
    }

    @EventHandler
    public void onTick(TickEvent event) {
        boolean show = !AlwaysEggs.christmas();
        fade = show ? Math.min(1, fade + 0.05F) : Math.max(0, fade - 0.05F);
        // the video's own sound, restarted each time the video loops
        if (sound.get() && show && Minecraft.getInstance().level != null) {
            long loop = (long) Math.floor(videoTime() / VideoSky.LENGTH);
            if (loop != lastLoop) {
                lastLoop = loop;
                stopSound();
                playingSound = SimpleSoundInstance.forUI(SOUND, speed.get(), volume.get());
                Minecraft.getInstance().getSoundManager().play(playingSound);
            }
        } else if (playingSound != null) {
            stopSound();
            lastLoop = -1;
        }
    }

    private void stopSound() {
        if (playingSound != null) {
            Minecraft.getInstance().getSoundManager().stop(playingSound);
            playingSound = null;
        }
    }

    /** from the sky pass mixin, after the normal sky (and Anubis's) is drawn */
    public static void render() {
        VideoSkybox m = active;
        if (m == null || failed || m.fade <= 0) return;
        try {
            float spinDeg = (System.nanoTime() % 3_600_000_000_000L) / 1.0E9F * m.spin.get();
            VideoSky.draw(m.videoTime(), new VideoSky.Look(m.screens.getInt(), m.screenSize.get(), m.height.get(), spinDeg,
                    m.brightness.get(), m.background.get(), VideoSky.TOP, VideoSky.HORIZON, m.fade));
        } catch (Throwable t) {
            failed = true; // never take the game down over the sky
            LOGGER.error("Video skybox stopped drawing", t);
        }
    }

    /** blend the fog toward the background's horizon so the horizon line matches */
    public static void tintFog(Vector4f fog) {
        VideoSkybox m = active;
        if (m == null || fog == null || !m.background.get() || m.fade <= 0) return;
        float t = 0.85F * m.fade;
        int c = VideoSky.HORIZON;
        fog.x += (((c >> 16) & 255) / 255.0F - fog.x) * t;
        fog.y += (((c >> 8) & 255) / 255.0F - fog.y) * t;
        fog.z += ((c & 255) / 255.0F - fog.z) * t;
    }
}
