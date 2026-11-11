package com.exav.addon.modules;

import com.exav.addon.ExavAddon;
import com.exav.addon.video.Tools;
import com.exav.addon.video.WindowCapture;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.MouseInputEvent;
import net.anubis.client.event.impl.Render2DEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.setting.ActionSetting;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.ModeSetting;
import net.anubis.client.module.setting.NumberSetting;
import net.anubis.client.module.setting.StringSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plays a video as an overlay you can move and resize. Uses ffmpeg (installed on your computer) to decode a video
 * file or a direct video link; picture and sound are both streamed from it. Drag it with the mouse while chat is
 * open (bottom-right corner resizes), or use the sliders. YouTube page links aren't supported.
 */
public final class VideoPlayer extends ClientModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("Exav Addon");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("exav-addon", "dynamic/video_player");

    private final ModeSetting mode = addSetting("mode", new ModeSetting("Source", "YouTube / Video", "YouTube / Video", "Window Capture"));
    private final StringSetting windowTitle = addSetting("window", new StringSetting("Window Title Contains", "YouTube"));
    private final NumberSetting cropTop = addSetting("crop-top", new NumberSetting("Crop Top (%)", 0.0F, 0.0F, 45.0F).step(0.5F));
    private final NumberSetting cropBottom = addSetting("crop-bottom", new NumberSetting("Crop Bottom (%)", 0.0F, 0.0F, 45.0F).step(0.5F));
    private final NumberSetting cropLeft = addSetting("crop-left", new NumberSetting("Crop Left (%)", 0.0F, 0.0F, 45.0F).step(0.5F));
    private final NumberSetting cropRight = addSetting("crop-right", new NumberSetting("Crop Right (%)", 0.0F, 0.0F, 45.0F).step(0.5F));
    private final StringSetting source = addSetting("source", new StringSetting("YouTube Link, Video Link or File", ""));
    private final ModeSetting resolution = addSetting("resolution", new ModeSetting("Resolution", "360p", "144p", "240p", "360p", "480p", "720p", "1080p"));
    private final ModeSetting fps = addSetting("fps", new ModeSetting("Frame Rate", "30", "15", "24", "30", "60"));
    private final NumberSetting x = addSetting("x", new NumberSetting("X (%)", 2.0F, 0.0F, 100.0F).step(0.5F));
    private final NumberSetting y = addSetting("y", new NumberSetting("Y (%)", 2.0F, 0.0F, 100.0F).step(0.5F));
    private final NumberSetting width = addSetting("width", new NumberSetting("Width (%)", 30.0F, 5.0F, 100.0F).step(0.5F));
    private final NumberSetting opacity = addSetting("opacity", new NumberSetting("Opacity", 1.0F, 0.1F, 1.0F).step(0.05F));
    private final NumberSetting volume = addSetting("volume", new NumberSetting("Volume", 0.7F, 0.0F, 1.0F).step(0.05F));
    private final BooleanSetting loop = addSetting("loop", new BooleanSetting("Loop", true));
    private final BooleanSetting playOnEnable = addSetting("autoplay", new BooleanSetting("Play When Enabled", true));
    private final StringSetting ffmpegPath = addSetting("ffmpeg", new StringSetting("FFmpeg Path (auto = downloaded by the addon)", "auto"));
    private final StringSetting ytdlpPath = addSetting("yt-dlp", new StringSetting("yt-dlp Path (auto = downloaded by the addon)", "auto"));
    private final ActionSetting updateYt = addSetting("update-yt", new ActionSetting("Update YouTube Support", () -> {
        try {
            Tools.forgetYtDlp();
            status = "YouTube support will update the next time you press Play";
        } catch (IOException e) {
            status = "Couldn't update: " + e.getMessage();
        }
    }));
    private final ActionSetting play = addSetting("play", new ActionSetting("Play / Restart", this::start));
    private final ActionSetting stopAction = addSetting("stop", new ActionSetting("Stop", this::stop));

    private final List<Process> processes = new ArrayList<>();
    private Thread videoThread, audioThread;
    private volatile byte[] frame;
    private volatile int frameNumber;
    private int uploaded = -1;
    private int frameW, frameH;
    private DynamicTexture texture;
    private volatile String status = "";
    private volatile boolean running;
    // dragging
    private int dragMode; // 0 none, 1 move, 2 resize
    private double dragStartX, dragStartY;
    private float startX, startY, startW;

    public VideoPlayer() {
        super("Video Player", "Plays a YouTube video, video link or file as a movable, resizable overlay.", ExavAddon.CATEGORY);
        windowTitle.visibleWhen(this::capturing);
        cropTop.visibleWhen(this::capturing);
        cropBottom.visibleWhen(this::capturing);
        cropLeft.visibleWhen(this::capturing);
        cropRight.visibleWhen(this::capturing);
        source.visibleWhen(() -> !capturing());
        volume.visibleWhen(() -> !capturing());
        loop.visibleWhen(() -> !capturing());
        ffmpegPath.visibleWhen(() -> !capturing());
        ytdlpPath.visibleWhen(() -> !capturing());
        updateYt.visibleWhen(() -> !capturing());
    }

    private boolean capturing() {
        return "Window Capture".equals(mode.get());
    }

    @Override
    protected void onEnable() {
        if (playOnEnable.get()) start();
    }

    @Override
    protected void onDisable() {
        stop();
    }

    // ---------------------------------------------------------------- decoding with ffmpeg

    private int frameHeight() {
        return Integer.parseInt(resolution.get().replace("p", ""));
    }

    private synchronized void start() {
        stop();
        if (capturing()) {
            startCapture();
            return;
        }
        String src = source.get().trim();
        if (src.isEmpty()) {
            status = "Paste a YouTube link (or a video link / file path) into the settings";
            return;
        }
        frameH = frameHeight();
        frameW = Math.round(frameH * 16 / 9.0F / 2) * 2;
        running = true;
        int session = ++playSession;
        status = "Loading...";
        // everything below can take a while (first-time downloads, asking YouTube), so it runs off the game thread
        Thread prepare = new Thread(() -> {
            try {
                String ffmpeg = Tools.ffmpeg(ffmpegPath.get(), this::setStatus);
                List<String> inputs = List.of(src);
                boolean network = src.startsWith("http");
                if (needsYtDlp(src)) {
                    inputs = resolveWithYtDlp(src, frameH, true);
                    if (inputs == null) return;
                }
                synchronized (this) {
                    if (!running || session != playSession) return;
                    launch(ffmpeg, inputs, network);
                }
            } catch (IOException e) {
                if (running && session == playSession) status = "Couldn't get the video player ready: " + e.getMessage();
                LOGGER.warn("Video player setup failed", e);
            }
        }, "Exav video setup");
        prepare.setDaemon(true);
        prepare.start();
    }

    private void setStatus(String text) {
        if (running) status = text;
    }

    private volatile int playSession;

    /** web pages (YouTube and other sites) need yt-dlp to find the actual video; files and direct video links don't */
    private static boolean needsYtDlp(String src) {
        String lower = src.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http")) return false;
        String path = lower.replaceAll("[?#].*$", "");
        return !(path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mkv") || path.endsWith(".mov")
                || path.endsWith(".m3u8") || path.endsWith(".avi") || path.endsWith(".flv") || path.endsWith(".ts"));
    }

    /** asks yt-dlp for the stream address(es): one with picture + sound, or a picture one and a sound one */
    private List<String> resolveWithYtDlp(String url, int height, boolean retry) throws IOException {
        String format = "bv*[height<=" + height + "][vcodec!^=av01]+ba/b[height<=" + height + "]/b";
        String ytDlp = Tools.ytDlp(ytdlpPath.get(), this::setStatus);
        String deno = Tools.deno(ytdlpPath.get(), this::setStatus);
        setStatus("Getting the video from YouTube...");
        try {
            List<String> cmd = new ArrayList<>(List.of(ytDlp, "--no-playlist", "--no-warnings", "-f", format, "-g"));
            if (deno != null) cmd.addAll(List.of("--js-runtimes", "deno:" + deno));
            cmd.add(url);
            Process p = new ProcessBuilder(cmd).start();
            synchronized (this) {
                processes.add(p);
            }
            String out = new String(p.getInputStream().readAllBytes()).trim();
            String err = new String(p.getErrorStream().readAllBytes()).trim();
            p.waitFor();
            List<String> urls = new ArrayList<>();
            for (String line : out.split("\\R")) if (line.startsWith("http")) urls.add(line.trim());
            if (urls.isEmpty() && retry && Tools.isAuto(ytdlpPath.get()) && running) {
                // YouTube changes often: get the newest yt-dlp once and try again
                Tools.forgetYtDlp();
                return resolveWithYtDlp(url, height, false);
            }
            if (urls.isEmpty()) {
                String last = err.isEmpty() ? "no video found" : err.substring(err.lastIndexOf('\n') + 1);
                if (running) status = "yt-dlp: " + (last.length() > 120 ? last.substring(0, 120) + "..." : last);
                LOGGER.warn("yt-dlp couldn't get {}: {}", url, err);
                return null;
            }
            return urls;
        } catch (IOException e) {
            if (running) status = "Couldn't run yt-dlp: " + e.getMessage();
            return null;
        } catch (InterruptedException e) {
            return null;
        }
    }

    /**
     * One ffmpeg plays everything: picture as raw RGBA frames on its normal output, sound as 16-bit PCM on its
     * second output (so both come from the same clock and stay in sync).
     */
    private synchronized void launch(String ffmpeg, List<String> inputs, boolean network) {
        int rate = Integer.parseInt(fps.get());
        status = "Loading...";
        List<String> cmd = new ArrayList<>(List.of(ffmpeg, "-nostdin", "-hide_banner", "-nostats", "-loglevel", "quiet"));
        for (String in : inputs) {
            if (network) cmd.addAll(List.of("-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5"));
            if (loop.get()) cmd.addAll(List.of("-stream_loop", "-1"));
            cmd.addAll(List.of("-re", "-i", in));
        }
        int audioInput = inputs.size() > 1 ? 1 : 0;
        cmd.addAll(List.of("-map", "0:v:0", "-vf",
                "scale=" + frameW + ":" + frameH + ":force_original_aspect_ratio=decrease,pad=" + frameW + ":" + frameH + ":(ow-iw)/2:(oh-ih)/2,fps=" + rate,
                "-f", "rawvideo", "-pix_fmt", "rgba", "pipe:1"));
        cmd.addAll(List.of("-map", audioInput + ":a:0?", "-f", "s16le", "-ac", "2", "-ar", "44100", "pipe:2"));
        try {
            Process p = new ProcessBuilder(cmd).start();
            processes.add(p);
            int w = frameW, h = frameH;
            videoThread = new Thread(() -> readVideo(p.getInputStream(), w, h), "Exav video");
            videoThread.setDaemon(true);
            videoThread.start();
            audioThread = new Thread(() -> playAudio(p.getErrorStream()), "Exav video audio");
            audioThread.setDaemon(true);
            audioThread.start();
        } catch (IOException e) {
            running = false;
            status = "Couldn't start ffmpeg: " + e.getMessage();
            LOGGER.warn("Couldn't start ffmpeg", e);
        }
    }

    private synchronized void stop() {
        running = false;
        playSession++;
        for (Process p : processes) p.destroyForcibly();
        processes.clear();
        frame = null;
        status = "";
    }

    // ---------------------------------------------------------------- window capture (e.g. YouTube in a browser)

    private void startCapture() {
        if (!WindowCapture.supported()) {
            status = "Window Capture only works on Windows";
            return;
        }
        frameH = frameHeight();
        frameW = Math.round(frameH * 16 / 9.0F / 2) * 2;
        int w = frameW, h = frameH;
        long frameMs = Math.max(5, 1000 / Integer.parseInt(fps.get()));
        running = true;
        status = "Looking for a window with \"" + windowTitle.get().trim() + "\" in its title...";
        videoThread = new Thread(() -> {
            WindowCapture capture = new WindowCapture();
            byte[] a = new byte[w * h * 4], b = new byte[w * h * 4];
            boolean useA = true;
            long lastFind = 0;
            try {
                while (running) {
                    long t0 = System.currentTimeMillis();
                    boolean ok = false;
                    try {
                        byte[] target = useA ? a : b;
                        ok = capture.grab(target, w, h, cropLeft.get() / 100, cropTop.get() / 100, cropRight.get() / 100, cropBottom.get() / 100);
                        if (ok) {
                            frame = target;
                            frameNumber++;
                            useA = !useA;
                            status = "";
                        }
                    } catch (Throwable e) {
                        LOGGER.debug("Window capture failed", e);
                    }
                    if (!ok) {
                        frame = null;
                        if (t0 - lastFind > 1500) { // window closed / not found yet -> look again
                            lastFind = t0;
                            capture.release();
                            String want = windowTitle.get().trim();
                            status = capture.find(want) ? "Window is minimized - restore it (it can stay behind Minecraft)"
                                    : "No window with \"" + want + "\" in its title - open the video in your browser";
                        }
                    }
                    long sleep = frameMs - (System.currentTimeMillis() - t0);
                    if (sleep > 0) Thread.sleep(sleep);
                }
            } catch (InterruptedException ignored) {
            } finally {
                capture.release();
            }
        }, "Exav window capture");
        videoThread.setDaemon(true);
        videoThread.start();
    }

    private void readVideo(InputStream in, int w, int h) {
        byte[] a = new byte[w * h * 4], b = new byte[w * h * 4];
        boolean useA = true;
        try (DataInputStream data = new DataInputStream(in)) {
            while (running) {
                byte[] target = useA ? a : b;
                data.readFully(target);
                frame = target;
                frameNumber++;
                useA = !useA;
                status = "";
            }
        } catch (IOException e) {
            if (running && frame == null) status = "Couldn't play that video (check the link / path)";
        }
    }

    private void playAudio(InputStream in) {
        AudioFormat format = new AudioFormat(44100, 16, 2, true, false);
        try (InputStream audio = in) {
            SourceDataLine line = AudioSystem.getSourceDataLine(format);
            line.open(format, 44100);
            line.start();
            byte[] buf = new byte[4096];
            int n;
            while (running && (n = audio.read(buf)) > 0) {
                float gain = volume.get() * Minecraft.getInstance().options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER);
                for (int i = 0; i + 1 < n; i += 2) { // scale the samples by the volume
                    int s = (short) ((buf[i] & 255) | (buf[i + 1] << 8));
                    s = Math.round(s * gain);
                    buf[i] = (byte) s;
                    buf[i + 1] = (byte) (s >> 8);
                }
                line.write(buf, 0, n - (n % 4));
            }
            line.drain();
            line.close();
        } catch (Exception e) {
            LOGGER.debug("Video audio stopped", e);
        }
    }

    // ---------------------------------------------------------------- drawing

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.graphics;
        int sw = g.guiWidth(), sh = g.guiHeight();
        int boxW = Math.round(sw * width.get() / 100.0F);
        int boxH = Math.round(boxW * 9 / 16.0F);
        int bx = Math.round((sw - boxW) * x.get() / 100.0F), by = Math.round((sh - boxH) * y.get() / 100.0F);
        handleDrag(mc, sw, sh, bx, by, boxW);
        byte[] f = frame;
        if (f == null) {
            if (!status.isEmpty()) {
                g.fill(bx, by, bx + Math.max(boxW, mc.font.width(status) + 8), by + 14, 0xC0101018);
                g.drawString(mc.font, status, bx + 4, by + 3, 0xFFE0E0E0);
            }
            return;
        }
        if (frameNumber != uploaded) {
            upload(f);
            uploaded = frameNumber;
        }
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(bx, by);
        pose.scale(boxW / (float) frameW, boxH / (float) frameH);
        int color = (Math.round(opacity.get() * 255) << 24) | 0xFFFFFF;
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0, 0, frameW, frameH, frameW, frameH, color);
        pose.popMatrix();
        if (mc.screen instanceof ChatScreen) { // show the drag handles while chat is open
            g.fill(bx, by, bx + boxW, by + 1, 0xFF8A5CFF);
            g.fill(bx, by + boxH - 1, bx + boxW, by + boxH, 0xFF8A5CFF);
            g.fill(bx, by, bx + 1, by + boxH, 0xFF8A5CFF);
            g.fill(bx + boxW - 1, by, bx + boxW, by + boxH, 0xFF8A5CFF);
            g.fill(bx + boxW - 6, by + boxH - 6, bx + boxW, by + boxH, 0xFFFFFFFF);
        }
    }

    private void upload(byte[] rgba) {
        if (texture == null || texture.getPixels() == null || texture.getPixels().getWidth() != frameW || texture.getPixels().getHeight() != frameH) {
            NativeImage image = new NativeImage(frameW, frameH, false);
            if (texture == null) {
                texture = new DynamicTexture(() -> "Exav video player", image);
                Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
            } else {
                texture.setPixels(image);
            }
        }
        NativeImage pixels = texture.getPixels();
        // ffmpeg gives RGBA bytes, which is exactly how NativeImage keeps its pixels in memory
        MemoryUtil.memByteBuffer(pixels.getPointer(), rgba.length).put(rgba);
        texture.upload();
    }

    // ---------------------------------------------------------------- dragging (while chat is open)

    @EventHandler
    public void onMouse(MouseInputEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ChatScreen) || event.button != 0) return;
        if (event.action == 0) {
            dragMode = 0;
            return;
        }
        var window = mc.getWindow();
        double scale = window.getGuiScale();
        double mx = mc.mouseHandler.xpos() / scale, my = mc.mouseHandler.ypos() / scale;
        int sw = window.getGuiScaledWidth(), sh = window.getGuiScaledHeight();
        int boxW = Math.round(sw * width.get() / 100.0F), boxH = Math.round(boxW * 9 / 16.0F);
        int bx = Math.round((sw - boxW) * x.get() / 100.0F), by = Math.round((sh - boxH) * y.get() / 100.0F);
        if (mx >= bx + boxW - 8 && mx <= bx + boxW && my >= by + boxH - 8 && my <= by + boxH) dragMode = 2;
        else if (mx >= bx && mx <= bx + boxW && my >= by && my <= by + boxH) dragMode = 1;
        else return;
        dragStartX = mx;
        dragStartY = my;
        startX = x.get();
        startY = y.get();
        startW = width.get();
        event.setCancelled(true);
    }

    private void handleDrag(Minecraft mc, int sw, int sh, int bx, int by, int boxW) {
        if (dragMode == 0) return;
        if (!(mc.screen instanceof ChatScreen)) {
            dragMode = 0;
            return;
        }
        double scale = mc.getWindow().getGuiScale();
        double mx = mc.mouseHandler.xpos() / scale, my = mc.mouseHandler.ypos() / scale;
        if (dragMode == 2) {
            float w = startW + (float) ((mx - dragStartX) / sw * 100);
            width.set(Math.max(5, Math.min(100, w)));
        } else {
            int boxH = Math.round(boxW * 9 / 16.0F);
            float nx = startX + (float) ((mx - dragStartX) / Math.max(1, sw - boxW) * 100);
            float ny = startY + (float) ((my - dragStartY) / Math.max(1, sh - boxH) * 100);
            x.set(Math.max(0, Math.min(100, nx)));
            y.set(Math.max(0, Math.min(100, ny)));
        }
    }
}
