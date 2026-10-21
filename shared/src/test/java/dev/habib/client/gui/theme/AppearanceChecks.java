package dev.habib.client.gui.theme;

import com.google.gson.Gson;

/** Dependency-free checks run by Gradle check on both supported Java targets. */
public final class AppearanceChecks {
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        Gson gson = new Gson();
        AppearanceSettings legacy = gson.fromJson("{}", AppearanceSettings.class);
        require(legacy.themeMode == ThemeMode.AUTO && legacy.backgroundBlur && legacy.animations, "Legacy config defaults");
        AppearanceSettings settings = gson.fromJson("{\"themeMode\":null,\"opacity\":-4,\"blurStrength\":99,\"accent\":0}", AppearanceSettings.class);
        settings.sanitize();
        require(settings.themeMode == ThemeMode.AUTO && settings.opacity == 0.35f && settings.blurStrength == 10 && settings.accent == 0xFF000000, "Invalid setting bounds");
        settings.opacity = Float.NaN; settings.sanitize();
        require(settings.opacity == 0.8f, "Non-finite opacity fallback");
        settings.themeMode = ThemeMode.LIGHT; settings.accent = 0xFF9258E8;
        AppearanceSettings restored = gson.fromJson(gson.toJson(settings), AppearanceSettings.class);
        require(restored.themeMode == ThemeMode.LIGHT && restored.accent == settings.accent, "Appearance persistence round-trip");
        ThemePalette palette = new ThemePalette();
        palette.update(0f, settings);
        require(palette.text == 0xFF222630 && palette.accent == settings.accent, "Light palette");
        require((palette.panel >>> 24) < 255, "World-visible translucent surface");
        palette.update(1f, settings);
        require(palette.text == 0xFFF2F4F8 && palette.muted != palette.text, "Dark palette hierarchy");
        settings.reducedTransparency = true; palette.update(1f, settings);
        require((palette.panel >>> 24) >= 249, "Reduced transparency fallback");
        settings.accent = 0xFFF4F5F7; palette.update(0f, settings);
        require(palette.accentText == 0xFF202630, "White accent remains readable");
        require(ThemePalette.mix(0xFF000000, 0xFFFFFFFF, 0.5f) == 0xFF808080, "Colour interpolation");
        require(ThemePalette.alpha(0x80FFFFFF, 0.5f) == 0x40FFFFFF, "Alpha composition");
        SystemThemeDetector.Result system = SystemThemeDetector.detect();
        require(system.source() != null && !system.source().isBlank(), "OS detection or documented fallback");
        System.out.println("Appearance checks passed; system: " + system.source() + ", dark=" + system.dark());
    }
}

// Copyright (c) 2026 Bloko. UI contributions licensed under the MIT License.
