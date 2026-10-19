package dev.habib.client.packs;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal Modrinth client: searches resource packs for the running Minecraft version and downloads
 * them into the game's resourcepacks folder. All network work runs on a background pool.
 */
public final class ModrinthPacks {
    public record Hit(String id, String title, String author, String description, long downloads) {}

    private static final String API = "https://api.modrinth.com/v2";
    private static final long MAX_BYTES = 300L * 1024 * 1024;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "habib-modrinth");
        t.setDaemon(true);
        return t;
    });

    private static final Path INDEX = FabricLoader.getInstance().getConfigDir().resolve("habibclient-packs.json");
    /** Modrinth project id -> resource pack id ("file/name.zip") of packs installed through this client. */
    private static final Map<String, String> INSTALLED = new ConcurrentHashMap<>();

    static {
        try {
            if (Files.exists(INDEX)) {
                Map<String, String> m = new Gson().fromJson(Files.readString(INDEX),
                        new TypeToken<Map<String, String>>() {}.getType());
                if (m != null) INSTALLED.putAll(m);
            }
        } catch (Exception e) {
            System.err.println("[Habib Client] Failed to read pack index: " + e);
        }
    }

    private ModrinthPacks() {}

    // ------------------------------------------------------------------ public api

    public static String mcVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("1.21.11");
    }

    /** Pack id of an already-installed Modrinth project, or null. */
    public static String packIdFor(String projectId) {
        return INSTALLED.get(projectId);
    }

    public static CompletableFuture<List<Hit>> search(String query) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String facets = "[[\"project_type:resourcepack\"],[\"versions:" + mcVersion() + "\"]]";
                String url = API + "/search?limit=40&facets=" + enc(facets)
                        + (query.isBlank() ? "&index=downloads" : "&query=" + enc(query.trim()));
                JsonArray hits = getJson(url).getAsJsonObject().getAsJsonArray("hits");
                List<Hit> out = new ArrayList<>();
                for (JsonElement el : hits) {
                    JsonObject o = el.getAsJsonObject();
                    out.add(new Hit(
                            str(o, "project_id"),
                            str(o, "title"),
                            str(o, "author"),
                            str(o, "description").replace('\n', ' '),
                            o.has("downloads") ? o.get("downloads").getAsLong() : 0L));
                }
                return out;
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
            }
        }, POOL);
    }

    /** Downloads the newest version for this Minecraft version. Completes with the pack id ("file/name.zip"). */
    public static CompletableFuture<String> install(Hit hit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return installBlocking(hit);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
            }
        }, POOL);
    }

    // ------------------------------------------------------------------ internals

    private static String installBlocking(Hit hit) throws Exception {
        String mc = mcVersion();
        JsonArray versions = getJson(API + "/project/" + hit.id() + "/version?game_versions="
                + enc("[\"" + mc + "\"]")).getAsJsonArray();
        if (versions.isEmpty()) throw new IOException("No download for Minecraft " + mc);

        JsonArray files = versions.get(0).getAsJsonObject().getAsJsonArray("files");
        if (files == null || files.isEmpty()) throw new IOException("Version has no files");
        JsonObject file = files.get(0).getAsJsonObject();
        for (JsonElement f : files) {
            JsonObject o = f.getAsJsonObject();
            if (o.has("primary") && o.get("primary").getAsBoolean()) file = o;
        }

        String url = str(file, "url");
        String name = sanitize(str(file, "filename"));
        if (!name.toLowerCase().endsWith(".zip")) throw new IOException("Pack is not a .zip file");
        String host = URI.create(url).getHost();
        if (host == null || !(host.equals("modrinth.com") || host.endsWith(".modrinth.com"))) {
            throw new IOException("Refusing download from " + host);
        }
        long size = file.has("size") ? file.get("size").getAsLong() : 0L;
        if (size > MAX_BYTES) throw new IOException("Pack is too large (" + (size >> 20) + " MB)");

        Path dir = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
        Files.createDirectories(dir);
        Path target = dir.resolve(name);
        String packId = "file/" + name;

        if (!Files.exists(target)) {
            String sha1 = null;
            if (file.has("hashes") && file.getAsJsonObject("hashes").has("sha1")) {
                sha1 = file.getAsJsonObject("hashes").get("sha1").getAsString();
            }
            Path tmp = Files.createTempFile("habib-pack-", ".part");
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", userAgent())
                        .timeout(Duration.ofMinutes(5))
                        .GET().build();
                HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (res.statusCode() != 200) throw new IOException("Download failed (HTTP " + res.statusCode() + ")");

                MessageDigest md = MessageDigest.getInstance("SHA-1");
                long total = 0;
                try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(tmp)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        total += n;
                        if (total > MAX_BYTES) throw new IOException("Pack is too large");
                        md.update(buf, 0, n);
                        out.write(buf, 0, n);
                    }
                }
                if (sha1 != null && !HexFormat.of().formatHex(md.digest()).equalsIgnoreCase(sha1)) {
                    throw new IOException("Download corrupted (hash mismatch)");
                }
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(tmp);
            }
        }

        INSTALLED.put(hit.id(), packId);
        saveIndex();
        return packId;
    }

    private static synchronized void saveIndex() {
        try {
            Files.createDirectories(INDEX.getParent());
            Files.writeString(INDEX, new Gson().toJson(new HashMap<>(INSTALLED)));
        } catch (IOException e) {
            System.err.println("[Habib Client] Failed to save pack index: " + e);
        }
    }

    private static JsonElement getJson(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", userAgent())
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IOException("Modrinth returned HTTP " + res.statusCode());
        return JsonParser.parseString(res.body());
    }

    private static String userAgent() {
        String v = FabricLoader.getInstance().getModContainer("habibclient")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
        return "habib/habib-client/" + v + " (Fabric client mod)";
    }

    private static String sanitize(String name) {
        String s = name.replaceAll("[^A-Za-z0-9._ ()+-]", "_");
        while (s.startsWith(".")) s = s.substring(1);
        return s.isEmpty() ? "pack.zip" : s;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
