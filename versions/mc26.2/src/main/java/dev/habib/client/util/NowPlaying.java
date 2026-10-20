package dev.habib.client.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Reads the Spotify desktop app through the platform's local media controls. No Spotify login or
 * developer token is needed. Polling, image loading, and playback commands all run off Minecraft's
 * render and tick threads.
 */
public final class NowPlaying {
    private static final long ACTIVE_WINDOW_MS = 10_000L;
    private static final long STALE_AFTER_MS = 15_000L;
    private static final long ART_RETRY_MS = 60_000L;
    private static final int MAX_CHARS = 60;
    private static final int MAX_ART_BYTES = 4 * 1024 * 1024;

    /** One reading. Position is as of {@code nanos}; use {@link #positionMs()} for the live value. */
    public record Snapshot(String artist, String title, boolean playing, long posMs, long durMs,
                           long nanos, String artworkUrl) {
        public boolean hasTimeline() {
            return durMs > 0;
        }

        public long positionMs() {
            long p = posMs + (playing ? (System.nanoTime() - nanos) / 1_000_000L : 0L);
            return durMs > 0 ? Math.max(0L, Math.min(durMs, p)) : Math.max(0L, p);
        }

        public float progress() {
            return durMs > 0 ? Math.max(0f, Math.min(1f, positionMs() / (float) durMs)) : 0f;
        }
    }

    private static volatile Snapshot current;
    private static volatile long lastAsk;
    private static volatile long lastSuccess;
    private static volatile String artworkUrl = "";
    private static volatile byte[] artworkBytes;
    private static volatile long lastArtworkAttempt;
    private static Thread pollThread;
    private static ExecutorService commandExecutor;
    private static ExecutorService artworkExecutor;

    private NowPlaying() {}

    /** Called from the HUD every frame; null means "nothing to show". */
    public static Snapshot snapshot() {
        lastAsk = System.currentTimeMillis();
        ensurePollThread();
        return current;
    }

    /** Album art bytes for the current track, if the local player exposes a cover URL. */
    public static byte[] artworkBytes() {
        return artworkBytes;
    }

    /** Key for the current art image, used by each Minecraft version's texture loader. */
    public static String artworkUrl() {
        return artworkUrl;
    }

    /** Queue a Spotify transport action without stalling the game thread. */
    public static void previous() { control("previous"); }
    public static void playPause() { control("playpause"); }
    public static void next() { control("next"); }

