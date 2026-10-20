package dev.habib.client.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.habib.client.config.HabibConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Checks the mod's GitHub repo for a newer release, downloads it, verifies it and installs it when the game closes.
 *
 * Safety: the download must come from github.com/{repo}/releases/download, must match the SHA-256 GitHub lists for
 * the file, and must be a Fabric mod with the id "habibclient" and the version of the release. The new jar is kept
 * outside the mods folder until the game has closed, so the running game is never touched.
 */
public final class Updater {
    public enum State { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, ERROR, DISABLED }

    private static final String MOD_ID = "habibclient";
    private static final Pattern REPO = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    private static final long MAX_BYTES = 30L * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static volatile State state = State.IDLE;
    private static volatile String message = "";
    private static volatile String latest = "";
    private static volatile String assetUrl = "";
    private static volatile String assetSha = "";

    private static String current = "0";
    private static String bakedRepo = "";
    private static Path currentJar;
    private static Path stageDir;
    private static volatile Path stagedJar;
    private static volatile String availabilityNotice = "";
    private static boolean hookInstalled;
    private static boolean noticeSent;
    private static boolean installedNow;

    private Updater() {}

    // ------------------------------------------------------------------ public view

    public static String currentVersion() {
        return current;
    }

    public static State state() {
        return state;
    }

    /** Short text for the menu header. */
    public static String statusText() {
        return switch (state) {
            case IDLE -> "Check for updates";
            case CHECKING -> "Checking for updates...";
            case UP_TO_DATE -> "Up to date";
            case AVAILABLE -> "Update " + latest + " available - click to install";
            case DOWNLOADING -> "Downloading " + latest + "...";
            case READY -> installedNow ? "Updated to " + latest + " - restart the game"
                    : "Update " + latest + " ready - installs when you close the game";
            case ERROR -> "Update check failed (" + message + ") - click to retry";
            case DISABLED -> "Updates off (" + message + ")";
        };
    }

    public static boolean highlighted() {
        return state == State.AVAILABLE || state == State.READY;
    }

    public static void onClick() {
        switch (state) {
            case IDLE, UP_TO_DATE, ERROR -> check(HabibConfig.autoUpdate);
            case AVAILABLE -> downloadAsync();
            default -> { }
        }
    }

    // ------------------------------------------------------------------ startup

