package com.exav.addon.audio;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plays an .ogg file from disk through the game's OpenAL device (so files from the config folder can be played
 * without a resource pack). Decoded once, then each play() starts a new voice.
 */
public final class OggClip {
    private static final Logger LOGGER = LoggerFactory.getLogger("Exav Addon");

    private final Path path;
    private long stamp = -1;
    private int buffer;
    private final List<Integer> voices = new ArrayList<>();

    public OggClip(Path path) {
        this.path = path;
    }

    public boolean exists() {
        return Files.isRegularFile(path);
    }

    private boolean load() {
        try {
            long now = Files.getLastModifiedTime(path).toMillis();
            if (buffer != 0 && now == stamp) return true;
            byte[] bytes = Files.readAllBytes(path);
            ByteBuffer data = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer channels = stack.mallocInt(1), rate = stack.mallocInt(1);
                ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(data, channels, rate);
                if (pcm == null) {
                    LOGGER.warn("Couldn't decode {} (is it an .ogg file?)", path);
                    return false;
                }
                if (buffer != 0) AL10.alDeleteBuffers(buffer);
                buffer = AL10.alGenBuffers();
                AL10.alBufferData(buffer, channels.get(0) == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16, pcm, rate.get(0));
                MemoryUtil.memFree(pcm);
            } finally {
                MemoryUtil.memFree(data);
            }
            stamp = now;
            return true;
        } catch (Throwable t) {
            LOGGER.warn("Couldn't load {}", path, t);
            buffer = 0;
            return false;
        }
    }

    /** plays the clip at the listener (not positional); volume 0..1 */
    public void play(float volume) {
        try {
            cleanup();
            if (!exists() || !load()) return;
            int source = AL10.alGenSources();
            AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, 0);
            AL10.alSourcef(source, AL10.AL_GAIN, volume);
            AL10.alSourcePlay(source);
            voices.add(source);
        } catch (Throwable t) {
            LOGGER.warn("Couldn't play {}", path, t);
        }
    }

    /** frees voices that finished playing */
    public void cleanup() {
        voices.removeIf(source -> {
            if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) return false;
            AL10.alDeleteSources(source);
            return true;
        });
    }
}
