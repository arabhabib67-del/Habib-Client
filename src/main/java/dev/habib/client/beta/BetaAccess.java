package dev.habib.client.beta;

import net.minecraft.client.MinecraftClient;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Verifies the shared beta password locally and unlocks modules for this game session. */
public final class BetaAccess {
    private static final Properties PASSWORD_DATA = loadPasswordData();
    private static final byte[] PASSWORD_SALT = decode("passwordSalt");
    private static final byte[] PASSWORD_HASH = decode("passwordHash");
    private static final int PASSWORD_ITERATIONS = parseIterations();
    private static volatile boolean authorized;

    private BetaAccess() {}

    private static Properties loadPasswordData() {
        Properties properties = new Properties();
        try (InputStream input = BetaAccess.class.getResourceAsStream("/habib-beta.properties")) {
            if (input != null) properties.load(input);
        } catch (IOException ignored) {
            // An absent configuration is reported by configurationMessage().
        }
        return properties;
    }

    private static byte[] decode(String key) {
        try {
            String value = PASSWORD_DATA.getProperty(key, "").trim();
            return value.isEmpty() ? new byte[0] : Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    private static int parseIterations() {
        try {
            return Integer.parseInt(PASSWORD_DATA.getProperty("passwordIterations", "0").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static boolean isConfigured() {
        return PASSWORD_SALT.length >= 16 && PASSWORD_HASH.length == 32
                && PASSWORD_ITERATIONS >= 100_000 && PASSWORD_ITERATIONS <= 2_000_000;
    }

    public static String configurationMessage() {
        return "Shared password isn't configured. Ask the host to rebuild the jar.";
    }

    public static boolean isAuthorized() {
        return authorized;
    }

    public static void authenticate(String password, Consumer<AuthResult> callback) {
        if (!isConfigured()) {
            callback.accept(new AuthResult(false, configurationMessage()));
            return;
        }
        if (password == null || password.isBlank() || password.length() > 128) {
            callback.accept(new AuthResult(false, "Enter the shared beta password."));
            return;
        }

        char[] characters = password.toCharArray();
        CompletableFuture.supplyAsync(() -> verify(characters))
                .whenComplete((approved, failure) -> MinecraftClient.getInstance().execute(() -> {
                    if (failure != null) {
                        callback.accept(new AuthResult(false, "Couldn't check the password. Please try again."));
                    } else if (approved) {
                        authorized = true;
                        callback.accept(new AuthResult(true, "Modules unlocked for this game session."));
                    } else {
                        callback.accept(new AuthResult(false, "That password didn't match. Try again."));
                    }
                }));
    }

    private static boolean verify(char[] password) {
        PBEKeySpec spec = new PBEKeySpec(password, PASSWORD_SALT, PASSWORD_ITERATIONS, 256);
        try {
            byte[] candidate = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            try {
                return MessageDigest.isEqual(PASSWORD_HASH, candidate);
            } finally {
                Arrays.fill(candidate, (byte) 0);
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        } finally {
            spec.clearPassword();
            Arrays.fill(password, '\0');
        }
    }

    public record AuthResult(boolean approved, String message) {}
}