    public static void init() {
        FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent(c -> {
            current = c.getMetadata().getVersion().getFriendlyString();
            bakedRepo = readBakedRepo(c);
            currentJar = locateJar(c);
        });
        ClientTickEvents.END_CLIENT_TICK.register(Updater::tick);

        if (currentJar == null) {
            set(State.DISABLED, "dev build");
            return;
        }
        stageDir = FabricLoader.getInstance().getConfigDir().resolve("habibclient-update");
        adoptStaged();
        if (state == State.READY) return;
        if (repo().isEmpty()) {
            set(State.DISABLED, "no repo set");
            return;
        }
        boolean autoDownload = HabibConfig.autoUpdate;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(8000L); // stay out of the way of game start-up
            } catch (InterruptedException e) {
                return;
            }
            check(autoDownload);
        }, "habib-update-start");
        t.setDaemon(true);
        t.start();
    }

    private static String readBakedRepo(ModContainer c) {
        try {
            CustomValue v = c.getMetadata().getCustomValue(MOD_ID);
            if (v == null) return "";
            CustomValue r = v.getAsObject().get("updateRepo");
            return r == null ? "" : r.getAsString().trim();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static Path locateJar(ModContainer c) {
        try {
            for (Path p : c.getOrigin().getPaths()) {
                if (Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    return p.toAbsolutePath();
                }
            }
        } catch (RuntimeException ignored) {
            // not a plain jar (development environment)
        }
        return null;
    }

    /** owner/name from the config file, else the one built into the jar. */
    private static String repo() {
        String r = HabibConfig.updateRepo == null ? "" : HabibConfig.updateRepo.trim();
        if (r.isEmpty()) r = bakedRepo;
        return REPO.matcher(r).matches() ? r : "";
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void set(State s, String msg) {
        message = msg == null ? "" : msg;
        state = s;
    }

    // ------------------------------------------------------------------ check

    private static synchronized boolean busy() {
        return state == State.CHECKING || state == State.DOWNLOADING || state == State.READY;
    }

    public static void check(boolean autoDownload) {
        if (busy()) return;
        String repo = repo();
        if (repo.isEmpty() || currentJar == null) {
            set(State.DISABLED, currentJar == null ? "dev build" : "no repo set");
            return;
        }
        set(State.CHECKING, "");
        Thread t = new Thread(() -> {
            try {
                JsonObject rel = fetchJson("https://api.github.com/repos/" + repo + "/releases/latest");
                String tag = str(rel, "tag_name");
                String ver = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
                if (!Versions.safe(ver)) throw new IOException("odd version tag");
                if (Versions.compare(ver, current) <= 0) {
                    set(State.UP_TO_DATE, "");
                    return;
                }
                JsonObject asset = pickAsset(rel.getAsJsonArray("assets"));
                if (asset == null) throw new IOException("release has no mod jar");
                String url = str(asset, "browser_download_url");
                String digest = str(asset, "digest").toLowerCase(Locale.ROOT);
                if (!url.startsWith("https://github.com/" + repo + "/releases/download/")) throw new IOException("unexpected download address");
                if (!digest.startsWith("sha256:") || digest.length() != 71) throw new IOException("release has no checksum");
                latest = ver;
                assetUrl = url;
                assetSha = digest.substring(7);
                set(State.AVAILABLE, "");
                availabilityNotice = autoDownload
                        ? "[Habib] Update " + ver + " is available; downloading automatically."
                        : "[Habib] Update " + ver + " is available. Click the version in the Habib Client menu to download it.";
                if (autoDownload) download();
            } catch (Exception e) {
                set(State.ERROR, brief(e));
            }
        }, "habib-update-check");
        t.setDaemon(true);
        t.start();
    }

    private static JsonObject pickAsset(JsonArray assets) {
        if (assets == null) return null;
        for (JsonElement e : assets) {
            if (!e.isJsonObject()) continue;
            JsonObject a = e.getAsJsonObject();
            String n = str(a, "name").toLowerCase(Locale.ROOT);
            if (n.endsWith(".jar") && !n.contains("sources") && !n.contains("dev")) return a;
        }
        return null;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static JsonObject fetchJson(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "HabibClient-Updater")
                .GET().build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() == 404) throw new IOException("repo or release not found (is the repo public?)");
        if (r.statusCode() == 403 || r.statusCode() == 429) throw new IOException("GitHub rate limit, try later");
        if (r.statusCode() != 200) throw new IOException("GitHub answered " + r.statusCode());
        return JsonParser.parseString(r.body()).getAsJsonObject();
    }

    private static String brief(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isBlank()) m = e.getClass().getSimpleName();
        return m.length() > 60 ? m.substring(0, 59) + "..." : m;
    }

    // ------------------------------------------------------------------ download + verify

    private static void downloadAsync() {
        if (state != State.AVAILABLE) return;
        Thread t = new Thread(() -> {
            try {
                download();
            } catch (Exception e) {
                set(State.ERROR, brief(e));
            }
        }, "habib-update-download");
        t.setDaemon(true);
        t.start();
    }

    private static void download() throws IOException, InterruptedException {
        String ver = latest;
        if (!Versions.safe(ver)) throw new IOException("odd version");
        set(State.DOWNLOADING, "");
        Files.createDirectories(stageDir);
        Path part = stageDir.resolve("habib-client-" + ver + ".jar.part");
        Path done = stageDir.resolve("habib-client-" + ver + ".jar");

        HttpRequest req = HttpRequest.newBuilder(URI.create(assetUrl))
                .timeout(Duration.ofSeconds(90))
                .header("User-Agent", "HabibClient-Updater")
                .GET().build();
        HttpResponse<InputStream> r = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (r.statusCode() != 200) throw new IOException("download answered " + r.statusCode());
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (InputStream in = r.body(); var out = Files.newOutputStream(part)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_BYTES) throw new IOException("file too large");
                    sha.update(buf, 0, n);
                    out.write(buf, 0, n);
                }
            }
            if (!hex(sha.digest()).equals(assetSha)) throw new IOException("checksum did not match");
            verifyJar(part, ver);
            Files.move(part, done, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("no SHA-256 available");
        } finally {
            Files.deleteIfExists(part);
        }
        stagedJar = done;
        installedNow = false;
        set(State.READY, "");
        installHook();
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    /** The file must really be this mod at the announced version. */
    private static void verifyJar(Path file, String ver) throws IOException {
        try (ZipFile z = new ZipFile(file.toFile())) {
            ZipEntry e = z.getEntry("fabric.mod.json");
            if (e == null) throw new IOException("not a Fabric mod");
            JsonObject o;
            try (var rd = new InputStreamReader(z.getInputStream(e), StandardCharsets.UTF_8)) {
                o = JsonParser.parseReader(rd).getAsJsonObject();
            }
            if (!MOD_ID.equals(str(o, "id"))) throw new IOException("wrong mod");
            if (Versions.compare(str(o, "version"), ver) != 0) throw new IOException("version mismatch");
        }
    }

    // ------------------------------------------------------------------ install

    /** A jar downloaded earlier (and not yet installed): install it now if the OS allows, else on exit. */
    private static void adoptStaged() {
        if (stageDir == null || !Files.isDirectory(stageDir)) return;
        Path best = null;
        String bestVer = "";
        try (Stream<Path> s = Files.list(stageDir)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String n = p.getFileName().toString();
                if (!n.startsWith("habib-client-") || !n.endsWith(".jar")) continue;
                String v = n.substring("habib-client-".length(), n.length() - ".jar".length());
                if (!Versions.safe(v)) continue;
                if (best == null || Versions.compare(v, bestVer) > 0) {
                    best = p;
                    bestVer = v;
                }
            }
        } catch (IOException e) {
            return;
        }
        if (best == null) return;
        if (Versions.compare(bestVer, current) <= 0) { // already installed or older: tidy up
            try {
                Files.deleteIfExists(best);
            } catch (IOException ignored) {
                // harmless
            }
            return;
        }
        try {
            verifyJar(best, bestVer);
        } catch (IOException e) {
            return;
        }
        latest = bestVer;
        stagedJar = best;
        if (!windows()) {
            try {
                Swapper.swap(currentJar, best, currentJar.getParent());
                installedNow = true;
                set(State.READY, "");
                return;
            } catch (IOException ignored) {
                // fall through: try again on exit
            }
        }
        set(State.READY, "");
        installHook();
    }

    private static synchronized void installHook() {
        if (hookInstalled) return;
        hookInstalled = true;
        Runtime.getRuntime().addShutdownHook(new Thread(Updater::spawnSwapper, "habib-update-swap"));
    }

    /** After the game exits, a tiny separate Java process swaps the jars (Windows can't replace a running jar). */
    private static void spawnSwapper() {
        try {
            Path staged = stagedJar;
            if (staged == null || !Files.isRegularFile(staged) || currentJar == null) return;
            String java = ProcessHandle.current().info().command().orElse("java");
            new ProcessBuilder(java, "-cp", staged.toString(), "dev.habib.client.update.Swapper",
                    Long.toString(ProcessHandle.current().pid()), currentJar.toString(), staged.toString(),
                    currentJar.getParent().toString(), stageDir.resolve("last-update.log").toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (Throwable ignored) {
            // the staged jar stays in config/habibclient-update for next start-up
        }
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player == null) return;
        String availableNotice = availabilityNotice;
        if (!availableNotice.isEmpty()) {
            availabilityNotice = "";
            mc.inGameHud.getChatHud().addMessage(Text.literal(availableNotice));
        }
        if (!noticeSent && state == State.READY) {
            noticeSent = true;
            mc.inGameHud.getChatHud().addMessage(Text.literal(installedNow
                    ? "[Habib] Updated to " + latest + ". Restart the game to use it."
                    : "[Habib] Update " + latest + " downloaded. It installs when you close the game."));
        }
    }
}
