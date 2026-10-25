package dev.habib.client.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

/** Fetches and briefly caches public MCTiers profiles for player name tags. */
public final class TierTagger {
    private static final long FOUND_CACHE_MS = Duration.ofMinutes(10).toMillis();
    private static final long NOT_FOUND_CACHE_MS = Duration.ofMinutes(5).toMillis();
    private static final long ERROR_RETRY_MS = Duration.ofSeconds(45).toMillis();

    private static final Map<UUID, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> PENDING = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(
            2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64), task -> {
                Thread thread = new Thread(task, "Habib Tier Lookup");
                thread.setDaemon(true);
                return thread;
            });

    private TierTagger() {}

    /** Returns a cached best tier and starts a background lookup when needed. */
    public static String tier(UUID uuid, String username) {
        if (uuid == null || username == null || username.isBlank()) return null;
        long now = System.currentTimeMillis();
        CacheEntry entry = CACHE.get(uuid);
        if (entry == null || entry.expiresAt <= now) request(uuid, username, entry, now);
        return entry == null ? null : entry.tier;
    }

    /** Readable, warm colors that keep the badge distinct from the player's name. */
    public static int color(String tier) {
        if (tier == null || tier.length() < 3) return 0xFFFFFFFF;
        return switch (tier.charAt(2)) {
            case '1' -> 0xFFFF6B6B;
            case '2' -> 0xFFFFA94D;
            case '3' -> 0xFFFFD43B;
            case '4' -> 0xFF74C0FC;
            default -> 0xFFB197FC;
        };
    }

    private static void request(UUID uuid, String username, CacheEntry previous, long now) {
        if (PENDING.putIfAbsent(uuid, Boolean.TRUE) != null) return;
        try {
            WORKERS.execute(() -> {
                try {
                    String result = lookup(uuid, username);
                    long ttl = result == null ? NOT_FOUND_CACHE_MS : FOUND_CACHE_MS;
                    CACHE.put(uuid, new CacheEntry(result, System.currentTimeMillis() + ttl));
                    trimCache();
                } catch (Exception ignored) {
                    String stale = previous == null ? null : previous.tier;
                    CACHE.put(uuid, new CacheEntry(stale, System.currentTimeMillis() + ERROR_RETRY_MS));
                } finally {
                    PENDING.remove(uuid);
                }
            });
        } catch (RejectedExecutionException ignored) {
            PENDING.remove(uuid);
            CACHE.put(uuid, new CacheEntry(previous == null ? null : previous.tier, now + ERROR_RETRY_MS));
        }
    }

    private static String lookup(UUID uuid, String username) throws IOException, InterruptedException {
        String encodedName = URLEncoder.encode(username, StandardCharsets.UTF_8);
        String[] endpoints = {
                "https://mctiers.com/api/v2/profile/by-name/" + encodedName,
                "https://mctiers.com/api/v2/profile/" + uuid
        };
        for (String endpoint : endpoints) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(6))
                    .header("Accept", "application/json")
                    .header("User-Agent", "HabibClient-TierTagger")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) continue;
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Tier service returned HTTP " + response.statusCode());
            }
            JsonElement json = JsonParser.parseString(response.body());
            return findBestTier(json);
        }
        return null;
    }

    private static String findBestTier(JsonElement element) {
        BestTier best = new BestTier();
        scan(element, best);
        return best.value;
    }

    private static void scan(JsonElement element, BestTier best) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) scan(child, best);
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> field : object.entrySet()) {
                if (field.getKey().equalsIgnoreCase("tier") && field.getValue().isJsonPrimitive()
                        && field.getValue().getAsJsonPrimitive().isString()) {
                    consider(field.getValue().getAsString(), best);
                }
                scan(field.getValue(), best);
            }
        }
    }

    private static void consider(String raw, BestTier best) {
        String tier = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (!tier.matches("(?:HT|LT)[1-5]")) return;
        int score = (tier.charAt(2) - '1') * 2 + (tier.charAt(0) == 'L' ? 1 : 0);
        if (score < best.score) {
            best.score = score;
            best.value = tier;
        }
    }

    private static void trimCache() {
        if (CACHE.size() <= 512) return;
        long now = System.currentTimeMillis();
        CACHE.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        if (CACHE.size() > 512) {
            int remove = CACHE.size() - 512;
            for (UUID uuid : CACHE.keySet()) {
                CACHE.remove(uuid);
                if (--remove <= 0) break;
            }
        }
    }

    private record CacheEntry(String tier, long expiresAt) {}

    private static final class BestTier {
        private int score = Integer.MAX_VALUE;
        private String value;
    }
}
