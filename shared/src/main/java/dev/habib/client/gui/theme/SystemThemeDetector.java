package dev.habib.client.gui.theme;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Called only by ThemeManager's daemon worker; never reads OS settings on the render thread. */
public final class SystemThemeDetector {
    public record Result(boolean dark, String source) {}
    private SystemThemeDetector() {}

    public static Result detect() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                String systemRoot = System.getenv("SystemRoot");
                if (systemRoot == null) return fallback();
                String output = run(systemRoot + "\\System32\\reg.exe", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                        "/v", "AppsUseLightTheme");
                if (output.matches("(?s).*AppsUseLightTheme\\s+REG_DWORD\\s+0x[01]\\s*")) {
                    return new Result(output.stripTrailing().endsWith("0x0"), "Windows application theme");
                }
            } else if (os.contains("mac")) {
                String output = run("/usr/bin/defaults", "read", "-g", "AppleInterfaceStyle");
                if (output.contains("Dark")) return new Result(true, "macOS appearance");
                if (!output.isBlank()) return new Result(false, "macOS appearance");
                // A missing AppleInterfaceStyle is the documented light appearance convention.
                String all = run("/usr/bin/defaults", "read", "-g");
                if (!all.isBlank()) return new Result(false, "macOS appearance");
            } else {
                String override = System.getenv("GTK_THEME");
                if (override != null && !override.isBlank()) return new Result(override.toLowerCase(Locale.ROOT).contains("dark"), "GTK theme override");
                String scheme = run("gsettings", "get", "org.gnome.desktop.interface", "color-scheme");
                if (scheme.contains("prefer-dark")) return new Result(true, "Desktop colour scheme");
                if (scheme.contains("prefer-light")) return new Result(false, "Desktop colour scheme");
                String theme = System.getenv("GTK_THEME");
                if (theme == null || theme.isBlank()) theme = run("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme");
                if (!theme.isBlank()) return new Result(theme.toLowerCase(Locale.ROOT).contains("dark"), "GTK theme");
            }
        } catch (IOException | SecurityException ignored) {
            // Headless systems and desktops without a supported settings service use dark glass.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return fallback();
    }

    private static Result fallback() { return new Result(true, "Dark fallback (system theme unavailable)"); }

    private static String run(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if (!process.waitFor(1200, TimeUnit.MILLISECONDS) || process.exitValue() != 0) return "";
            try (var stream = process.getInputStream()) {
                return new String(stream.readNBytes(8192), StandardCharsets.UTF_8).trim();
            }
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            process.getInputStream().close();
            process.getErrorStream().close();
            process.getOutputStream().close();
        }
    }
}

// Copyright (c) 2026 Bloko. UI contributions licensed under the MIT License.
