package dev.habib.client.hud;

import dev.habib.client.HabibClient;
import dev.habib.client.gui.render.GlassRenderer;
import dev.habib.client.gui.theme.ThemeManager;
import dev.habib.client.gui.theme.ThemePalette;
import dev.habib.client.util.NowPlaying;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Compact now-playing card with album art, live progress, and the active Spotify shortcuts. */
public class SpotifyModule extends HudModule {
    private static final int W = 220;
    private static final int H = 58;
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
    protected void render(DrawContext ctx, MinecraftClient mc, boolean editing) {
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

        int tile = hasTrack ? tileColor(snapshot.title() + snapshot.artist()) : 0xFF1B3928;
        GlassRenderer.rounded(ctx, 7, 8, 36, 36, 7, 0xFF000000 | scale(tile, 0.72f));
        if (!SpotifyArtwork.draw(ctx, mc, 8, 9, 34)) {
            String note = "♪";
            ctx.drawText(mc.textRenderer, note, 25 - mc.textRenderer.getWidth(note) / 2, 20,
                    hasTrack ? palette.text : SPOTIFY_GREEN, false);
        }

        int textX = 51;
        int rightX = W - 8;
        String title = hasTrack ? snapshot.title() : "Spotify";
        String detail = hasTrack ? trackDetails(snapshot) : NowPlaying.statusText();
        drawTrimmed(ctx, mc, title, textX, 7, rightX - textX,
                hasTrack ? palette.text : palette.muted, true);
        drawTrimmed(ctx, mc, detail, textX, 19, rightX - textX, palette.muted, false);

        if (hasTrack) {
            int progressRight = rightX;
            if (snapshot.hasTimeline()) {
                GlassRenderer.rounded(ctx, textX, 32, progressRight - textX, 2, 1,
                        ThemePalette.alpha(palette.track, 0.75f));
                int fillWidth = Math.round((progressRight - textX) * snapshot.progress());
                if (fillWidth > 0) {
                    int progressColor = color == 0xFFFFFFFF ? SPOTIFY_GREEN : color;
                    GlassRenderer.rounded(ctx, textX, 32, fillWidth, 2, 1, progressColor);
                }
            }

            String state = snapshot.playbackStatus();
            GlassRenderer.rounded(ctx, textX, 38, 5, 5, 3,
                    snapshot.playing() ? SPOTIFY_GREEN : palette.muted);
            ctx.drawText(mc.textRenderer, state, textX + 9, 36, palette.muted, false);
            if (snapshot.hasTimeline()) {
                String time = NowPlaying.clock(snapshot.positionMs()) + " / " + NowPlaying.clock(snapshot.durMs());
                int timeWidth = mc.textRenderer.getWidth(time);
                ctx.drawText(mc.textRenderer, time, rightX - timeWidth, 36, palette.muted, false);
            }
        }

        String controls = keyName(HabibClient.spotifyPreviousKey) + " Prev   "
                + keyName(HabibClient.spotifyPlayPauseKey) + " Play/Pause   "
                + keyName(HabibClient.spotifyNextKey) + " Next";
        drawTrimmed(ctx, mc, controls, textX, 48, rightX - textX, palette.muted, false);
    }

    private static String keyName(net.minecraft.client.option.KeyBinding key) {
        return key == null ? "-" : key.getBoundKeyLocalizedText().getString();
    }

    private static String trackDetails(NowPlaying.Snapshot snapshot) {
        if (snapshot.artist().isBlank()) return snapshot.album();
        return snapshot.album().isBlank() ? snapshot.artist() : snapshot.artist() + " · " + snapshot.album();
    }

    private static void drawTrimmed(DrawContext ctx, MinecraftClient mc, String value, int x, int y,
                                    int maxWidth, int color, boolean shadow) {
        if (mc.textRenderer.getWidth(value) <= maxWidth) {
            ctx.drawText(mc.textRenderer, value, x, y, color, shadow);
            return;
        }
        String ellipsis = "…";
        int textWidth = Math.max(0, maxWidth - mc.textRenderer.getWidth(ellipsis));
        String trimmed = mc.textRenderer.trimToWidth(value, textWidth);
        ctx.drawText(mc.textRenderer, trimmed + ellipsis, x, y, color, shadow);
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
