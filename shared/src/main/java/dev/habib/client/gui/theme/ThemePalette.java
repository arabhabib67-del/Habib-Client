package dev.habib.client.gui.theme;

/** One reused palette, interpolated in place; colours are never allocated per widget. */
public final class ThemePalette {
    public int panel, sidebar, row, hover, text, muted, edge, track, accent, selected, accentText;

    void update(float dark, AppearanceSettings settings) {
        int opacity = Math.round((settings.reducedTransparency ? 0.98f : 0.50f + settings.opacity * 0.46f) * 255);
        panel = (opacity << 24) | (mix(0xFFD9DCE2, 0xFF20242C, dark) & 0xFFFFFF);
        sidebar = mix(0x65ACB1BA, 0x7013171D, dark);
        row = mix(0x64FFFFFF, 0x382D333E, dark);
        hover = mix(0xA0FFFFFF, 0x80535B69, dark);
        text = mix(0xFF222630, 0xFFF2F4F8, dark);
        muted = mix(0xFF575E69, 0xFFACB4C2, dark);
        edge = mix(0x75FFFFFF, 0x45DDE5F2, dark);
        track = mix(0xFFAFB3BB, 0xFF505764, dark);
        accent = settings.accent;
        selected = mix(0xB8FFFFFF, 0x804D5665, dark);
        int luminance = ((accent >> 16 & 255) * 299 + (accent >> 8 & 255) * 587 + (accent & 255) * 114) / 1000;
        accentText = luminance > 170 ? 0xFF202630 : 0xFFFFFFFF;
    }

    public static int alpha(int color, float opacity) {
        return (Math.round((color >>> 24) * Math.max(0f, Math.min(1f, opacity))) << 24) | (color & 0xFFFFFF);
    }

    public static int mix(int from, int to, float progress) {
        float t = Math.max(0f, Math.min(1f, progress));
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int value = from >>> shift & 255;
            result |= Math.round(value + ((to >>> shift & 255) - value) * t) << shift;
        }
        return result;
    }
}

// Copyright (c) 2026 Bloko. UI contributions licensed under the MIT License.
