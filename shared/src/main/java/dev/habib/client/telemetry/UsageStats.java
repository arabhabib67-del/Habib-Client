package dev.habib.client.telemetry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import dev.habib.client.config.HabibConfig;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opt-in aggregate usage counts. No network call includes a player name or Minecraft account ID. */
public final class UsageStats {
    public record Summary(int activeToday, int activeLast7Days, Map<String, Integer> byGameVersion,
                          Map<String, Integer> byModVersion, String updatedAt) {
        private static Summary empty() { return new Summary(0, 0, Map.of(), Map.of(), ""); }
    }

    private record BuildInfo(String endpoint, String modVersion, String minecraftVersion) {}

    private static final Gson GSON = new Gson();
    private static final BuildInfo BUILD = loadBuildInfo();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private static final AtomicBoolean SENDING = new AtomicBoolean();
    private static final AtomicBoolean LOADING = new AtomicBoolean();
    private static volatile Summary summary = Summary.empty();
    private static volatile String status = "Open Stats to view the latest counts.";
    private static volatile LocalDate lastPingDay;

    private UsageStats() {}

    private static BuildInfo loadBuildInfo() {
        Properties properties = new Properties();
        try (InputStream input = UsageStats.class.getResourceAsStream("/habib-analytics.properties")) {
            if (input != null) properties.load(input);
        } catch (Exception ignored) {
        }
        String endpoint = properties.getProperty("endpoint", "").trim();
        try {
            URI uri = URI.create(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) endpoint = "";
        } catch (Exception ignored) {
            endpoint = "";
        }
        while (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
        return new BuildInfo(endpoint, properties.getProperty("modVersion", "unknown"),
                properties.getProperty("minecraftVersion", "unknown"));
    }

    public static boolean configured() { return !BUILD.endpoint().isBlank(); }
    public static boolean sharingEnabled() { return HabibConfig.shareUsageStats; }
    public static Summary summary() { return summary; }
    public static String status() { return status; }

    /** Called after the saved settings have loaded. */
    public static void initialize() {
        if (!configured()) {
            status = "The stats service has not been set up yet.";
        } else if (HabibConfig.shareUsageStats) {
            status = "Usage sharing is on; only opted-in installations are counted.";
            sendPingIfNeeded();
        } else {
            status = "Usage sharing is off. You can opt in below.";
        }
    }

    public static void toggleSharing() {
        if (HabibConfig.shareUsageStats) {
            HabibConfig.shareUsageStats = false;
            HabibConfig.save();
            status = "Usage sharing is off.";
            return;
        }
        if (!configured()) {
            status = "The stats service has not been set up yet.";
            return;
        }
        if (HabibConfig.usageStatsInstallId == null || HabibConfig.usageStatsInstallId.isBlank()) {
            HabibConfig.usageStatsInstallId = UUID.randomUUID().toString();
        }
        HabibConfig.shareUsageStats = true;
        HabibConfig.save();
        status = "Opted in. Sending one anonymous active-use ping today.";
        sendPingIfNeeded();
    }

    /** Fetches aggregate counts only when the user opens Stats or presses Refresh. */
    public static void refresh() {
        if (!configured()) {
            status = "The stats service has not been set up yet.";
            return;
        }
        if (!LOADING.compareAndSet(false, true)) return;
        status = "Loading usage counts...";
        HttpRequest request = HttpRequest.newBuilder(URI.create(BUILD.endpoint() + "/api/stats"))
                .timeout(Duration.ofSeconds(8)).header("Accept", "application/json").GET().build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> {
                    try {
                        if (error != null || response == null || response.statusCode() != 200) {
                            status = "Couldn't load counts. Check the stats service and connection.";
                            return;
                        }
                        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                        Map<String, Integer> gameVersions = counts(json, "gameVersions");
                        Map<String, Integer> modVersions = counts(json, "modVersions");
                        summary = new Summary(value(json, "activeToday"), value(json, "activeLast7Days"),
                                Map.copyOf(gameVersions), Map.copyOf(modVersions),
                                json.has("updatedAt") ? json.get("updatedAt").getAsString() : "");
                        status = "Counts updated. Only opted-in installations are included.";
                    } catch (Exception ignored) {
                        status = "The stats service returned an unreadable response.";
                    } finally {
                        LOADING.set(false);
                    }
                });
    }

    private static int value(JsonObject json, String key) {
        return json.has(key) ? Math.max(0, json.get(key).getAsInt()) : 0;
    }

    private static Map<String, Integer> counts(JsonObject json, String key) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (json.has(key) && json.get(key).isJsonObject()) {
            for (var entry : json.getAsJsonObject(key).entrySet()) {
                counts.put(entry.getKey(), Math.max(0, entry.getValue().getAsInt()));
            }
        }
        return counts;
    }

    private static void sendPingIfNeeded() {
        if (!configured() || !HabibConfig.shareUsageStats || !SENDING.compareAndSet(false, true)) return;
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (today.equals(lastPingDay)) {
            SENDING.set(false);
            return;
        }
        String installId = HabibConfig.usageStatsInstallId;
        if (installId == null || installId.isBlank()) {
            SENDING.set(false);
            return;
        }
        Map<String, String> payload = Map.of("installId", installId, "modVersion", BUILD.modVersion(),
                "minecraftVersion", BUILD.minecraftVersion());
        HttpRequest request = HttpRequest.newBuilder(URI.create(BUILD.endpoint() + "/api/ping"))
                .timeout(Duration.ofSeconds(8)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload))).build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, error) -> {
                    if (error == null && response != null && response.statusCode() >= 200 && response.statusCode() < 300) {
                        lastPingDay = today;
                    }
                    SENDING.set(false);
                });
    }
}
