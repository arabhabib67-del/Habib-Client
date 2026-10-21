package dev.habib.client.gui.theme;

/** One reused palette, interpolated in place; colours are never allocated per widget. */
public final class ThemePalette {
    public int panel, sidebar, row, hover, text, muted, edge, track, accent, selected, accentText;

    void update(float dark, AppearanceSettings settings) {
        int opacity = Math.round((settings.reducedTransparency ? 0.98f : 0.50f + settings.opacity * 0.46f) * 255);
        panel = (opacity << 24) | (mix(0xFFD1D0CD, 0xFF262626, dark) & 0xFFFFFF);
        sidebar = mix(0x48918F8A, 0x65151515, dark);
        row = mix(0x26FFFFFF, 0x263E3E3E, dark);
        hover = mix(0x4FFFFFFF, 0x59646464, dark);
        text = mix(0xFF262626, 0xFFF0F0F0, dark);
        muted = mix(0xFF5B5B5B, 0xFFADADAD, dark);
        edge = mix(0x5AFFFFFF, 0x2CFFFFFF, dark);
        track = mix(0xFFA5A5A5, 0xFF555555, dark);
        accent = settings.accent;
        selected = mix(0x38959491, 0x665B5B5B, dark);
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
