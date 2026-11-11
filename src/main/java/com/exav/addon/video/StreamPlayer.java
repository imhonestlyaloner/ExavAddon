package com.exav.addon.video;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.function.Consumer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;

/**
 * Plays a video inside the game with the FFmpeg library bundled in the addon (no outside programs): one source with
 * picture + sound, or a picture-only source and a sound-only source (how YouTube serves higher qualities). Sound goes
 * to the speakers and keeps the time; pictures are handed over as RGBA frames, each shown when the sound reaches it.
 */
public final class StreamPlayer {
    /** a decoded picture: RGBA rows, tightly packed */
    public record Picture(byte[] rgba, int width, int height) {
    }

    public interface Listener {
        void picture(Picture picture);

        void status(String text);

        /** the video reached its end (not called when stopped) */
        void finished();
    }

    private static final int RATE = 44100;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private final String video, audio;
    private final int maxHeight, fps;
    private final java.util.function.DoubleSupplier volume;
    private final Listener listener;
    private volatile boolean running = true;
    private volatile SourceDataLine line;
    private volatile long audioStartUs = Long.MIN_VALUE;
    private volatile long wallStartNs;
    private Thread videoThread, audioThread;

    /** audio may be null (sound comes from the video source) */
    public StreamPlayer(String video, String audio, int maxHeight, int fps, java.util.function.DoubleSupplier volume, Listener listener) {
        this.video = video;
        this.audio = audio;
        this.maxHeight = maxHeight;
        this.fps = fps;
        this.volume = volume;
        this.listener = listener;
    }

    public void start() {
        wallStartNs = System.nanoTime();
        videoThread = new Thread(this::runVideo, "Exav video");
        videoThread.setDaemon(true);
        videoThread.start();
        audioThread = new Thread(this::runAudio, "Exav video sound");
        audioThread.setDaemon(true);
        audioThread.start();
    }

    public void stop() {
        running = false;
        SourceDataLine l = line;
        if (l != null) {
            l.stop();
            l.flush();
        }
    }

    private static FFmpegFrameGrabber open(String source) {
        FFmpegFrameGrabber g = new FFmpegFrameGrabber(source);
        if (source.startsWith("http")) {
            g.setOption("user_agent", USER_AGENT);
            g.setOption("reconnect", "1");
            g.setOption("reconnect_streamed", "1");
            g.setOption("reconnect_delay_max", "5");
        }
        return g;
    }

    /** where playback is now (microseconds of the video): the sound's position when there is sound, else the clock */
    private long clockUs() {
        SourceDataLine l = line;
        if (l != null && audioStartUs != Long.MIN_VALUE) return audioStartUs + l.getLongFramePosition() * 1_000_000L / RATE;
        return (System.nanoTime() - wallStartNs) / 1000;
    }

    private void runVideo() {
        try (FFmpegFrameGrabber g = open(video)) {
            g.setPixelFormat(avutil.AV_PIX_FMT_RGBA);
            g.start();
            // keep the shape: fit inside the chosen height (never bigger than the source)
            int srcW = Math.max(1, g.getImageWidth()), srcH = Math.max(1, g.getImageHeight());
            int h = Math.min(maxHeight, srcH), w = Math.max(2, Math.round(srcW * (h / (float) srcH)) / 2 * 2);
            g.setImageWidth(w);
            g.setImageHeight(h);
            listener.status("");
            long minGapUs = 1_000_000L / Math.max(1, fps), lastShown = -1;
            Frame f;
            while (running && (f = g.grabImage()) != null) {
                long ts = f.timestamp;
                if (lastShown >= 0 && ts > lastShown && ts - lastShown < minGapUs - 2000) continue; // keep to the chosen frame rate
                // wait for the sound (or the clock) to reach this picture; if we're behind, show it straight away
                long wait;
                while (running && (wait = ts - clockUs()) > 2000) Thread.sleep(Math.min(20, wait / 1000));
                if (!running) break;
                if (clockUs() - ts > 400_000) continue; // far behind: drop it to catch up
                lastShown = ts;
                listener.picture(copy(f, g.getImageWidth(), g.getImageHeight()));
            }
            if (running) listener.finished();
        } catch (InterruptedException ignored) {
        } catch (Throwable e) {
            if (running) listener.status("Couldn't play the video: " + e.getMessage());
        }
    }

    private static Picture copy(Frame f, int w, int h) {
        ByteBuffer src = (ByteBuffer) f.image[0];
        int stride = f.imageStride > 0 ? f.imageStride : w * 4;
        byte[] out = new byte[w * h * 4];
        for (int y = 0; y < h; y++) {
            src.position(y * stride);
            src.get(out, y * w * 4, w * 4);
        }
        return new Picture(out, w, h);
    }

    private void runAudio() {
        try (FFmpegFrameGrabber g = open(audio != null ? audio : video)) {
            g.setSampleFormat(avutil.AV_SAMPLE_FMT_S16);
            g.setSampleRate(RATE);
            g.setAudioChannels(2);
            g.start();
            if (g.getAudioChannels() <= 0) return; // no sound in this video
            AudioFormat format = new AudioFormat(RATE, 16, 2, true, false);
            SourceDataLine l = AudioSystem.getSourceDataLine(format);
            l.open(format, RATE); // ~0.25 s buffer
            byte[] buf = new byte[0];
            Frame f;
            while (running && (f = g.grabSamples()) != null) {
                if (f.samples == null || f.samples.length == 0) continue;
                ShortBuffer s = (ShortBuffer) f.samples[0];
                s.rewind();
                int n = s.remaining();
                if (buf.length < n * 2) buf = new byte[n * 2];
                float gain = (float) Math.max(0, Math.min(1, volume.getAsDouble()));
                for (int i = 0; i < n; i++) {
                    int v = Math.round(s.get(i) * gain);
                    buf[i * 2] = (byte) v;
                    buf[i * 2 + 1] = (byte) (v >> 8);
                }
                if (line == null) {
                    audioStartUs = f.timestamp;
                    l.start();
                    line = l;
                }
                l.write(buf, 0, n * 2); // blocks: the sound plays in real time and sets the pace
            }
            if (running) l.drain();
            l.close();
        } catch (Throwable e) {
            // no sound / no output device: the picture keeps going on the clock
            line = null;
        }
    }

    /** loads the FFmpeg library ahead of time (it's unpacked from the addon jar on first use) */
    public static void preload(Consumer<String> status) {
        status.accept("Starting the video player...");
        org.bytedeco.javacpp.Loader.load(org.bytedeco.ffmpeg.global.avformat.class);
    }
}
