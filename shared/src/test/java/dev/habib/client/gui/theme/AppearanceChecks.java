package dev.habib.client.gui.theme;

import com.google.gson.Gson;

/** Dependency-free checks run by Gradle check on both supported Java targets. */
public final class AppearanceChecks {
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
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
        require(palette.text == 0xFF262626 && palette.accent == settings.accent, "Light palette");
        require((palette.panel >>> 24) < 255, "World-visible translucent surface");
        palette.update(1f, settings);
        require(palette.text == 0xFFF0F0F0 && palette.muted != palette.text, "Dark palette hierarchy");
        settings.reducedTransparency = true; palette.update(1f, settings);
        require((palette.panel >>> 24) >= 249, "Reduced transparency fallback");
        settings.accent = 0xFFF4F5F7; palette.update(0f, settings);
        require(palette.accentText == 0xFF202630, "White accent remains readable");
        require(ThemePalette.mix(0xFF000000, 0xFFFFFFFF, 0.5f) == 0xFF808080, "Colour interpolation");
        require(ThemePalette.alpha(0x80FFFFFF, 0.5f) == 0x40FFFFFF, "Alpha composition");
        float reference = 1f - (float) Math.exp(-4f);
        for (int fps : new int[] {30, 60, 144}) {
            float progress = 0;
            for (int frame = 0; frame < fps / 2; frame++) {
                float next = AnimationUtils.follow(progress, 1f, 1f / fps, 8f);
                require(next >= progress && next <= 1f, "Monotonic motion at " + fps + " FPS");
                progress = next;
            }
            require(Math.abs(progress - reference) < 0.00001f, "Frame-rate independent motion at " + fps + " FPS");
        }
        require(AnimationUtils.follow(0.4f, 1f, Float.NaN, 8f) == 0.4f, "Invalid frame time");
        try (var font = AppearanceChecks.class.getResourceAsStream("/assets/habibclient/font/habib-ui-regular.ttf")) {
            require(font != null && java.util.Arrays.equals(font.readNBytes(4), new byte[] {0, 1, 0, 0}), "Bundled TrueType font");
        }
        require(AppearanceChecks.class.getResource("/assets/habibclient/font/ofl-inter.txt") != null, "Font redistribution license");
        float peak = 0f;
        for (int ms = 0; ms <= 800; ms++) {
            float bounce = AnimationUtils.openingSpring(ms / 1000f);
            require(Float.isFinite(bounce) && bounce >= 0f && bounce <= 1.12f, "Opening spring bounds");
            peak = Math.max(peak, bounce);
        }
        require(peak > 1.05f, "Opening has a visible, restrained overshoot");
        require(AnimationUtils.openingSpring(0.8f) == 1f, "Opening settles exactly");
        for (int fps : new int[] {30, 60, 144}) {
            float elapsed = 0;
            for (int frame = 0; frame < fps / 2; frame++) elapsed += 1f / fps;
            require(Math.abs(AnimationUtils.openingSpring(elapsed) - AnimationUtils.openingSpring(0.5f)) < 0.00001f,
                    "Opening timing at " + fps + " FPS");
        }
        for (String mask : new String[] {"rounded-fill", "rounded-rim"}) {
            try (var stream = AppearanceChecks.class.getResourceAsStream("/assets/habibclient/textures/gui/" + mask + ".png")) {
                require(stream != null && java.util.Arrays.equals(stream.readNBytes(8), new byte[] {(byte)137,80,78,71,13,10,26,10}), "Bundled smooth shape mask");
            }
        }
        SystemThemeDetector.Result system = SystemThemeDetector.detect();
        require(system.source() != null && !system.source().isBlank(), "OS detection or documented fallback");
        System.out.println("Appearance checks passed; system: " + system.source() + ", dark=" + system.dark());
    }
}

// Copyright (c) 2026 Bloko. UI contributions licensed under the MIT License.
