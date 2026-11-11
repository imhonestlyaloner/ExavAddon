package com.exav.addon.video;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The video player's helper programs (ffmpeg to decode, yt-dlp + Deno to read YouTube), fetched by the addon itself the
 * first time they're needed: official GitHub releases, checked against their published SHA-256 sums, kept in
 * .minecraft/config/exav-addon/tools. Nothing is installed on the computer. Windows only; elsewhere the programs on
 * PATH are used.
 */
public final class Tools {
    private Tools() {
    }

    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(20)).build();

    private record Tool(String name, String exe, String url, String sumsUrl, String sumsName, String zipEntry, long approxBytes) {
    }

    private static final Tool FFMPEG = new Tool("ffmpeg", "ffmpeg.exe",
            "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip",
            "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/checksums.sha256",
            "ffmpeg-master-latest-win64-gpl.zip", "bin/ffmpeg.exe", 200_000_000L);
    private static final Tool YT_DLP = new Tool("yt-dlp", "yt-dlp.exe",
            "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe",
            "https://github.com/yt-dlp/yt-dlp/releases/latest/download/SHA2-256SUMS",
            "yt-dlp.exe", null, 18_000_000L);
    private static final Tool DENO = new Tool("Deno", "deno.exe",
            "https://github.com/denoland/deno/releases/latest/download/deno-x86_64-pc-windows-msvc.zip",
            "https://github.com/denoland/deno/releases/latest/download/deno-x86_64-pc-windows-msvc.zip.sha256sum",
            null, "deno.exe", 45_000_000L);

    public static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("exav-addon").resolve("tools");
    }

    /** ffmpeg to run: the given path, or (for "auto") the addon's own copy, downloaded if needed */
    public static String ffmpeg(String setting, Consumer<String> status) throws IOException {
        return resolve(setting, FFMPEG, status, false);
    }

    public static String ytDlp(String setting, Consumer<String> status) throws IOException {
        // yt-dlp has to keep up with YouTube: get a fresh copy when ours is over a week old
        return resolve(setting, YT_DLP, status, true);
    }

    /** Deno (yt-dlp runs YouTube's player code with it); null when not on Windows / not auto */
    public static String deno(String ytDlpSetting, Consumer<String> status) throws IOException {
        if (!auto(ytDlpSetting) || !windows()) return null;
        return resolve("auto", DENO, status, false);
    }

    /** throws away our yt-dlp so the next play downloads the newest one */
    public static void forgetYtDlp() throws IOException {
        Files.deleteIfExists(folder().resolve(YT_DLP.exe));
    }

    public static boolean isAuto(String setting) {
        return auto(setting);
    }

    private static boolean auto(String setting) {
        String s = setting.trim().toLowerCase(Locale.ROOT);
        // bare program names (the old defaults) count as auto too
        return s.isEmpty() || s.equals("auto") || s.equals("ffmpeg") || s.equals("ffmpeg.exe") || s.equals("yt-dlp") || s.equals("yt-dlp.exe");
    }

    private static synchronized String resolve(String setting, Tool tool, Consumer<String> status, boolean refreshWeekly) throws IOException {
        if (!auto(setting)) return setting.trim();
        if (!windows()) return tool.name.equals("Deno") ? "deno" : tool.name; // use the one on PATH
        Path exe = folder().resolve(tool.exe);
        boolean stale = refreshWeekly && Files.isRegularFile(exe)
                && System.currentTimeMillis() - Files.getLastModifiedTime(exe).toMillis() > 7L * 24 * 3600 * 1000;
        if (Files.isRegularFile(exe) && !stale) return exe.toString();
        try {
            download(tool, exe, status);
        } catch (IOException e) {
            if (stale && Files.isRegularFile(exe)) return exe.toString(); // keep using the old one
            throw e;
        }
        return exe.toString();
    }

    private static void download(Tool tool, Path exe, Consumer<String> status) throws IOException {
        Files.createDirectories(folder());
        status.accept("Downloading " + tool.name + " (one time)...");
        String expected = expectedSha256(tool);
        Path tmp = folder().resolve(tool.exe + ".download");
        try {
            HttpResponse<InputStream> res = send(tool.url);
            long total = res.headers().firstValueAsLong("content-length").orElse(tool.approxBytes);
            MessageDigest sha;
            try {
                sha = MessageDigest.getInstance("SHA-256");
            } catch (Exception e) {
                throw new IOException(e);
            }
            try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(tmp)) {
                byte[] buf = new byte[1 << 16];
                long done = 0;
                int lastPct = -1, n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    sha.update(buf, 0, n);
                    done += n;
                    int pct = (int) Math.min(99, done * 100 / Math.max(1, total));
                    if (pct != lastPct) {
                        lastPct = pct;
                        status.accept("Downloading " + tool.name + " (one time): " + pct + "%  (" + done / 1_000_000 + " / " + total / 1_000_000 + " MB)");
                    }
                }
            }
            String got = HexFormat.of().formatHex(sha.digest());
            if (!got.equalsIgnoreCase(expected)) throw new IOException(tool.name + " download didn't match its checksum - try again");
            if (tool.zipEntry == null) {
                Files.move(tmp, exe, StandardCopyOption.REPLACE_EXISTING);
            } else {
                status.accept("Unpacking " + tool.name + "...");
                extract(tmp, tool.zipEntry, exe);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void extract(Path zip, String entryEnd, Path exe) throws IOException {
        Path part = exe.resolveSibling(exe.getFileName() + ".part");
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (!e.isDirectory() && e.getName().replace('\\', '/').endsWith(entryEnd)) {
                    Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
                    Files.move(part, exe, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        } finally {
            Files.deleteIfExists(part);
        }
        throw new IOException("couldn't find " + entryEnd + " in the download");
    }

    /** the release's published SHA-256 for the file we're about to download */
    private static String expectedSha256(Tool tool) throws IOException {
        String text;
        try (InputStream in = send(tool.sumsUrl).body()) {
            text = new String(in.readAllBytes());
        }
        for (String line : text.split("\\R")) {
            String l = line.trim();
            if (tool.sumsName != null) { // "<hash>  <file name>" lines
                String[] parts = l.split("\\s+");
                if (parts.length >= 2 && parts[parts.length - 1].replace("*", "").equals(tool.sumsName)) return parts[0];
            } else { // a single hash, possibly as "Hash : <hash>"
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b([0-9a-fA-F]{64})\\b").matcher(l);
                if (m.find()) return m.group(1);
            }
        }
        throw new IOException("couldn't read the checksum for " + tool.name);
    }

    private static HttpResponse<InputStream> send(String url) throws IOException {
        try {
            HttpResponse<InputStream> res = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(15))
                    .header("User-Agent", "ExavAddon").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() / 100 != 2) {
                res.body().close();
                throw new IOException("download failed (HTTP " + res.statusCode() + ")");
            }
            return res;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");
        }
    }
}
