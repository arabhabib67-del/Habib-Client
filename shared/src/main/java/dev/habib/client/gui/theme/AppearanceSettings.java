package dev.habib.client.gui.theme;

/** Local UI preferences: deliberately excluded from shared gameplay profiles. */
public final class AppearanceSettings {
    public volatile ThemeMode themeMode = ThemeMode.AUTO;
    /** Surface opacity: 0 = clearer glass, 1 = more opaque glass. */
    public float opacity = 0.80f;
    public boolean backgroundBlur = true;
    public int blurStrength = 5;
    public boolean animations = true;
    public boolean reducedTransparency = false;
    public int accent = 0xFF008AFF;

    public void sanitize() {
        if (themeMode == null) themeMode = ThemeMode.AUTO;
        opacity = Float.isFinite(opacity) ? Math.max(0.35f, Math.min(1f, opacity)) : 0.80f;
        blurStrength = Math.max(1, Math.min(10, blurStrength));
        accent |= 0xFF000000;
    }
}

// Copyright (c) 2026 Bloko. UI contributions licensed under the MIT License.
