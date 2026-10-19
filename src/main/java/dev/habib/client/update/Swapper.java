package dev.habib.client.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Replaces the old mod jar with the downloaded one. Uses only the JDK so it can run on its own after the game has
 * closed ({@code java -cp new.jar dev.habib.client.update.Swapper ...}), because a running jar can't be replaced
 * on Windows. The old jar is renamed away first and only deleted once the new one is safely in place, so the mods
 * folder never ends up with two copies of the mod (which would stop the game from starting).
 */
public final class Swapper {
    private Swapper() {}

    /** @return true when the new jar is now in the mods folder and the old one is gone. */
    public static boolean swap(Path oldJar, Path newJar, Path modsDir) throws IOException {
        String name = newJar.getFileName().toString();
        Path target = modsDir.resolve(name);
        Path backup = null;
        boolean hadOld = oldJar != null && Files.exists(oldJar) && !oldJar.equals(target);
        if (hadOld) {
            backup = oldJar.resolveSibling(oldJar.getFileName() + ".old");
            Files.deleteIfExists(backup);
            Files.move(oldJar, backup); // throws while the file is still locked: nothing has changed yet
        }
        Path part = modsDir.resolve(name + ".part");
        try {
            Files.copy(newJar, part, StandardCopyOption.REPLACE_EXISTING);
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(part);
                if (backup != null) Files.move(backup, oldJar, StandardCopyOption.REPLACE_EXISTING); // put the old mod back
            } catch (IOException ignored) {
                // nothing more we can do
            }
            throw e;
        }
        if (backup != null) {
            try {
                Files.deleteIfExists(backup);
            } catch (IOException ignored) {
                // a leftover .jar.old is ignored by the game
            }
        }
        try {
            Files.deleteIfExists(newJar);
        } catch (IOException ignored) {
            // staging copy; harmless
        }
        return true;
    }

    /** args: pid oldJar newJar modsDir logFile */
    public static void main(String[] a) {
        if (a.length < 5) return;
        Path log = Path.of(a[4]);
        try {
            long pid = Long.parseLong(a[0]);
            ProcessHandle.of(pid).ifPresent(h -> {
                try {
                    h.onExit().get(120, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    // carry on and just retry below
                }
            });
            Thread.sleep(1200L); // let the OS release the jar
            Path oldJar = Path.of(a[1]);
            Path newJar = Path.of(a[2]);
            Path mods = Path.of(a[3]);
            IOException last = null;
            for (int i = 0; i < 40; i++) {
                if (!Files.exists(newJar)) {
                    note(log, "Nothing to install (already done).");
                    return;
                }
                try {
                    swap(oldJar, newJar, mods);
                    note(log, "Installed " + newJar.getFileName() + ".");
                    return;
                } catch (IOException e) {
                    last = e;
                    Thread.sleep(750L);
                }
            }
            note(log, "Could not replace the old jar: " + last + ". Copy " + newJar + " into " + mods
                    + " and delete " + oldJar.getFileName() + " yourself.");
        } catch (Throwable t) {
            note(log, "Updater failed: " + t);
        }
    }

    private static void note(Path log, String line) {
        try {
            Files.createDirectories(log.getParent());
            Files.writeString(log, Instant.now() + "  " + line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // logging is best effort
        }
    }
}
