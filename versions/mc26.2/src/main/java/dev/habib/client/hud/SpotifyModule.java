package dev.habib.client.hud;

import dev.habib.client.util.NowPlaying;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Now-playing card: cover tile on the left, song and artist, "elapsed / total" on the right and a thin progress
 * bar along the bottom. The bar is red like the reference design; pick any other colour in the menu to recolour it.
 */
public class SpotifyModule extends HudModule {
    private static final int W = 150;
    private static final int H = 34;
    private static final int RED = 0xFFE5484D;
    private static final NowPlaying.Snapshot SAMPLE =
            new NowPlaying.Snapshot("Artist", "Song title", false, 135_000L, 195_000L, 0L);

    public SpotifyModule(int x, int y) {
        super("spotify", "Spotify", x, y);
        width = W;
        height = H;
    }

    @Override
    protected void render(GuiGraphics ctx, Minecraft mc, boolean editing) {
        NowPlaying.Snapshot s = NowPlaying.snapshot();
        if (s == null || s.title().isEmpty()) {
            if (!editing) return; // nothing playing: draw nothing
            s = SAMPLE;           // layout editor / menu preview
        }
        width = W;
        height = H;
        box(ctx, 0, 0, W, H, 0x99000000);

        // cover tile: a stand-in colour block (album art isn't available locally) with a note on it
        int hue = tileColor(s.title() + s.artist());
        ctx.fill(4, 4, 26, 26, 0xFF000000 | scale(hue, 0.55f));
        ctx.fill(5, 5, 25, 25, 0xFF000000 | hue);
        String note = "\u266A";
        ctx.drawString(mc.font, note, 15 - mc.font.width(note) / 2, 11, 0xFFFFFFFF, false);

        int textX = 31;
        int rightX = W - 4;
        boolean timeline = s.hasTimeline();
        String time = timeline ? NowPlaying.clock(s.positionMs()) + " / " + NowPlaying.clock(s.durMs()) : "";
        int timeW = timeline ? mc.font.width(time) : 0;

        int titleCol = s.playing() ? 0xFFFFFFFF : 0xFFB0B0B0;
        ctx.drawString(mc.font, mc.font.plainSubstrByWidth(s.title(), rightX - textX), textX, 5, titleCol, true);
        int artistMax = rightX - textX - (timeline ? timeW + 6 : 0);
        ctx.drawString(mc.font, mc.font.plainSubstrByWidth(s.artist(), Math.max(10, artistMax)), textX, 16, 0xFF9A9A9A, true);
        if (timeline) ctx.drawString(mc.font, time, rightX - timeW, 16, 0xFFB8B8B8, true);

        if (timeline) {
            int barL = 4;
            int barR = W - 4;
            int barY = 28;
            int fillCol = !s.playing() ? 0xFF777777 : (color == 0xFFFFFFFF ? RED : color);
            ctx.fill(barL, barY, barR, barY + 2, 0xFF2B2B2B);
            int fillW = Math.round((barR - barL) * s.progress());
            if (fillW > 0) ctx.fill(barL, barY, barL + fillW, barY + 2, fillCol);
        }
    }

    /** Muted, stable colour per song so the stand-in cover differs from track to track (RGB, no alpha). */
    private static int tileColor(String seed) {
        float h = ((seed.hashCode() & 0x7FFFFFFF) % 360) / 60f;
        float sat = 0.45f;
        float val = 0.55f;
        float c = val * sat;
        float x = c * (1f - Math.abs(h % 2f - 1f));
        float m = val - c;
        float r, g, b;
        int sector = (int) h;
        switch (sector) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    private static int scale(int rgb, float k) {
        int r = Math.round(((rgb >> 16) & 255) * k);
        int g = Math.round(((rgb >> 8) & 255) * k);
        int b = Math.round((rgb & 255) * k);
        return (r << 16) | (g << 8) | b;
    }
}
