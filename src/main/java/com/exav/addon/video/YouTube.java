package com.exav.addon.video;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

/**
 * Finds the actual video and sound streams of a YouTube link, inside the game, with NewPipeExtractor (the library behind
 * the NewPipe app; it also runs YouTube's player code itself, so nothing else is needed).
 */
public final class YouTube {
    private YouTube() {
    }

    /** what to play: a picture stream and, when YouTube serves it separately, a sound stream (else null) */
    public record Streams(String video, String audio, String title) {
    }

    private static boolean ready;

    public static boolean isYouTube(String link) {
        String l = link.toLowerCase(Locale.ROOT);
        return l.contains("youtube.com/") || l.contains("youtu.be/") || l.contains("youtube-nocookie.com/");
    }

    public static synchronized Streams find(String link, int maxHeight) throws Exception {
        if (!ready) {
            NewPipe.init(new JavaDownloader());
            ready = true;
        }
        StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, link);
        // picture + sound together (YouTube only offers this up to 360p)
        VideoStream muxed = best(info.getVideoStreams(), maxHeight);
        // picture only (higher qualities), paired with the best sound
        VideoStream videoOnly = best(info.getVideoOnlyStreams(), maxHeight);
        AudioStream audio = info.getAudioStreams().stream()
                .filter(a -> a.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP && a.isUrl())
                .max(Comparator.comparingInt(AudioStream::getAverageBitrate)).orElse(null);
        if (videoOnly != null && audio != null && (muxed == null || videoOnly.getHeight() > muxed.getHeight())) {
            return new Streams(videoOnly.getContent(), audio.getContent(), info.getName());
        }
        if (muxed != null) return new Streams(muxed.getContent(), null, info.getName());
        if (videoOnly != null) return new Streams(videoOnly.getContent(), audio == null ? null : audio.getContent(), info.getName());
        throw new IOException("YouTube didn't offer a playable version of this video");
    }

    /** the tallest stream that fits the chosen quality (or the smallest one if none fit); skips AV1 */
    private static VideoStream best(List<VideoStream> streams, int maxHeight) {
        List<VideoStream> ok = streams.stream()
                .filter(v -> v.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP && v.isUrl())
                .filter(v -> v.getCodec() == null || !v.getCodec().startsWith("av01"))
                .toList();
        return ok.stream().filter(v -> v.getHeight() <= maxHeight).max(Comparator.comparingInt(VideoStream::getHeight)
                        .thenComparingInt(v -> v.getFps()))
                .orElse(ok.stream().min(Comparator.comparingInt(VideoStream::getHeight)).orElse(null));
    }

    /** NewPipe asks this to fetch pages; plain Java HTTP */
    private static final class JavaDownloader extends Downloader {
        private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15)).build();

        @Override
        public Response execute(Request request) throws IOException {
            HttpRequest.BodyPublisher body = request.dataToSend() == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(request.dataToSend());
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(request.url())).timeout(Duration.ofSeconds(30))
                    .method(request.httpMethod(), body);
            boolean agent = false;
            for (Map.Entry<String, List<String>> h : request.headers().entrySet()) {
                String name = h.getKey();
                if (name.equalsIgnoreCase("host") || name.equalsIgnoreCase("content-length") || name.equalsIgnoreCase("connection")) continue;
                if (name.equalsIgnoreCase("user-agent")) agent = true;
                for (String v : h.getValue()) {
                    try {
                        b.header(name, v);
                    } catch (IllegalArgumentException restricted) {
                        // a header Java sets itself
                    }
                }
            }
            if (!agent) b.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0");
            try {
                HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 429) throw new IOException("YouTube is rate-limiting right now - try again in a bit");
                return new Response(res.statusCode(), "", res.headers().map(), res.body(), res.uri().toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted");
            }
        }
    }
}
