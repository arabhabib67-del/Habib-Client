package dev.habib.client.hud;

import dev.habib.client.util.NowPlaying;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/** Loads the Spotify desktop player's cached album art as a client-side texture. */
final class SpotifyArtwork {
    private static final Identifier TEXTURE = Identifier.of("habibclient", "spotify_album_art");
    private static String loadedUrl = "";
    private static String failedUrl = "";
    private static byte[] failedBytes;

    private SpotifyArtwork() {}

    static boolean draw(DrawContext ctx, MinecraftClient mc, int x, int y, int size) {
        NowPlaying.ArtworkSnapshot artwork = NowPlaying.artworkSnapshot();
        String url = artwork.url();
        byte[] bytes = artwork.bytes();
        if (bytes != failedBytes) failedUrl = "";
        if (bytes != null && !url.isBlank() && !url.equals(loadedUrl) && !url.equals(failedUrl)) {
            try {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
                NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "Habib Client Spotify art", image);
                mc.getTextureManager().registerTexture(TEXTURE, texture);
                texture.upload();
                loadedUrl = url;
                failedUrl = "";
                failedBytes = null;
            } catch (IOException | RuntimeException ignored) {
                failedUrl = url;
                failedBytes = bytes;
            }
        }
        if (!url.equals(loadedUrl) || loadedUrl.isBlank()) return false;
        ctx.drawTexturedQuad(TEXTURE, x, y, x + size, y + size, 0f, 0f, 1f, 1f);
        return true;
    }
}
