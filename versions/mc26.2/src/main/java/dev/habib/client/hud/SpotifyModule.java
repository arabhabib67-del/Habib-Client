package dev.habib.client.hud;

import dev.habib.client.HabibClient;
import dev.habib.client.gui.render.GlassRenderer;
import dev.habib.client.gui.theme.ThemeManager;
import dev.habib.client.gui.theme.ThemePalette;
import dev.habib.client.util.NowPlaying;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.KeyMapping;

/** Clean now-playing card with prominent album art, track details, and Spotify shortcuts. */
public class SpotifyModule extends HudModule {
    private static final int W = 228;
    private static final int H = 70;
    private static final int ART_SIZE = 44;
    private static final int SPOTIFY_GREEN = 0xFF1ED760;
    private static final NowPlaying.Snapshot SAMPLE =
            new NowPlaying.Snapshot("Mild Orange", "Some Feeling", "Foreplay", "Playing",
                    82_000L, 194_000L, 0L, "");

    public SpotifyModule(int x, int y) {
        super("spotify", "Spotify", x, y);
        width = W;
        height = H;
    }

    @Override
    protected void render(GuiGraphicsExtractor ctx, Minecraft mc, boolean editing) {
        NowPlaying.Snapshot snapshot = NowPlaying.snapshot();
        boolean preview = snapshot == null && editing;
        if (preview) snapshot = SAMPLE;
        boolean hasTrack = snapshot != null && !snapshot.title().isBlank();
        width = W;
        height = H;

        ThemePalette palette = ThemeManager.palette();
        if (dev.habib.client.config.HabibConfig.hudBackground) {
            GlassRenderer.surface(ctx, 0, 0, W, H, 9,
                    ThemePalette.alpha(palette.edge, 0.78f), ThemePalette.alpha(palette.panel, 0.94f));
        }

        int artX = 10;
        int artY = (H - ART_SIZE) / 2;
        int tile = hasTrack ? tileColor(snapshot.title() + snapshot.artist()) : 0xFF1B3928;
        GlassRenderer.rounded(ctx, artX - 1, artY - 1, ART_SIZE + 2, ART_SIZE + 2, 8,
                ThemePalette.alpha(scale(tile, 0.72f), 0.92f));
        if (!SpotifyArtwork.draw(ctx, mc, artX, artY, ART_SIZE)) {
            String note = "♪";
            ctx.text(mc.font, note, artX + (ART_SIZE - mc.font.width(note)) / 2, artY + 16,
                    hasTrack ? palette.text : SPOTIFY_GREEN, false);
        }

        int textX = 63;
        int rightX = W - 10;
        String title = hasTrack ? snapshot.title() : "Spotify";
        String detail = hasTrack ? snapshot.artist() : NowPlaying.statusText();
        drawTrimmed(ctx, mc, title, textX, 8, rightX - textX,
                hasTrack ? palette.text : palette.muted, true);
        if (hasTrack) {
            GlassRenderer.rounded(ctx, textX, 25, 5, 5, 3,
                    snapshot.playing() ? SPOTIFY_GREEN : palette.muted);
            drawTrimmed(ctx, mc, detail, textX + 9, 21, rightX - textX - 9, palette.muted, false);
        } else {
            drawTrimmed(ctx, mc, detail, textX, 21, rightX - textX, palette.muted, false);
        }

        if (hasTrack) {
            if (snapshot.hasTimeline()) {
                GlassRenderer.rounded(ctx, textX, 37, rightX - textX, 3, 2,
                        ThemePalette.alpha(palette.track, 0.75f));
                int fillWidth = Math.round((rightX - textX) * snapshot.progress());
                if (fillWidth > 0) {
                    int progressColor = color == 0xFFFFFFFF ? SPOTIFY_GREEN : color;
                    GlassRenderer.rounded(ctx, textX, 37, fillWidth, 3, 2, progressColor);
                }
                String elapsed = NowPlaying.clock(snapshot.positionMs());
                String duration = NowPlaying.clock(snapshot.durMs());
                ctx.text(mc.font, elapsed, textX, 44, palette.muted, false);
                int durationWidth = mc.font.width(duration);
                ctx.text(mc.font, duration, rightX - durationWidth, 44, palette.muted, false);
            } else {
                drawTrimmed(ctx, mc, snapshot.playbackStatus(), textX, 39,
                        rightX - textX, palette.muted, false);
            }
        }

        String controls = keyName(HabibClient.spotifyPreviousKey) + " Prev   "
                + keyName(HabibClient.spotifyPlayPauseKey) + " Play/Pause   "
                + keyName(HabibClient.spotifyNextKey) + " Next";
        drawTrimmed(ctx, mc, controls, textX, 58, rightX - textX, palette.muted, false);
    }

    private static String keyName(KeyMapping key) {
        return key == null ? "-" : key.getTranslatedKeyMessage().getString();
    }

    private static void drawTrimmed(GuiGraphicsExtractor ctx, Minecraft mc, String value, int x, int y,
                                    int maxWidth, int color, boolean shadow) {
        if (mc.font.width(value) <= maxWidth) {
            ctx.text(mc.font, value, x, y, color, shadow);
            return;
        }
        String ellipsis = "…";
        int textWidth = Math.max(0, maxWidth - mc.font.width(ellipsis));
        String trimmed = mc.font.plainSubstrByWidth(value, textWidth);
        ctx.text(mc.font, trimmed + ellipsis, x, y, color, shadow);
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
