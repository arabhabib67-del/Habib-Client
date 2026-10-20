package dev.habib.client.beta;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** Checks per-account beta passwords against the configured hosted verifier. */
public final class BetaAccess {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final String SERVICE_URL = loadServiceUrl();
    private static final URI VERIFY_URI = makeVerifyUri(SERVICE_URL);

    private static volatile String authorizedUuid = "";

    private BetaAccess() {}

    private static String loadServiceUrl() {
        Properties properties = new Properties();
        try (InputStream input = BetaAccess.class.getResourceAsStream("/habib-beta.properties")) {
            if (input == null) return "";
            properties.load(input);
            return properties.getProperty("serviceUrl", "").trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static URI makeVerifyUri(String base) {
        if (base.isBlank()) return null;
        try {
            URI uri = URI.create(base.replaceAll("/+$", ""));
            String scheme = uri.getScheme();
            String host = uri.getHost();
            boolean local = "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host);
            if (host == null || !("https".equalsIgnoreCase(scheme) || local && "http".equalsIgnoreCase(scheme))) {
                return null;
            }
            return URI.create(uri + "/api/verify");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isConfigured() {
        return VERIFY_URI != null;
    }

    public static String configurationMessage() {
        if (SERVICE_URL.isBlank()) return "Beta verifier isn't configured in this build.";
        return VERIFY_URI == null ? "Beta verifier URL is invalid. Contact the beta host." : "";
    }

    public static String currentUserId() {
        UUID uuid = Minecraft.getInstance().getUser().getProfileId();
        return uuid == null ? "" : uuid.toString();
    }

    public static String currentUsername() {
        return Minecraft.getInstance().getUser().getName();
    }

    public static boolean isAuthorized() {
        String userId = currentUserId();
        return isConfigured() && !userId.isBlank() && userId.equals(authorizedUuid);
    }

    public static void authenticate(String password, Consumer<AuthResult> callback) {
        String userId = currentUserId();
        if (!isConfigured()) {
            callback.accept(new AuthResult(false, configurationMessage()));
            return;
        }
        if (userId.isBlank()) {
            callback.accept(new AuthResult(false, "This Minecraft account has no usable ID."));
            return;
        }
        if (password == null || password.isBlank() || password.length() > 128) {
            callback.accept(new AuthResult(false, "Enter the password assigned to this account."));
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("uuid", userId);
        body.addProperty("password", password);
        HttpRequest request = HttpRequest.newBuilder(VERIFY_URI)
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((response, failure) -> Minecraft.getInstance().execute(() -> {
                    if (failure != null) {
                        callback.accept(new AuthResult(false, networkMessage(failure)));
                        return;
                    }
                    try {
                        JsonObject result = GSON.fromJson(response.body(), JsonObject.class);
                        boolean ok = response.statusCode() == 200 && result != null
                                && result.has("ok") && result.get("ok").getAsBoolean();
                        if (ok) authorizedUuid = userId;
                        String message = result != null && result.has("message")
                                ? result.get("message").getAsString()
                                : ok ? "Access approved." : "This account or password isn't approved.";
                        callback.accept(new AuthResult(ok, message));
                    } catch (RuntimeException e) {
                        callback.accept(new AuthResult(false, "The beta verifier returned an invalid response."));
                    }
                }));
    }

    private static String networkMessage(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause() : failure;
        if (cause instanceof java.net.http.HttpTimeoutException) return "The beta verifier took too long. Try again.";
        return "Couldn't reach the beta verifier. Check your connection and try again.";
    }

    public record AuthResult(boolean approved, String message) {}
}
