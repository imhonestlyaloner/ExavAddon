package com.exav.addon.modules;

import com.exav.addon.ExavAddon;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.anubis.client.event.EventHandler;
import net.anubis.client.event.impl.TickEvent;
import net.anubis.client.module.ClientModule;
import net.anubis.client.module.setting.ActionSetting;
import net.anubis.client.module.setting.BooleanSetting;
import net.anubis.client.module.setting.StringSetting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wear any picture as a cape (client side: only you see it). Put the picture in .minecraft/config/exav-addon/capes/
 * and type its file name. It's laid out on the cape (both sides) in HD, cropped to the cape's shape, and reloaded
 * when the file changes.
 */
public final class CustomCape extends ClientModule {
    private static final Logger LOGGER = LoggerFactory.getLogger("Exav Addon");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("exav-addon", "dynamic/custom_cape");
    private static volatile CustomCape active;

    private final StringSetting file = addSetting("file", new StringSetting("Cape Image", "cape.png"));
    private final BooleanSetting elytra = addSetting("elytra", new BooleanSetting("Use On Elytra", true));
    private final ActionSetting openFolder = addSetting("open-folder", new ActionSetting("Open Capes Folder", CustomCape::openFolder));

    private ClientAsset.ResourceTexture asset;
    private Path loadedPath;
    private long loadedStamp;
    private int ticks;

    public CustomCape() {
        super("Custom Cape", "Wear any picture as your cape (only you see it). Put it in config/exav-addon/capes/.", ExavAddon.CATEGORY);
    }

    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("exav-addon").resolve("capes");
    }

    private static void openFolder() {
        try {
            Files.createDirectories(folder());
            net.minecraft.util.Util.getPlatform().openPath(folder());
        } catch (Exception e) {
            LOGGER.warn("Couldn't open the capes folder", e);
        }
    }

    @Override
    protected void onEnable() {
        active = this;
        loadedPath = null;
        reload();
    }

    @Override
    protected void onDisable() {
        active = null;
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (++ticks % 40 == 0) reload(); // pick up a new or edited picture
    }

    private void reload() {
        Path path = folder().resolve(file.get().trim());
        try {
            Files.createDirectories(folder());
            if (!Files.isRegularFile(path)) {
                asset = null;
                return;
            }
            long stamp = Files.getLastModifiedTime(path).toMillis();
            if (path.equals(loadedPath) && stamp == loadedStamp && asset != null) return;
            NativeImage picture;
            try (InputStream in = Files.newInputStream(path)) {
                picture = NativeImage.read(in);
            }
            NativeImage cape = layout(picture);
            picture.close();
            Minecraft.getInstance().getTextureManager().register(TEXTURE, new DynamicTexture(() -> "Exav custom cape", cape));
            asset = new ClientAsset.ResourceTexture(TEXTURE, TEXTURE);
            loadedPath = path;
            loadedStamp = stamp;
        } catch (Exception e) {
            asset = null;
            LOGGER.warn("Couldn't load cape image {} (PNG works best)", path, e);
        }
    }

    /**
     * The cape texture is a 64x32 layout (here 16x bigger for detail): front panel at (1,1) 10x16, back panel at
     * (12,1), thin edges around them. The picture goes on both panels (cropped to 10:16), the edges get its
     * average colour.
     */
    private static NativeImage layout(NativeImage picture) {
        int k = 16;
        NativeImage cape = new NativeImage(64 * k, 32 * k, true);
        int pw = picture.getWidth(), ph = picture.getHeight();
        // crop the middle of the picture to the cape's 10:16 shape
        float want = 10.0F / 16.0F;
        int cw = pw, ch = ph, cx = 0, cy = 0;
        if ((float) pw / ph > want) {
            cw = Math.round(ph * want);
            cx = (pw - cw) / 2;
        } else {
            ch = Math.round(pw / want);
            cy = (ph - ch) / 2;
        }
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = 0; y < ph; y += 4) {
            for (int x = 0; x < pw; x += 4) {
                int c = picture.getPixel(x, y);
                r += c & 255;
                g += (c >> 8) & 255;
                b += (c >> 16) & 255;
                n++;
            }
        }
        int avg = 0xFF000000 | (int) (b / Math.max(1, n)) << 16 | (int) (g / Math.max(1, n)) << 8 | (int) (r / Math.max(1, n));
        // edges and the whole cape area in the average colour first
        for (int y = 0; y < 17 * k; y++) {
            for (int x = 0; x < 22 * k; x++) cape.setPixel(x, y, avg);
        }
        for (int panel = 0; panel < 2; panel++) {
            int ox = (panel == 0 ? 1 : 12) * k, oy = k, w = 10 * k, h = 16 * k;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    // the back panel is seen from the other side, so mirror it
                    int sx = cx + (panel == 0 ? x : w - 1 - x) * cw / w, sy = cy + y * ch / h;
                    int c = picture.getPixel(Math.min(pw - 1, sx), Math.min(ph - 1, sy));
                    if (((c >>> 24) & 255) < 8) c = avg; // fill transparent parts
                    cape.setPixel(ox + x, oy + y, c | 0xFF000000);
                }
            }
        }
        // elytra uses the cape texture too (its wing area sits at 22..46 on the 64x32 layout)
        for (int y = 0; y < 22 * k; y++) {
            for (int x = 22 * k; x < 46 * k; x++) {
                int sx = cx + (x - 22 * k) * cw / (24 * k), sy = cy + y * ch / (22 * k);
                int c = picture.getPixel(Math.min(pw - 1, sx), Math.min(ph - 1, sy));
                cape.setPixel(x, y, ((c >>> 24) & 255) < 8 ? avg : c | 0xFF000000);
            }
        }
        return cape;
    }

    /** from the player skin mixin: swap in the cape for your own player */
    public static PlayerSkin apply(PlayerSkin skin) {
        CustomCape m = active;
        if (m == null || m.asset == null || skin == null) return skin;
        return new PlayerSkin(skin.body(), m.asset, m.elytra.get() ? m.asset : skin.elytra(), skin.model(), skin.secure());
    }
}
