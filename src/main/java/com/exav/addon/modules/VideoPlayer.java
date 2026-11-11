package com.exav.addon.modules;

import com.exav.addon.ExavAddon;
import com.exav.addon.video.StreamPlayer;
import com.exav.addon.video.YouTube;
import com.exav.addon.video.WindowCapture;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.MouseInputEvent;
import net.anubis.client.event.impl.Render2DEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.setting.ActionSetting;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.KeySetting;
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
 * Plays a YouTube video, a video link or a video file as an overlay you can move and resize - all inside the game
 * (YouTube is read with the bundled NewPipeExtractor, video decoded with the bundled FFmpeg library; nothing to
 * install or download). Drag it with the mouse while chat is open (bottom-right corner resizes), or use the sliders.
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
    private final ModeSetting onEnd = addSetting("on-end", new ModeSetting("When a Video Ends", "Next Video", "Next Video", "Loop", "Stop"));
    private final KeySetting nextKey = addSetting("next-key", new KeySetting("Next Video Key", 93));     // ]
    private final KeySetting previousKey = addSetting("previous-key", new KeySetting("Previous Video Key", 91)); // [
    private final ActionSetting nextAction = addSetting("next", new ActionSetting("Next Video", () -> skip(1)));
    private final ActionSetting previousAction = addSetting("previous", new ActionSetting("Previous Video", () -> skip(-1)));
    private final BooleanSetting showTitle = addSetting("show-title", new BooleanSetting("Show Video Title", true));
    private final BooleanSetting playOnEnable = addSetting("autoplay", new BooleanSetting("Play When Enabled", true));
    private final ActionSetting play = addSetting("play", new ActionSetting("Play / Restart", this::start));
    private final ActionSetting stopAction = addSetting("stop", new ActionSetting("Stop", this::stop));

    private StreamPlayer player;
    private Thread videoThread;
    /** the newest picture (with its own size), swapped in by the playback thread */
    private volatile StreamPlayer.Picture frame;
    private StreamPlayer.Picture uploaded;
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
        onEnd.visibleWhen(() -> !capturing());
        nextKey.visibleWhen(() -> !capturing());
        previousKey.visibleWhen(() -> !capturing());
        nextAction.visibleWhen(() -> !capturing());
        previousAction.visibleWhen(() -> !capturing());
        showTitle.visibleWhen(() -> !capturing());
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

    private volatile int playSession;
    /** the last YouTube link and its streams, so Loop / Restart doesn't ask YouTube again */
    private String resolvedLink;
    private YouTube.Streams resolved;
    private long resolvedAt;

    // ---------------------------------------------------------------- what's playing, and what's next

    /** the link playing now (starts as the one in the settings, changes as you skip) */
    private String current;
    /** links played before this one, for Previous */
    private final java.util.ArrayDeque<String> history = new java.util.ArrayDeque<>();
    /** the playlist's videos when the link is a playlist (else empty), and where we are in it */
    private List<String> playlist = List.of();
    private int playlistIndex = -1;
    private volatile String upNext;
    private volatile String title = "";
    private volatile long titleShownAt;

    /** Play / Restart / turning the module on: start over from the link in the settings */
    private synchronized void start() {
        history.clear();
        playlist = List.of();
        playlistIndex = -1;
        play(source.get().trim());
    }

    /** next (+1) or previous (-1) video */
    private synchronized void skip(int direction) {
        if (capturing() || current == null) return;
        if (direction < 0) {
            if (playlistIndex > 0) {
                playlistIndex--;
                play(playlist.get(playlistIndex));
            } else if (!history.isEmpty()) {
                play(history.pop());
            } else {
                play(current); // nothing before it: start this one again
            }
            return;
        }
        if (!YouTube.isYouTube(current)) {
            play(current); // a file / direct link has no "next": play it again
            return;
        }
        history.push(current);
        if (history.size() > 50) history.removeLast();
        if (!playlist.isEmpty() && playlistIndex + 1 < playlist.size()) {
            playlistIndex++;
            play(playlist.get(playlistIndex));
        } else if (upNext != null) {
            playlist = List.of();
            playlistIndex = -1;
            play(upNext);
        } else {
            status = "No next video found";
        }
    }

    @EventHandler
    public void onKey(net.anubis.client.event.impl.KeyInputEvent event) {
        if (event.action != 1 || capturing() || Minecraft.getInstance().screen != null) return;
        if (event.key == nextKey.value()) skip(1);
        else if (event.key == previousKey.value()) skip(-1);
    }

    private synchronized void play(String src) {
        stop();
        if (capturing()) {
            startCapture();
            return;
        }
        if (src == null || src.isEmpty()) {
            status = "Paste a YouTube link (or a video link / file path) into the settings";
            return;
        }
        current = src;
        upNext = null;
        title = "";
        running = true;
        int session = ++playSession;
        int height = frameHeight();
        status = "Loading...";
        // finding the streams and opening them can take a moment, so it runs off the game thread
        Thread prepare = new Thread(() -> {
            try {
                // the FFmpeg library inside the addon jar is unpacked here the first time (nothing is downloaded)
                if (System.getProperty("org.bytedeco.javacpp.cachedir") == null) {
                    System.setProperty("org.bytedeco.javacpp.cachedir",
                            net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("exav-addon").resolve("video-library").toString());
                }
                StreamPlayer.preload(this::setStatus);
                String video = src, audio = null;
                if (YouTube.isYouTube(src)) {
                    String link = src;
                    if (YouTube.isPlaylist(src) && playlistIndex < 0) { // first time on a playlist link: get its videos
                        setStatus("Getting the playlist...");
                        List<String> videos = YouTube.playlist(src);
                        if (!videos.isEmpty()) {
                            String id = YouTube.videoId(src);
                            int at = 0;
                            for (int i = 0; i < videos.size(); i++) if (!id.isEmpty() && videos.get(i).contains(id)) at = i;
                            synchronized (this) {
                                if (session != playSession) return;
                                playlist = videos;
                                playlistIndex = at;
                            }
                            link = videos.get(at);
                        }
                    }
                    final String video0 = link;
                    YouTube.Streams streams;
                    synchronized (this) {
                        boolean fresh = video0.equals(resolvedLink) && resolved != null && System.currentTimeMillis() - resolvedAt < 3 * 3600_000L;
                        streams = fresh ? resolved : null;
                    }
                    if (streams == null) {
                        setStatus("Getting the video from YouTube...");
                        streams = YouTube.find(video0, height);
                        synchronized (this) {
                            resolvedLink = video0;
                            resolved = streams;
                            resolvedAt = System.currentTimeMillis();
                        }
                    }
                    video = streams.video();
                    audio = streams.audio();
                    upNext = streams.upNext();
                    title = streams.title() == null ? "" : streams.title();
                    titleShownAt = System.currentTimeMillis();
                }
                synchronized (this) {
                    if (!running || session != playSession) return;
                    setStatus("Loading...");
                    player = new StreamPlayer(video, audio, height, Integer.parseInt(fps.get()),
                            () -> volume.get() * Minecraft.getInstance().options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER),
                            new StreamPlayer.Listener() {
                                @Override
                                public void picture(StreamPlayer.Picture picture) {
                                    if (session == playSession) frame = picture;
                                }

                                @Override
                                public void status(String text) {
                                    if (session == playSession) setStatus(text);
                                }

                                @Override
                                public void finished() {
                                    if (session != playSession) return;
                                    switch (onEnd.get()) {
                                        case "Loop" -> Minecraft.getInstance().execute(() -> play(current));
                                        case "Next Video" -> Minecraft.getInstance().execute(() -> skip(1));
                                        default -> { }
                                    }
                                }
                            });
                    player.start();
                }
            } catch (Throwable e) {
                if (running && session == playSession) {
                    String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    status = "Couldn't play that video: " + (why.length() > 140 ? why.substring(0, 140) + "..." : why);
                }
                LOGGER.warn("Video player couldn't start", e);
            }
        }, "Exav video setup");
        prepare.setDaemon(true);
        prepare.start();
    }

    private void setStatus(String text) {
        if (running) status = text;
    }

    private synchronized void stop() {
        running = false;
        playSession++;
        if (player != null) player.stop();
        player = null;
        frame = null;
        status = "";
    }

    // ---------------------------------------------------------------- window capture (e.g. YouTube in a browser)

    private void startCapture() {
        if (!WindowCapture.supported()) {
            status = "Window Capture only works on Windows";
            return;
        }
        int h = frameHeight(), w = Math.round(h * 16 / 9.0F / 2) * 2;
        int session = ++playSession;
        long frameMs = Math.max(5, 1000 / Integer.parseInt(fps.get()));
        running = true;
        status = "Looking for a window with \"" + windowTitle.get().trim() + "\" in its title...";
        videoThread = new Thread(() -> {
            WindowCapture capture = new WindowCapture();
            byte[] a = new byte[w * h * 4], b = new byte[w * h * 4];
            boolean useA = true;
            long lastFind = 0;
            try {
                while (running && session == playSession) {
                    long t0 = System.currentTimeMillis();
                    boolean ok = false;
                    try {
                        byte[] target = useA ? a : b;
                        ok = capture.grab(target, w, h, cropLeft.get() / 100, cropTop.get() / 100, cropRight.get() / 100, cropBottom.get() / 100);
                        if (ok) {
                            frame = new StreamPlayer.Picture(target, w, h);
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
        StreamPlayer.Picture f = frame;
        if (f == null) {
            if (!status.isEmpty()) {
                g.fill(bx, by, bx + Math.max(boxW, mc.font.width(status) + 8), by + 14, 0xC0101018);
                g.drawString(mc.font, status, bx + 4, by + 3, 0xFFE0E0E0);
            }
            return;
        }
        if (f != uploaded) {
            upload(f);
            uploaded = f;
        }
        int alpha = Math.round(opacity.get() * 255);
        g.fill(bx, by, bx + boxW, by + boxH, alpha << 24); // black bars around videos that aren't 16:9
        // fit the picture inside the box, keeping its shape
        float fit = Math.min(boxW / (float) f.width(), boxH / (float) f.height());
        float dw = f.width() * fit, dh = f.height() * fit;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(bx + (boxW - dw) / 2, by + (boxH - dh) / 2);
        pose.scale(fit, fit);
        int color = alpha << 24 | 0xFFFFFF;
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0, 0, f.width(), f.height(), f.width(), f.height(), color);
        pose.popMatrix();
        // the video's title for a few seconds when it starts
        long since = System.currentTimeMillis() - titleShownAt;
        if (showTitle.get() && !title.isEmpty() && since < 4500 && !capturing()) {
            float fade = Math.min(1, (4500 - since) / 600.0F) * opacity.get();
            String text = mc.font.plainSubstrByWidth("\u25B6 " + title, Math.max(20, boxW - 8));
            g.fill(bx, by, bx + boxW, by + 13, (int) (170 * fade) << 24);
            g.drawString(mc.font, text, bx + 4, by + 3, (int) (255 * fade) << 24 | 0xFFFFFF);
        }
        if (mc.screen instanceof ChatScreen) { // show the drag handles while chat is open
            g.fill(bx, by, bx + boxW, by + 1, 0xFF8A5CFF);
            g.fill(bx, by + boxH - 1, bx + boxW, by + boxH, 0xFF8A5CFF);
            g.fill(bx, by, bx + 1, by + boxH, 0xFF8A5CFF);
            g.fill(bx + boxW - 1, by, bx + boxW, by + boxH, 0xFF8A5CFF);
            g.fill(bx + boxW - 6, by + boxH - 6, bx + boxW, by + boxH, 0xFFFFFFFF);
        }
    }

    private void upload(StreamPlayer.Picture picture) {
        byte[] rgba = picture.rgba();
        int frameW = picture.width(), frameH = picture.height();
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
        // FFmpeg gives RGBA bytes, which is exactly how NativeImage keeps its pixels in memory
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
