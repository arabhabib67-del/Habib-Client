package dev.habib.client.gui.render;

import dev.habib.client.config.HabibConfig;
import dev.habib.client.gui.theme.ThemeManager;
import dev.habib.client.gui.theme.ThemePalette;
import net.minecraft.client.gui.DrawContext;

/** GUI-only glass primitives. No shaders, framebuffer allocations, or persistent GPU state. */
public final class GlassRenderer {
    private GlassRenderer() {}

    /** Scanline rounded rectangle with analytically covered, one-pixel antialiased edges. */
    public static void rounded(DrawContext ctx, int x, int y, int w, int h, int radius, int color) {
        // Small switches and knobs use half-unit geometry for a finer silhouette at every GUI scale.
        if (radius > 0 && w <= 48 && h <= 28 && !HabibConfig.appearance.reducedTransparency) {
            ctx.getMatrices().pushMatrix();
            try {
                ctx.getMatrices().translate((float) x, (float) y);
                ctx.getMatrices().scale(0.5f, 0.5f);
                roundedPixels(ctx, 0, 0, w * 2, h * 2, radius * 2, color);
            } finally { ctx.getMatrices().popMatrix(); }
        } else roundedPixels(ctx, x, y, w, h, radius, color);
    }

    private static void roundedPixels(DrawContext ctx, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) { ctx.fill(x, y, x + w, y + h, color); return; }
        ctx.fill(x, y + r, x + w, y + h - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            double edge = r - Math.sqrt(r * r - dy * dy);
            int inset = (int) Math.ceil(edge);
            int coverage = ThemePalette.alpha(color, (float) (inset - edge));
            line(ctx, x, y + row, w, inset, color, coverage);
            line(ctx, x, y + h - 1 - row, w, inset, color, coverage);
        }
    }

    /** Animated controls retain fractional coordinates until Minecraft applies GUI scaling. */
    public static void rounded(DrawContext ctx, float x, float y, int w, int h, int radius, int color) {
        ctx.getMatrices().pushMatrix();
        try {
            ctx.getMatrices().translate(x, y);
            rounded(ctx, 0, 0, w, h, radius, color);
        } finally { ctx.getMatrices().popMatrix(); }
    }

    private static void line(DrawContext ctx, int x, int y, int width, int inset, int color, int coverage) {
        ctx.fill(x + inset, y, x + width - inset, y + 1, color);
        if (inset > 0 && (coverage >>> 24) > 0) {
            ctx.fill(x + inset - 1, y, x + inset, y + 1, coverage);
            ctx.fill(x + width - inset, y, x + width - inset + 1, y + 1, coverage);
        }
    }

    public static void surface(DrawContext ctx, int x, int y, int w, int h, int r, int edge, int fill) {
        rounded(ctx, x, y, w, h, r, fill);
        outline(ctx, x, y, w, h, r, edge);
    }

    /** Draw only the rim: translucent fills must never sit over a solid border-coloured rectangle. */
    private static void outline(DrawContext ctx, int x, int y, int w, int h, int radius, int color) {
        if (w <= 1 || h <= 1 || (color >>> 24) == 0) return;
        int r = Math.max(1, Math.min(radius, Math.min(w, h) / 2));
        ctx.fill(x, y + r, x + 1, y + h - r, color);
        ctx.fill(x + w - 1, y + r, x + w, y + h - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            double offset = r - Math.sqrt(r * r - dy * dy);
            int inset = (int) Math.floor(offset);
            if (row == 0) {
                ctx.fill(x + inset + 1, y, x + w - inset - 1, y + 1, color);
                ctx.fill(x + inset + 1, y + h - 1, x + w - inset - 1, y + h, color);
            } else {
                int outer = ThemePalette.alpha(color, (float) (1 - (offset - inset)));
                int inner = ThemePalette.alpha(color, (float) (offset - inset));
                for (int side = 0; side < 2; side++) {
                    int atY = side == 0 ? y + row : y + h - 1 - row;
                    ctx.fill(x + inset, atY, x + inset + 1, atY + 1, outer);
                    ctx.fill(x + inset + 1, atY, x + inset + 2, atY + 1, inner);
                    ctx.fill(x + w - inset - 1, atY, x + w - inset, atY + 1, outer);
                    ctx.fill(x + w - inset - 2, atY, x + w - inset - 1, atY + 1, inner);
                }
            }
        }
    }

    public static void panel(DrawContext ctx, int x, int y, int w, int h, float opacity) {
        ThemePalette p = ThemeManager.palette();
        if (!HabibConfig.appearance.reducedTransparency) {
            for (int spread = 8; spread >= 2; spread -= 2) {
                rounded(ctx, x - spread, y + 3 - spread / 2, w + spread * 2, h + spread,
                        20 + spread, ThemePalette.alpha(0x06000000, opacity));
            }
        }
        surface(ctx, x, y, w, h, 20, ThemePalette.alpha(p.edge, opacity), ThemePalette.alpha(p.panel, opacity));
        // Gentle vertical sheen, explicitly separate from the real vanilla background blur.
        if (!HabibConfig.appearance.reducedTransparency) {
            for (int band = 0; band < 8; band++) {
                int top = y + 3 + band * 4;
                ctx.fill(x + 16, top, x + w - 16, top + 4, ThemePalette.alpha(0x07FFFFFF, opacity * (1f - band / 8f)));
            }
        }
    }

    public static void toggle(DrawContext ctx, int x, int y, float progress, float opacity) {
        ThemePalette p = ThemeManager.palette();
        rounded(ctx, x, y, 34, 18, 9, ThemePalette.alpha(ThemePalette.mix(p.track, p.accent, progress), opacity));
        float knob = x + 2f + Math.max(0f, Math.min(1f, progress)) * 16f;
        rounded(ctx, knob, y + 3, 14, 14, 7, ThemePalette.alpha(0x20000000, opacity));
        rounded(ctx, knob, y + 2, 14, 14, 7, ThemePalette.alpha(0xFFF9FBFF, opacity));
    }

    public static void slider(DrawContext ctx, int x, int y, int width, float value, float opacity) {
        ThemePalette p = ThemeManager.palette();
        float end = x + Math.max(0f, Math.min(1f, value)) * width;
        rounded(ctx, x, y + 5, width, 4, 2, ThemePalette.alpha(p.track, opacity * 0.65f));
        rounded(ctx, x, y + 5, Math.max(2, Math.round(end - x)), 4, 2, ThemePalette.alpha(p.accent, opacity));
        rounded(ctx, end - 5, y + 2, 10, 10, 5, ThemePalette.alpha(0xFFF7FAFF, opacity));
    }

    /** Compact cartoon bomb mark, drawn geometrically so it works with every font. */
    public static void bomb(DrawContext ctx, int x, int y, int color, int accent) {
        rounded(ctx, x + 1, y + 6, 14, 14, 7, color);
        rounded(ctx, x + 10, y + 4, 5, 5, 2, color);
        rounded(ctx, x + 13, y + 1, 2, 5, 1, color);
        rounded(ctx, x + 14, y + 1, 4, 2, 1, color);
        rounded(ctx, x + 18, y, 2, 2, 1, accent);
        rounded(ctx, x + 18, y + 4, 2, 2, 1, accent);
        rounded(ctx, x + 4, y + 9, 3, 4, 2, ThemePalette.alpha(ThemeManager.palette().panel, 0.65f));
    }

    /** Small geometric icons avoid font-dependent Unicode glyphs. */
    public static void icon(DrawContext ctx, int kind, int x, int y, int color) {
        if (kind == 1) { // crossed blades
            for (int i = 0; i < 9; i++) {
                ctx.fill(x + i, y + i, x + i + 2, y + i + 2, color);
                ctx.fill(x + 8 - i, y + i, x + 10 - i, y + i + 2, color);
            }
        } else if (kind == 11) { // appearance / overlapping circles
            rounded(ctx, x, y, 8, 8, 4, color);
            rounded(ctx, x + 4, y + 4, 7, 7, 4, ThemePalette.alpha(color, 0.55f));
        } else {
            surface(ctx, x, y, 10, 10, kind % 3 + 2, color, ThemePalette.alpha(ThemeManager.palette().panel, 1));
            ctx.fill(x + 3, y + 3, x + 7, y + 4, color);
            ctx.fill(x + 3, y + 6, x + 7, y + 7, color);
        }
    }
}

// Copyright (c) 2026 Bloko. UI contributions licensed under the MIT License.
