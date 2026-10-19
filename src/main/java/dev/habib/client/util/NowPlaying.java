package dev.habib.client.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Reads what the Spotify desktop app is playing, from your own computer only: the Windows media session
 * (with a window-title fallback), AppleScript on macOS, playerctl on Linux. No Spotify account, login, token
 * or network access. Polling only happens while the HUD module is enabled; the progress between polls is
 * worked out locally so the bar moves smoothly.
 */
public final class NowPlaying {
    private static final long ACTIVE_WINDOW_MS = 10_000L;
    private static final int MAX_CHARS = 60;

    /** One reading. Position is as of {@code nanos}; use {@link #positionMs()} for the live value. */
    public record Snapshot(String artist, String title, boolean playing, long posMs, long durMs, long nanos) {
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
    private static Thread thread;

    private NowPlaying() {}

    /** Called from the HUD every frame; null means "nothing to show". */
    public static Snapshot snapshot() {
        lastAsk = System.currentTimeMillis();
        ensureThread();
        return current;
    }

    /** 135000 -> "2:15" (hours appear only when needed). */
    public static String clock(long ms) {
        long s = Math.max(0L, ms / 1000L);
        long h = s / 3600L;
        long m = (s / 60L) % 60L;
        long sec = s % 60L;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(Locale.ROOT, "%d:%02d", s / 60L, sec);
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static synchronized void ensureThread() {
        if (thread != null) return;
        thread = new Thread(() -> {
            while (true) {
                try {
                    if (System.currentTimeMillis() - lastAsk < ACTIVE_WINDOW_MS) poll();
                    Thread.sleep(windows() ? 3500L : 2000L); // PowerShell is slower to start than the others
                } catch (InterruptedException e) {
                    return;
                } catch (Throwable t) {
                    current = null;
                    try {
                        Thread.sleep(3000L);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }, "habib-nowplaying");
        thread.setDaemon(true);
        thread.start();
    }

    // ------------------------------------------------------------------ per-OS readers

    /** Windows: the Spotify entry in the system media controls has artist, title, position and length. */
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
            "Write-Output ($p.Artist+\"`t\"+$p.Title+\"`t\"+$st+\"`t\"+[int64]$pos+\"`t\"+[int64]$t.EndTime.TotalMilliseconds)",
            "}else{",
            "$w=Get-Process Spotify|?{$_.MainWindowTitle}|select -First 1 -ExpandProperty MainWindowTitle",
            "if($w){Write-Output ('T:'+$w)}",
            "}");

    private static void poll() throws IOException, InterruptedException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String encoded = Base64.getEncoder().encodeToString(WINDOWS_SCRIPT.getBytes(StandardCharsets.UTF_16LE));
            String out = run(List.of("powershell", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                    "-EncodedCommand", encoded)).trim();
            if (out.startsWith("T:")) applyWindowTitle(out.substring(2).trim());
            else applyTabbed(out, 1.0, 1.0);
        } else if (os.contains("mac")) {
            String out = run(List.of("osascript", "-e",
                    "if application \"Spotify\" is running then tell application \"Spotify\" to "
                            + "(artist of current track) & \"\\t\" & (name of current track) & \"\\t\" & (player state as string)"
                            + " & \"\\t\" & (player position as string) & \"\\t\" & (duration of current track as string)")).trim();
            if (out.contains("-1743") || out.toLowerCase(Locale.ROOT).contains("not authorized")) {
                // macOS asks once whether this app may control Spotify; "Don't Allow" ends up here
                current = new Snapshot("Turn on Automation > Spotify", "Permission needed", false, 0, 0, System.nanoTime());
            } else {
                applyTabbed(out, 1000.0, 1.0); // position in seconds, duration in milliseconds
            }
        } else {
            String out = run(List.of("playerctl", "-p", "spotify", "metadata", "--format",
                    "{{artist}}\t{{title}}\t{{status}}\t{{position}}\t{{mpris:length}}")).trim();
            applyTabbed(out, 0.001, 0.001); // both in microseconds
        }
    }

    /** Fallback (no timeline): the window title is "Artist - Song" while playing, plain "Spotify ..." when idle. */
    static void applyWindowTitle(String title) {
        if (title.isEmpty()) {
            current = null;
        } else if (title.equals("Advertisement")) {
            current = new Snapshot("Spotify", "Advertisement", true, 0, 0, System.nanoTime());
        } else if (title.toLowerCase(Locale.ROOT).startsWith("spotify") && !title.contains(" - ")) {
            current = new Snapshot("Spotify", "Paused", false, 0, 0, System.nanoTime());
        } else {
            int dash = title.indexOf(" - ");
            String artist = dash > 0 ? title.substring(0, dash).trim() : "";
            String song = dash > 0 ? title.substring(dash + 3).trim() : title;
            current = new Snapshot(clip(artist), clip(song), true, 0, 0, System.nanoTime());
        }
    }

    /** artist TAB title TAB state [TAB position TAB length]; the two scales turn those numbers into milliseconds. */
    static void applyTabbed(String out, double posToMs, double durToMs) {
        String[] parts = out.split("\t", -1);
        if (parts.length < 3 || (parts[0].trim().isEmpty() && parts[1].trim().isEmpty())) {
            current = null;
            return;
        }
        boolean isPlaying = parts[2].trim().equalsIgnoreCase("playing");
        long pos = parts.length > 3 ? toMs(parts[3], posToMs) : 0L;
        long dur = parts.length > 4 ? toMs(parts[4], durToMs) : 0L;
        current = new Snapshot(clip(parts[0].trim()), clip(parts[1].trim()), isPlaying, pos, dur, System.nanoTime());
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
            // wait first (with a limit) so a pending macOS permission prompt can't hang the poller forever
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
