package dev.habib.client.gui.render;

import dev.habib.client.config.HabibConfig;
import dev.habib.client.gui.theme.ThemeManager;
import dev.habib.client.gui.theme.ThemePalette;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// Shared menu shapes. The masks keep corners smooth when the GUI is scaled.
public final class GlassRenderer {
    private GlassRenderer() {}

    private static final net.minecraft.resources.Identifier FILL = net.minecraft.resources.Identifier.fromNamespaceAndPath("habibclient", "textures/gui/rounded-fill.png");
    private static final net.minecraft.resources.Identifier RIM = net.minecraft.resources.Identifier.fromNamespaceAndPath("habibclient", "textures/gui/rounded-rim.png");

    // Stretch the middle, keep the corners round.
    public static void rounded(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int radius, int color) {
        shape(ctx, FILL, x, y, w, h, radius, color);
    }

    public static void rounded(GuiGraphicsExtractor ctx, float x, float y, int w, int h, int radius, int color) {
        ctx.pose().pushMatrix();
        try {
            ctx.pose().translate(x, y);
            rounded(ctx, 0, 0, w, h, radius, color);
        } finally { ctx.pose().popMatrix(); }
    }

    private static void shape(GuiGraphicsExtractor ctx, net.minecraft.resources.Identifier texture, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) { ctx.fill(x, y, x + w, y + h, color); return; }
        patch(ctx, texture, x, y, r, r, 0, 0, 256, 256, color);
        patch(ctx, texture, x + w - r, y, r, r, 258, 0, 256, 256, color);
        patch(ctx, texture, x, y + h - r, r, r, 0, 258, 256, 256, color);
        patch(ctx, texture, x + w - r, y + h - r, r, r, 258, 258, 256, 256, color);
        patch(ctx, texture, x + r, y, w - 2 * r, r, 256, 0, 2, 256, color);
        patch(ctx, texture, x + r, y + h - r, w - 2 * r, r, 256, 258, 2, 256, color);
        patch(ctx, texture, x, y + r, r, h - 2 * r, 0, 256, 256, 2, color);
        patch(ctx, texture, x + w - r, y + r, r, h - 2 * r, 258, 256, 256, 2, color);
        if (texture == FILL) patch(ctx, texture, x + r, y + r, w - 2 * r, h - 2 * r, 256, 256, 2, 2, color);
    }

    private static void patch(GuiGraphicsExtractor ctx, net.minecraft.resources.Identifier texture, int x, int y, int w, int h,
                              int u, int v, int sw, int sh, int color) {
        if (w > 0 && h > 0) ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, sw, sh, 514, 514, color);
    }

    public static void surface(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int edge, int fill) {
        rounded(ctx, x, y, w, h, r, fill);
        shape(ctx, RIM, x, y, w, h, r, edge);
    }

    public static void panel(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float opacity) {
        ThemePalette p = ThemeManager.palette();
        if (!HabibConfig.appearance.reducedTransparency) {
            for (int spread = 8; spread >= 2; spread -= 2) {
                rounded(ctx, x - spread, y + 3 - spread / 2, w + spread * 2, h + spread,
                        28 + spread, ThemePalette.alpha(0x06000000, opacity));
            }
        }
        surface(ctx, x, y, w, h, 28, ThemePalette.alpha(p.edge, opacity), ThemePalette.alpha(p.panel, opacity));
        // Gentle vertical sheen, explicitly separate from the real vanilla background blur.
        if (!HabibConfig.appearance.reducedTransparency) {
            for (int band = 0; band < 8; band++) {
                int top = y + 3 + band * 4;
                ctx.fill(x + 28, top, x + w - 28, top + 4, ThemePalette.alpha(0x07FFFFFF, opacity * (1f - band / 8f)));
            }
        }
    }

    public static void toggle(GuiGraphicsExtractor ctx, int x, int y, float progress, float opacity) {
        ThemePalette p = ThemeManager.palette();
        rounded(ctx, x, y, 34, 18, 9, ThemePalette.alpha(ThemePalette.mix(p.track, p.accent, progress), opacity));
        float knob = x + 2f + Math.max(0f, Math.min(1f, progress)) * 16f;
        rounded(ctx, knob, y + 3, 14, 14, 7, ThemePalette.alpha(0x20000000, opacity));
        rounded(ctx, knob, y + 2, 14, 14, 7, ThemePalette.alpha(0xFFF9FBFF, opacity));
    }

    public static void slider(GuiGraphicsExtractor ctx, int x, int y, int width, float value, float opacity) {
        ThemePalette p = ThemeManager.palette();
        float end = x + Math.max(0f, Math.min(1f, value)) * width;
        rounded(ctx, x, y + 5, width, 4, 2, ThemePalette.alpha(p.track, opacity * 0.65f));
        rounded(ctx, x, y + 5, Math.max(2, Math.round(end - x)), 4, 2, ThemePalette.alpha(p.accent, opacity));
        rounded(ctx, end - 5, y + 2, 10, 10, 5, ThemePalette.alpha(0xFFF7FAFF, opacity));
    }

    /** Compact cartoon bomb mark, drawn geometrically so it works with every font. */
    public static void bomb(GuiGraphicsExtractor ctx, int x, int y, int color, int accent) {
        rounded(ctx, x + 1, y + 6, 14, 14, 7, color);
        rounded(ctx, x + 10, y + 4, 5, 5, 2, color);
        rounded(ctx, x + 13, y + 1, 2, 5, 1, color);
        rounded(ctx, x + 14, y + 1, 4, 2, 1, color);
        rounded(ctx, x + 18, y, 2, 2, 1, accent);
        rounded(ctx, x + 18, y + 4, 2, 2, 1, accent);
        rounded(ctx, x + 4, y + 9, 3, 4, 2, ThemePalette.alpha(ThemeManager.palette().panel, 0.65f));
    }

    /** Small geometric icons avoid font-dependent Unicode glyphs. */
    public static void icon(GuiGraphicsExtractor ctx, int kind, int x, int y, int color) {
        if (kind == 1) { // crossed blades
            ctx.pose().pushMatrix();
            try {
                ctx.pose().translate(x + 5f, y + 5f);
                ctx.pose().rotate((float) Math.PI / 4f);
                rounded(ctx, -1, -6, 2, 12, 1, color);
                ctx.pose().rotate(-(float) Math.PI / 2f);
                rounded(ctx, -1, -6, 2, 12, 1, color);
            } finally { ctx.pose().popMatrix(); }
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
