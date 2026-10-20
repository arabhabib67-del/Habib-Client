package dev.habib.client.hud;

import dev.habib.client.util.NowPlaying;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Now-playing card with album art, progress, and the configurable Spotify playback shortcuts. */
public class SpotifyModule extends HudModule {
    private static final int W = 194;
    private static final int H = 43;
    private static final int RED = 0xFFE5484D;
    private static final NowPlaying.Snapshot SAMPLE =
            new NowPlaying.Snapshot("Artist", "Song title", false, 135_000L, 195_000L, 0L, "");
    private static final NowPlaying.Snapshot IDLE =
            new NowPlaying.Snapshot("Spotify", "Nothing playing", false, 0L, 0L, 0L, "");

    public SpotifyModule(int x, int y) {
        super("spotify", "Spotify", x, y);
        width = W;
        height = H;
    }

    @Override
    protected void render(GuiGraphicsExtractor ctx, Minecraft mc, boolean editing) {
        NowPlaying.Snapshot s = NowPlaying.snapshot();
        if (s == null || s.title().isEmpty()) {
            s = editing ? SAMPLE : IDLE;
        }
        width = W;
        height = H;
        box(ctx, 0, 0, W, H, 0x99000000);

        int hue = tileColor(s.title() + s.artist());
        ctx.fill(4, 4, 32, 32, 0xFF000000 | scale(hue, 0.55f));
        if (!SpotifyArtwork.draw(ctx, mc, 5, 5, 26)) {
            ctx.fill(5, 5, 31, 31, 0xFF000000 | hue);
            String note = "\u266A";
            ctx.text(mc.font, note, 18 - mc.font.width(note) / 2, 14, 0xFFFFFFFF, false);
        }

        int textX = 37;
        int rightX = W - 5;
        boolean timeline = s.hasTimeline();
        String time = timeline ? NowPlaying.clock(s.positionMs()) + " / " + NowPlaying.clock(s.durMs()) : "";
        int timeW = timeline ? mc.font.width(time) : 0;

        int titleCol = s.playing() ? 0xFFFFFFFF : 0xFFB0B0B0;
        ctx.text(mc.font, mc.font.plainSubstrByWidth(s.title(), rightX - textX), textX, 5, titleCol, true);
        int artistMax = rightX - textX - (timeline ? timeW + 6 : 0);
        ctx.text(mc.font, mc.font.plainSubstrByWidth(s.artist(), Math.max(10, artistMax)), textX, 16, 0xFF9A9A9A, true);
        if (timeline) ctx.text(mc.font, time, rightX - timeW, 16, 0xFFB8B8B8, true);

        shortcut(ctx, mc, "|< F7", 37);
        shortcut(ctx, mc, ">|| F8", 84);
        shortcut(ctx, mc, "F9 >|", 134);

        if (timeline) {
            int barL = 4;
            int barR = W - 4;
            int barY = 39;
            int fillCol = !s.playing() ? 0xFF777777 : (color == 0xFFFFFFFF ? RED : color);
            ctx.fill(barL, barY, barR, barY + 2, 0xFF2B2B2B);
            int fillW = Math.round((barR - barL) * s.progress());
            if (fillW > 0) ctx.fill(barL, barY, barL + fillW, barY + 2, fillCol);
        }
    }

    private static void shortcut(GuiGraphicsExtractor ctx, Minecraft mc, String text, int x) {
        ctx.fill(x, 25, x + 43, 37, 0x8838383C);
        ctx.text(mc.font, text, x + 4, 28, 0xFFCCCCD0, false);
    }

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
