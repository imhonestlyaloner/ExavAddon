package com.exav.addon.video;

import com.sun.jna.Memory;
import com.sun.jna.Platform;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinGDI;
import com.sun.jna.platform.win32.WinNT;
import java.util.Locale;

/**
 * Copies the picture of another window (e.g. a browser playing YouTube) using Windows' PrintWindow, which works even
 * while the window is behind Minecraft (just not while it's minimized). Windows only.
 */
public final class WindowCapture {
    private static final int PW_CLIENTONLY = 1, PW_RENDERFULLCONTENT = 2;

    private WinDef.HWND window;
    private String windowTitle = "";
    private WinDef.HDC memDc;
    private WinDef.HBITMAP bitmap;
    private Memory pixels;
    private int bw, bh;

    public static boolean supported() {
        return Platform.isWindows();
    }

    public String title() {
        return windowTitle;
    }

    /** finds the first visible window whose title contains the text (not Minecraft itself) */
    public boolean find(String contains) {
        String want = contains.toLowerCase(Locale.ROOT).trim();
        window = null;
        windowTitle = "";
        if (want.isEmpty()) return false;
        char[] buf = new char[512];
        User32.INSTANCE.EnumWindows((hwnd, data) -> {
            if (!User32.INSTANCE.IsWindowVisible(hwnd)) return true;
            int n = User32.INSTANCE.GetWindowText(hwnd, buf, buf.length);
            if (n <= 0) return true;
            String title = new String(buf, 0, n);
            String lower = title.toLowerCase(Locale.ROOT);
            if (lower.contains(want) && !lower.startsWith("minecraft")) {
                window = hwnd;
                windowTitle = title;
                return false;
            }
            return true;
        }, null);
        return window != null;
    }

    /**
     * Grabs the window and writes it into {@code out} as RGBA at outW x outH: the cropped part of the window
     * (crop fractions of each side), scaled to fit and centred on black.
     * Returns false if the window is gone or minimized.
     */
    public boolean grab(byte[] out, int outW, int outH, float cropL, float cropT, float cropR, float cropB) {
        if (window == null) return false;
        WinDef.RECT rect = new WinDef.RECT();
        if (!User32.INSTANCE.GetClientRect(window, rect)) return false;
        int w = rect.right - rect.left, h = rect.bottom - rect.top;
        if (w < 8 || h < 8) return false; // minimized
        WinDef.HDC screen = User32.INSTANCE.GetDC(null);
        try {
            if (bitmap == null || w != bw || h != bh) {
                release();
                memDc = GDI32.INSTANCE.CreateCompatibleDC(screen);
                bitmap = GDI32.INSTANCE.CreateCompatibleBitmap(screen, w, h);
                GDI32.INSTANCE.SelectObject(memDc, bitmap);
                pixels = new Memory((long) w * h * 4);
                bw = w;
                bh = h;
            }
            if (!User32.INSTANCE.PrintWindow(window, memDc, PW_CLIENTONLY | PW_RENDERFULLCONTENT)) return false;
            WinGDI.BITMAPINFO info = new WinGDI.BITMAPINFO();
            info.bmiHeader.biWidth = w;
            info.bmiHeader.biHeight = -h; // top-down rows
            info.bmiHeader.biPlanes = 1;
            info.bmiHeader.biBitCount = 32;
            info.bmiHeader.biCompression = WinGDI.BI_RGB;
            if (GDI32.INSTANCE.GetDIBits(memDc, bitmap, 0, h, pixels, info, WinGDI.DIB_RGB_COLORS) == 0) return false;
        } finally {
            User32.INSTANCE.ReleaseDC(null, screen);
        }
        // crop, then fit inside the output keeping the shape
        int sx0 = Math.round(w * cropL), sy0 = Math.round(h * cropT);
        int sw = Math.max(1, Math.round(w * (1 - cropL - cropR))), sh = Math.max(1, Math.round(h * (1 - cropT - cropB)));
        float fit = Math.min(outW / (float) sw, outH / (float) sh);
        int dw = Math.max(1, Math.round(sw * fit)), dh = Math.max(1, Math.round(sh * fit));
        int ox = (outW - dw) / 2, oy = (outH - dh) / 2;
        java.util.Arrays.fill(out, (byte) 0);
        byte[] row = new byte[w * 4];
        int lastSy = -1;
        for (int y = 0; y < dh; y++) {
            int sy = Math.min(h - 1, sy0 + (int) ((y + 0.5F) * sh / dh));
            if (sy != lastSy) {
                pixels.read((long) sy * w * 4, row, 0, row.length);
                lastSy = sy;
            }
            int o = ((oy + y) * outW + ox) * 4;
            for (int x = 0; x < dw; x++, o += 4) {
                int sx = Math.min(w - 1, sx0 + (int) ((x + 0.5F) * sw / dw)) * 4;
                out[o] = row[sx + 2];     // BGRA -> RGBA
                out[o + 1] = row[sx + 1];
                out[o + 2] = row[sx];
                out[o + 3] = (byte) 255;
            }
        }
        return true;
    }

    public void release() {
        if (bitmap != null) GDI32.INSTANCE.DeleteObject(bitmap);
        if (memDc != null) GDI32.INSTANCE.DeleteDC(memDc);
        bitmap = null;
        memDc = null;
        pixels = null;
        bw = bh = 0;
    }
}
