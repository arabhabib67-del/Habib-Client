package dev.habib.client.hud;

import dev.habib.client.util.NowPlaying;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Loads the Spotify desktop player's cached album art as a client-side texture. */
final class SpotifyArtwork {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("habibclient", "spotify_album_art");
    private static String loadedUrl = "";
    private static String failedUrl = "";

    private SpotifyArtwork() {}

    static boolean draw(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, int size) {
        String url = NowPlaying.artworkUrl();
        byte[] bytes = NowPlaying.artworkBytes();
        if (bytes != null && !url.isBlank() && !url.equals(loadedUrl) && !url.equals(failedUrl)) {
            try {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
                DynamicTexture texture = new DynamicTexture(() -> "Habib Client Spotify art", image);
                mc.getTextureManager().register(TEXTURE, texture);
                texture.upload();
                loadedUrl = url;
                failedUrl = "";
            } catch (IOException | RuntimeException ignored) {
                failedUrl = url;
            }
        }
        if (!url.equals(loadedUrl) || loadedUrl.isBlank()) return false;
        ctx.blit(TEXTURE, x, y, size, size, 0f, 0f, 1f, 1f);
        return true;
    }
}
