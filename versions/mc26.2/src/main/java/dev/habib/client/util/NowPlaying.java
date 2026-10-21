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

    private enum PlayerStatus {
        CHECKING, NOT_RUNNING, NOTHING_PLAYING, PERMISSION_REQUIRED, UNAVAILABLE, PLAYING, PAUSED, STOPPED
    }

    /** One reading. Position is as of {@code nanos}; use {@link #positionMs()} for the live value. */
    public record Snapshot(String artist, String title, String album, String playbackStatus,
                           long posMs, long durMs, long nanos, String artworkUrl) {
        public boolean playing() {
            return "Playing".equalsIgnoreCase(playbackStatus);
        }

        public boolean hasTimeline() {
            return durMs > 0;
        }

        public long positionMs() {
            long p = posMs + (playing() ? (System.nanoTime() - nanos) / 1_000_000L : 0L);
            return durMs > 0 ? Math.max(0L, Math.min(durMs, p)) : Math.max(0L, p);
        }

        public float progress() {
            return durMs > 0 ? Math.max(0f, Math.min(1f, positionMs() / (float) durMs)) : 0f;
        }
    }

    /** URL and downloaded image from the same track, published together to avoid stale pairings. */
    public record ArtworkSnapshot(String url, byte[] bytes) {}

    private static volatile Snapshot current;
    private static volatile PlayerStatus playerStatus = PlayerStatus.CHECKING;
    private static volatile long lastAsk;
    private static volatile long lastSuccess;
    private static volatile ArtworkSnapshot artwork = new ArtworkSnapshot("", null);
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

    /** A clear explanation shown in the HUD when no valid track metadata is available. */
    public static String statusText() {
        return switch (playerStatus) {
            case CHECKING -> "Checking Spotify...";
            case NOT_RUNNING -> "Open Spotify to show a track";
            case NOTHING_PLAYING -> "Nothing is playing";
            case PERMISSION_REQUIRED -> mac()
                    ? "Allow Spotify in macOS System Settings"
                    : "Spotify permission is required";
            case UNAVAILABLE -> "Spotify data is unavailable";
            case PLAYING -> "Playing";
            case PAUSED -> "Paused";
            case STOPPED -> "Stopped";
        };
    }

    /** Album art bytes for the current track, if the local player exposes a cover URL. */
    public static byte[] artworkBytes() {
        return artwork.bytes();
    }

    /** Key for the current art image, used by each Minecraft version's texture loader. */
    public static String artworkUrl() {
        return artwork.url();
    }

    /** Read the current URL and its bytes as one consistent value. */
    public static ArtworkSnapshot artworkSnapshot() {
        return artwork;
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
                        if (current != null) {
                            current = null;
                            playerStatus = PlayerStatus.UNAVAILABLE;
                            updateArtwork("");
                        }
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

    /** Windows: use the Spotify system media session, with a window-title fallback. */
    private static final String WINDOWS_SCRIPT = String.join("\n",
            "$ErrorActionPreference='SilentlyContinue'",
            "[Console]::OutputEncoding=[Text.Encoding]::UTF8",
            "function Fallback{$spotify=Get-Process -Name Spotify -ErrorAction SilentlyContinue|select -First 1;if($spotify){if($spotify.MainWindowTitle){Write-Output ('T:'+$spotify.MainWindowTitle)}else{Write-Output 'N:'}}}",
            "try{",
            "Add-Type -AssemblyName System.Runtime.WindowsRuntime",
            "$m=[System.WindowsRuntimeSystemExtensions].GetMethods()|?{$_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'}|select -First 1",
            "function Aw($op,$t){$n=$m.MakeGenericMethod($t).Invoke($null,@($op));$n.Wait(-1)|Out-Null;$n.Result}",
            "[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]",
            "[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties,Windows.Media.Control,ContentType=WindowsRuntime]",
            "$mgr=Aw ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])",
            "$sessions=@($mgr.GetSessions()|?{$_.SourceAppUserModelId -match 'Spotify' -and $_.GetPlaybackInfo().PlaybackStatus.ToString() -ne 'Closed'})",
            "$s=$sessions|?{$_.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing'}|select -First 1",
            "if(!$s){$s=$sessions|select -First 1}",
            "if($s){",
            "$p=Aw ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])",
            "$t=$s.GetTimelineProperties()",
            "$st=$s.GetPlaybackInfo().PlaybackStatus.ToString()",
            "$pos=$t.Position.TotalMilliseconds",
            "if($st -eq 'Playing'){$pos+=([DateTimeOffset]::UtcNow-$t.LastUpdatedTime).TotalMilliseconds}",
            "if($t.EndTime.TotalMilliseconds -gt 0){$pos=[Math]::Min($pos,$t.EndTime.TotalMilliseconds)}",
            "$artist=([string]$p.Artist)-replace '[\\t\\r\\n]',' '",
            "$title=([string]$p.Title)-replace '[\\t\\r\\n]',' '",
            "$album=([string]$p.AlbumTitle)-replace '[\\t\\r\\n]',' '",
            "$cover=''",
            "try{if($p.Thumbnail){[void][Windows.Storage.Streams.DataReader,Windows.Storage.Streams,ContentType=WindowsRuntime];$sha=[Security.Cryptography.SHA256]::Create();$key=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($artist+$title+$album)))).Replace('-','');$sha.Dispose();$path=Join-Path $env:TEMP ('habibclient-spotify-'+$key+'.art');if(!(Test-Path $path)){$stream=Aw ($p.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType]);$reader=New-Object Windows.Storage.Streams.DataReader($stream.GetInputStreamAt(0));$size=[uint32]$stream.Size;[void](Aw ($reader.LoadAsync($size)) ([uint32]));$bytes=New-Object byte[] $size;$reader.ReadBytes($bytes);[System.IO.File]::WriteAllBytes($path,$bytes);$reader.Dispose();$stream.Dispose()};$cover=([System.Uri]$path).AbsoluteUri}}catch{}",
            "Write-Output ($artist+\"`t\"+$title+\"`t\"+$album+\"`t\"+$st+\"`t\"+[int64]$pos+\"`t\"+[int64]$t.EndTime.TotalMilliseconds+\"`t\"+$cover)",
            "}else{",
            "Fallback",
            "}}catch{Fallback}");

    private static void poll() throws IOException, InterruptedException {
        if (windows()) {
            String encoded = Base64.getEncoder().encodeToString(WINDOWS_SCRIPT.getBytes(StandardCharsets.UTF_16LE));
            String out = run(List.of("powershell", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                    "-EncodedCommand", encoded)).trim();
            if (out.startsWith("T:")) {
                if (!applyWindowTitle(out.substring(2).trim())) clearTrack(PlayerStatus.NOT_RUNNING);
            } else if (out.startsWith("N:")) {
                clearTrack(PlayerStatus.NOTHING_PLAYING);
            } else if (out.isBlank()) {
                clearTrack(PlayerStatus.NOT_RUNNING);
            } else if (!applyTabbed(out, 1.0, 1.0)) {
                clearTrack(PlayerStatus.UNAVAILABLE);
            }
        } else if (mac()) {
            // Use Spotify's Apple-event property codes directly so osascript does not depend on
            // resolving the app's scripting dictionary before the metadata request runs.
            String script = String.join("\n",
                    "if application \"Spotify\" is running then",
                    "    tell application \"Spotify\"",
                    "        set currentTrack to «property pTrk»",
                    "        set trackArtist to «property pArt» of currentTrack",
                    "        set trackTitle to «property pnam» of currentTrack",
                    "        set trackAlbum to «property pAlb» of currentTrack",
                    "        set playbackState to «property pPlS» as string",
                    "        set playbackPosition to «property pPos» as string",
                    "        set trackDuration to «property pDur» of currentTrack as string",
                    "        set coverURL to \"\"",
                    "        try",
                    "            set coverURL to «property aUrl» of currentTrack as text",
                    "        end try",
                    "        set tabChar to ASCII character 9",
                    "        trackArtist & tabChar & trackTitle & tabChar & trackAlbum & tabChar & playbackState & tabChar & playbackPosition & tabChar & trackDuration & tabChar & coverURL",
                    "    end tell",
                    "else",
                    "    \"\"",
                    "end if");
            String out = run(List.of("osascript", "-e", script)).trim();
            String error = out.toLowerCase(Locale.ROOT);
            // Spotify's scripting dictionary reports both position and duration in seconds.
            if (out.contains("-1743") || error.contains("not authorized") || error.contains("not permitted to send apple events")) {
                clearTrack(PlayerStatus.PERMISSION_REQUIRED);
            } else if (out.isBlank()) {
                clearTrack(PlayerStatus.NOT_RUNNING);
            } else if (!applyTabbed(out, 1000.0, 1000.0)) {
                clearTrack(PlayerStatus.UNAVAILABLE);
            }
        } else {
            String out = run(List.of("playerctl", "-p", "spotify", "metadata", "--format",
                    "{{artist}}\t{{title}}\t{{album}}\t{{status}}\t{{position}}\t{{mpris:length}}\t{{mpris:artUrl}}"));
            out = out.trim();
            if (out.isBlank()) clearTrack(PlayerStatus.NOT_RUNNING);
            else if (!applyTabbed(out, 0.001, 0.001)) clearTrack(PlayerStatus.UNAVAILABLE);
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
    static boolean applyWindowTitle(String title) {
        if (title.isEmpty()) return false;
        if (title.equals("Advertisement")) {
            accept(new Snapshot("Spotify", "Advertisement", "", "Playing", 0, 0, System.nanoTime(), ""));
        } else if (title.toLowerCase(Locale.ROOT).startsWith("spotify") && !title.contains(" - ")) {
            clearTrack(PlayerStatus.NOTHING_PLAYING);
        } else {
            String track = title.replaceFirst("(?i)^spotify(?: premium)?\\s*[-–—:]\\s*", "").trim();
            int dash = track.indexOf(" - ");
            if (dash < 0) dash = track.indexOf(" – ");
            if (dash < 0) dash = track.indexOf(" — ");
            String artist = dash > 0 ? track.substring(0, dash).trim() : "";
            String song = dash > 0 ? track.substring(dash + 3).trim() : track;
            accept(new Snapshot(clip(artist), clip(song), "", "Playing", 0, 0, System.nanoTime(), ""));
        }
        return true;
    }

    /** artist TAB title TAB album TAB state [TAB position TAB length TAB artwork URL]. */
    static boolean applyTabbed(String out, double posToMs, double durToMs) {
        String[] parts = out.split("\t", -1);
        if (parts.length < 4) return false;
        String artist = cleanField(parts[0]);
        String title = cleanField(parts[1]);
        if (artist.isEmpty() && title.isEmpty()) {
            clearTrack(PlayerStatus.NOTHING_PLAYING);
            return true;
        }
        String album = cleanField(parts[2]);
        String state = normalizePlaybackState(cleanField(parts[3]));
        long pos = parts.length > 4 ? toMs(parts[4], posToMs) : 0L;
        long dur = parts.length > 5 ? toMs(parts[5], durToMs) : 0L;
        String art = parts.length > 6 ? cleanField(parts[6]) : "";
        accept(new Snapshot(clip(artist), clip(title), clip(album), state, pos, dur, System.nanoTime(), art));
        return true;
    }

    private static String normalizePlaybackState(String state) {
        if (state.equalsIgnoreCase("playing") || state.equals("kPSP")) return "Playing";
        if (state.equalsIgnoreCase("paused") || state.equals("kPSp")) return "Paused";
        if (state.equalsIgnoreCase("stopped") || state.equals("kPSS")) return "Stopped";
        return "Unknown";
    }

    private static void accept(Snapshot snapshot) {
        current = snapshot;
        playerStatus = switch (snapshot.playbackStatus()) {
            case "Playing" -> PlayerStatus.PLAYING;
            case "Paused" -> PlayerStatus.PAUSED;
            case "Stopped" -> PlayerStatus.STOPPED;
            default -> PlayerStatus.UNAVAILABLE;
        };
        lastSuccess = System.currentTimeMillis();
        updateArtwork(snapshot.artworkUrl());
    }

    private static void clearTrack(PlayerStatus status) {
        current = null;
        playerStatus = status;
        lastSuccess = System.currentTimeMillis();
        updateArtwork("");
    }

    private static String cleanField(String field) {
        return field.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private static synchronized void updateArtwork(String url) {
        String next = url == null ? "" : url.trim();
        ArtworkSnapshot currentArtwork = artwork;
        if (!Objects.equals(currentArtwork.url(), next)) {
            artwork = new ArtworkSnapshot(next, null);
            currentArtwork = artwork;
            lastArtworkAttempt = 0L;
        }
        if (next.isEmpty() || currentArtwork.bytes() != null || System.currentTimeMillis() - lastArtworkAttempt < ART_RETRY_MS) return;
        lastArtworkAttempt = System.currentTimeMillis();
        artworkExecutor().execute(() -> {
            byte[] bytes = downloadArtwork(next);
            ArtworkSnapshot latest = artwork;
            if (Objects.equals(latest.url(), next) && bytes != null) artwork = new ArtworkSnapshot(next, bytes);
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
