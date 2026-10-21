package dev.habib.client.gui.theme;

import dev.habib.client.config.HabibConfig;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ThemeManager {
    private static volatile SystemThemeDetector.Result system = new SystemThemeDetector.Result(true, "Detecting system theme...");
    private static final ThemePalette PALETTE = new ThemePalette();
    private static float blend = 1f;
    static {
        PALETTE.update(blend, HabibConfig.appearance);
        var worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "habib-system-theme");
            thread.setDaemon(true);
            return thread;
        });
        worker.scheduleWithFixedDelay(() -> {
            if (HabibConfig.appearance.themeMode == ThemeMode.AUTO) system = SystemThemeDetector.detect();
        }, 0, 5, TimeUnit.SECONDS);
    }
    private ThemeManager() {}

    public static ThemePalette update(float dt) {
        AppearanceSettings settings = HabibConfig.appearance;
        boolean dark = settings.themeMode == ThemeMode.DARK || (settings.themeMode == ThemeMode.AUTO && system.dark());
        blend = animate(blend, dark ? 1f : 0f, dt, 12f);
        PALETTE.update(blend, settings);
        return PALETTE;
    }

    public static ThemePalette palette() { return PALETTE; }
    public static String systemDescription() { return system.source(); }
    public static float animate(float current, float target, float dt, float speed) {
        if (!HabibConfig.appearance.animations) return target;
        return current + (target - current) * (1f - (float) Math.exp(-Math.max(0f, Math.min(dt, 0.1f)) * speed));
    }
}

// Copyright (c) 2026 Bloko. UI contributions licensed under the MIT License.
