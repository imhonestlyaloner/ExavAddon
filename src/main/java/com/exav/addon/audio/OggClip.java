package com.exav.addon.audio;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.util.valueproviders.ConstantFloat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plays an .ogg file from disk through Minecraft's own sound system (so it follows the game's volume sliders and
 * output device). The sound loader is pointed at the file by {@code SoundBufferLibraryMixin}.
 */
public final class OggClip {
    private static final Logger LOGGER = LoggerFactory.getLogger("Exav Addon");
    /** sound file id (as the sound loader asks for it) -> file on disk */
    private static final Map<Identifier, OggClip> FILES = new ConcurrentHashMap<>();
    private static int counter;

    private final Path path;
    private CompletableFuture<SoundBuffer> decoded;
    private long stamp = Long.MIN_VALUE;
    private Sound sound;

    public OggClip(Path path) {
        this.path = path;
    }

    public boolean exists() {
        return Files.isRegularFile(path);
    }

    /** decodes the file ahead of time (again if it changed), so play() starts instantly and stays in sync */
    public void preload() {
        try {
            long now = Files.getLastModifiedTime(path).toMillis();
            if (decoded != null && now == stamp) return;
            stamp = now;
            Path file = path;
            decoded = CompletableFuture.supplyAsync(() -> decode(file), Util.nonCriticalIoPool());
            // a new id per version of the file, so the game never mixes up an old and a new one
            Identifier id = Identifier.fromNamespaceAndPath("exav-addon", "disk/clip_" + (counter++));
            sound = new Sound(id, ConstantFloat.of(1), ConstantFloat.of(1), 1, Sound.Type.FILE, false, false, 16);
            FILES.put(sound.getPath(), this);
        } catch (Exception e) {
            decoded = null;
        }
    }

    /** plays the clip at the listener (not positional); volume 0..1, on top of the game's Master volume */
    public void play(float volume) {
        try {
            preload();
            if (sound == null) return;
            Minecraft.getInstance().getSoundManager().play(new Instance(sound, volume));
        } catch (Throwable t) {
            LOGGER.warn("Couldn't play {}", path, t);
        }
    }

    public void cleanup() {
    }

    /** called by the sound loader: our files load from disk, everything else is left alone (null) */
    public static CompletableFuture<SoundBuffer> load(Identifier soundPath) {
        OggClip clip = FILES.get(soundPath);
        return clip == null ? null : clip.decoded;
    }

    private static SoundBuffer decode(Path file) {
        try (InputStream in = Files.newInputStream(file); JOrbisAudioStream ogg = new JOrbisAudioStream(in)) {
            ByteBuffer data = ogg.readAll();
            return new SoundBuffer(data, ogg.getFormat());
        } catch (Exception e) {
            LOGGER.warn("Couldn't load {} (is it an .ogg file?)", file, e);
            throw new RuntimeException(e);
        }
    }

    private static final class Instance extends AbstractSoundInstance {
        Instance(Sound sound, float volume) {
            super(sound.getLocation(), SoundSource.MASTER, SoundInstance.createUnseededRandom());
            this.sound = sound;
            this.volume = volume;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            return new WeighedSoundEvents(sound.getLocation(), null);
        }
    }
}