    /** 135000 -> "2:15" (hours appear only when needed). */
    public static String clock(long ms) {
        long s = Math.max(0L, ms / 1000L);
        long h = s / 3600L;
        long sec = s % 60L;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, (s / 60L) % 60L, sec)
                : String.format(Locale.ROOT, "%d:%02d", s / 60L, sec);
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static boolean mac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    private static synchronized void ensurePollThread() {
        if (pollThread != null && pollThread.isAlive()) return;
        pollThread = new Thread(() -> {
            long nextPoll = 0L;
            while (!Thread.currentThread().isInterrupted()) {
                long now = System.currentTimeMillis();
                try {
                    if (now - lastAsk < ACTIVE_WINDOW_MS && now >= nextPoll) {
                        poll();
                        nextPoll = System.currentTimeMillis() + (windows() ? 2500L : 1500L);
                    } else if (now - lastSuccess > STALE_AFTER_MS) {
                        current = null;
                        nextPoll = now + 400L;
                    }
                    Thread.sleep(200L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Throwable ignored) {
                    // Keep the last good track through transient player/OS errors. It expires above.
                    nextPoll = System.currentTimeMillis() + 1500L;
                }
            }
        }, "habib-nowplaying");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    private static synchronized ExecutorService commandExecutor() {
        if (commandExecutor == null || commandExecutor.isShutdown()) {
            commandExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "habib-spotify-controls");
                t.setDaemon(true);
                return t;
            });
        }
        return commandExecutor;
    }

    private static synchronized ExecutorService artworkExecutor() {
        if (artworkExecutor == null || artworkExecutor.isShutdown()) {
            artworkExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "habib-spotify-artwork");
                t.setDaemon(true);
                return t;
            });
        }
        return artworkExecutor;
    }

    private static void control(String action) {
        ensurePollThread();
        commandExecutor().execute(() -> {
            try {
                sendControl(action);
            } catch (Throwable ignored) {
                // Missing player, permission, or platform media support: keep gameplay responsive.
            }
        });
    }

    // ------------------------------------------------------------------ per-OS readers

    /** Windows: use the Spotify system media session, with a title-only fallback. */
    private static final String WINDOWS_SCRIPT = String.join("\n",
            "$ErrorActionPreference='SilentlyContinue'",
            "[Console]::OutputEncoding=[Text.Encoding]::UTF8",
            "Add-Type -AssemblyName System.Runtime.WindowsRuntime",
            "$m=[System.WindowsRuntimeSystemExtensions].GetMethods()|?{$_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'}|select -First 1",
            "function Aw($op,$t){$n=$m.MakeGenericMethod($t).Invoke($null,@($op));$n.Wait(-1)|Out-Null;$n.Result}",
            "[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]",
            "[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties,Windows.Media.Control,ContentType=WindowsRuntime]",
            "$mgr=Aw ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])",
            "$s=$mgr.GetSessions()|?{$_.SourceAppUserModelId -like '*Spotify*'}|select -First 1",
            "if($s){",
            "$p=Aw ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])",
            "$t=$s.GetTimelineProperties()",
            "$st=$s.GetPlaybackInfo().PlaybackStatus.ToString()",
            "$pos=$t.Position.TotalMilliseconds",
            "if($st -eq 'Playing'){$pos+=([DateTimeOffset]::UtcNow-$t.LastUpdatedTime).TotalMilliseconds}",
            "$cover=''",
            "try{if($p.Thumbnail){[void][Windows.Storage.Streams.DataReader,Windows.Storage.Streams,ContentType=WindowsRuntime];$sha=[Security.Cryptography.SHA256]::Create();$key=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($p.Artist+$p.Title)))).Replace('-','');$sha.Dispose();$path=Join-Path $env:TEMP ('habibclient-spotify-'+$key+'.art');if(!(Test-Path $path)){$stream=Aw ($p.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType]);$reader=New-Object Windows.Storage.Streams.DataReader($stream.GetInputStreamAt(0));$size=[uint32]$stream.Size;[void](Aw ($reader.LoadAsync($size)) ([uint32]));$bytes=New-Object byte[] $size;$reader.ReadBytes($bytes);[System.IO.File]::WriteAllBytes($path,$bytes);$reader.Dispose();$stream.Dispose()};$cover=([System.Uri]$path).AbsoluteUri}}catch{}",
            "Write-Output ($p.Artist+\"`t\"+$p.Title+\"`t\"+$st+\"`t\"+[int64]$pos+\"`t\"+[int64]$t.EndTime.TotalMilliseconds+\"`t\"+$cover)",
            "}else{",
            "$w=Get-Process Spotify|?{$_.MainWindowTitle}|select -First 1 -ExpandProperty MainWindowTitle",
            "if($w){Write-Output ('T:'+$w)}",
            "}");

    private static void poll() throws IOException, InterruptedException {
        if (windows()) {
            String encoded = Base64.getEncoder().encodeToString(WINDOWS_SCRIPT.getBytes(StandardCharsets.UTF_16LE));
            String out = run(List.of("powershell", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                    "-EncodedCommand", encoded)).trim();
            if (out.startsWith("T:")) applyWindowTitle(out.substring(2).trim());
            else applyTabbed(out, 1.0, 1.0);
        } else if (mac()) {
            // Use Spotify's Apple-event property codes directly so osascript does not depend on
            // resolving the app's scripting dictionary before the metadata request runs.
            String script = String.join("\n",
                    "if application \"Spotify\" is running then",
                    "    tell application \"Spotify\"",
                    "        set currentTrack to «property pTrk»",
                    "        set trackArtist to «property pArt» of currentTrack",
                    "        set trackTitle to «property pnam» of currentTrack",
                    "        set playbackState to «property pPlS» as string",
                    "        set playbackPosition to «property pPos» as string",
                    "        set trackDuration to «property pDur» of currentTrack as string",
                    "        set coverURL to \"\"",
                    "        try",
                    "            set coverURL to «property aUrl» of currentTrack as text",
                    "        end try",
                    "        set tabChar to ASCII character 9",
                    "        trackArtist & tabChar & trackTitle & tabChar & playbackState & tabChar & playbackPosition & tabChar & trackDuration & tabChar & coverURL",
                    "    end tell",
                    "else",
                    "    \"\"",
                    "end if");
            String out = run(List.of("osascript", "-e", script)).trim();
            if (out.contains("-1743") || out.toLowerCase(Locale.ROOT).contains("not authorized")) {
                accept(new Snapshot("Allow Spotify access", "Permission needed", false, 0, 0,
                        System.nanoTime(), ""));
            } else {
                applyTabbed(out, 1000.0, 1000.0); // Spotify reports both values in seconds
            }
        } else {
            String out = run(List.of("playerctl", "-p", "spotify", "metadata", "--format",
                    "{{artist}}\t{{title}}\t{{status}}\t{{position}}\t{{mpris:length}}\t{{mpris:artUrl}}"));
            applyTabbed(out.trim(), 0.001, 0.001); // both timeline values are in microseconds
        }
    }

    private static void sendControl(String action) throws IOException, InterruptedException {
        if (mac()) {
            String command = switch (action) {
                case "previous" -> "previous track";
                case "next" -> "next track";
                default -> "playpause";
            };
            run(List.of("osascript", "-e", "tell application \"Spotify\" to " + command));
        } else if (windows()) {
            String method = switch (action) {
                case "previous" -> "TrySkipPreviousAsync";
                case "next" -> "TrySkipNextAsync";
                default -> "TryTogglePlayPauseAsync";
            };
            String script = String.join("\n",
                    "$ErrorActionPreference='SilentlyContinue'",
                    "Add-Type -AssemblyName System.Runtime.WindowsRuntime",
                    "$m=[System.WindowsRuntimeSystemExtensions].GetMethods()|?{$_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'}|select -First 1",
                    "function Aw($op,$t){$n=$m.MakeGenericMethod($t).Invoke($null,@($op));$n.Wait(-1)|Out-Null;$n.Result}",
                    "[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]",
                    "$mgr=Aw ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])",
                    "$s=$mgr.GetSessions()|?{$_.SourceAppUserModelId -like '*Spotify*'}|select -First 1",
                    "if($s){[void](Aw ($s." + method + "()) ([bool]))}");
            String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            run(List.of("powershell", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-EncodedCommand", encoded));
        } else {
            String command = switch (action) {
                case "previous" -> "previous";
                case "next" -> "next";
                default -> "play-pause";
            };
            run(List.of("playerctl", "-p", "spotify", command));
        }
    }

    /** Fallback (no timeline): the window title is "Artist - Song" while playing. */
    static void applyWindowTitle(String title) {
        if (title.isEmpty()) return;
        if (title.equals("Advertisement")) {
            accept(new Snapshot("Spotify", "Advertisement", true, 0, 0, System.nanoTime(), ""));
        } else if (title.toLowerCase(Locale.ROOT).startsWith("spotify") && !title.contains(" - ")) {
            accept(new Snapshot("Spotify", "Paused", false, 0, 0, System.nanoTime(), ""));
        } else {
            int dash = title.indexOf(" - ");
            String artist = dash > 0 ? title.substring(0, dash).trim() : "";
            String song = dash > 0 ? title.substring(dash + 3).trim() : title;
            accept(new Snapshot(clip(artist), clip(song), true, 0, 0, System.nanoTime(), ""));
        }
    }

    /** artist TAB title TAB state [TAB position TAB length TAB artwork URL]. */
    static void applyTabbed(String out, double posToMs, double durToMs) {
        String[] parts = out.split("\t", -1);
        if (parts.length < 3 || (parts[0].trim().isEmpty() && parts[1].trim().isEmpty())) return;
        boolean isPlaying = parts[2].trim().equalsIgnoreCase("playing");
        long pos = parts.length > 3 ? toMs(parts[3], posToMs) : 0L;
        long dur = parts.length > 4 ? toMs(parts[4], durToMs) : 0L;
        String art = parts.length > 5 ? parts[5].trim() : "";
        accept(new Snapshot(clip(parts[0].trim()), clip(parts[1].trim()), isPlaying, pos, dur,
                System.nanoTime(), art));
    }

    private static void accept(Snapshot snapshot) {
        current = snapshot;
        lastSuccess = System.currentTimeMillis();
        updateArtwork(snapshot.artworkUrl());
    }

    private static synchronized void updateArtwork(String url) {
        String next = url == null ? "" : url.trim();
        if (!Objects.equals(artworkUrl, next)) {
            artworkUrl = next;
            artworkBytes = null;
            lastArtworkAttempt = 0L;
        }
        if (next.isEmpty() || artworkBytes != null || System.currentTimeMillis() - lastArtworkAttempt < ART_RETRY_MS) return;
        lastArtworkAttempt = System.currentTimeMillis();
        artworkExecutor().execute(() -> {
            byte[] bytes = downloadArtwork(next);
            if (Objects.equals(artworkUrl, next) && bytes != null) artworkBytes = bytes;
        });
    }

    private static byte[] downloadArtwork(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (!"https".equalsIgnoreCase(scheme) && !"file".equalsIgnoreCase(scheme)) return null;
            URLConnection connection = uri.toURL().openConnection();
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(4000);
            if (connection instanceof HttpURLConnection http) {
                http.setRequestProperty("User-Agent", "HabibClient/1.14.0");
                http.setRequestProperty("Accept", "image/*");
                String type = http.getContentType();
                if (type != null && !type.toLowerCase(Locale.ROOT).startsWith("image/")) return null;
            }
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[8192];
                int count;
                while ((count = in.read(chunk)) >= 0) {
                    if (out.size() + count > MAX_ART_BYTES) return null;
                    out.write(chunk, 0, count);
                }
                return out.size() == 0 ? null : out.toByteArray();
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long toMs(String raw, double scale) {
        try {
            return Math.max(0L, Math.round(Double.parseDouble(raw.trim().replace(',', '.')) * scale));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String clip(String s) {
        return s.length() > MAX_CHARS ? s.substring(0, MAX_CHARS - 1) + "..." : s;
    }

    private static String run(List<String> command) throws IOException, InterruptedException {
        Process p;
        try {
            p = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException missing) {
            return ""; // e.g. playerctl not installed
        }
        try (InputStream in = p.getInputStream()) {
            if (!p.waitFor(8, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "";
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int n;
            while ((n = in.read(chunk)) > 0 && buf.size() < 8192) buf.write(chunk, 0, n);
            return buf.toString(StandardCharsets.UTF_8);
        } finally {
            p.destroy();
        }
    }
}
