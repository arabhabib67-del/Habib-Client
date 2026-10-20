package dev.habib.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.habib.client.beta.BetaAccess;
import dev.habib.client.config.HabibConfig;
import dev.habib.client.gui.BetaAccessScreen;
import dev.habib.client.gui.MenuScreen;
import dev.habib.client.hud.HudManager;
import dev.habib.client.update.Updater;
import dev.habib.client.util.CrystalOptimizer;
import dev.habib.client.util.Effects;
import dev.habib.client.util.ConsumableOptimizer;
import dev.habib.client.util.PearlOptimizer;
import dev.habib.client.util.NowPlaying;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class HabibClient implements ClientModInitializer {
    public static final String NAME = "Habib Client";

    public static KeyMapping openEditorKey;
    public static KeyMapping zoomKey;
    public static KeyMapping spotifyPreviousKey;
    public static KeyMapping spotifyPlayPauseKey;
    public static KeyMapping spotifyNextKey;

    private static boolean zooming = false;
    private static int savedFov = 70;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("habibclient", "main"));
        openEditorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.habibclient.editor", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        zoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.habibclient.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, category));
        spotifyPreviousKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.habibclient.spotify_previous", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, category));
        spotifyPlayPauseKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.habibclient.spotify_play_pause", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, category));
        spotifyNextKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.habibclient.spotify_next", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, category));

        HudManager.init();
        Updater.init();
        CrystalOptimizer.init();
        Effects.init();
        PearlOptimizer.init();
        ConsumableOptimizer.init();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!BetaAccess.isAuthorized()) {
                stopZoom(mc);
                if (!(mc.gui.screen() instanceof BetaAccessScreen)) {
                    mc.gui.setScreen(new BetaAccessScreen(mc.gui.screen()));
                }
                return;
            }
            while (openEditorKey.consumeClick()) {
                if (mc.gui.screen() == null) {
                    mc.gui.setScreen(new MenuScreen());
                }
            }
            if (mc.gui.screen() == null && mc.player != null) {
                while (spotifyPreviousKey.consumeClick()) NowPlaying.previous();
                while (spotifyPlayPauseKey.consumeClick()) NowPlaying.playPause();
                while (spotifyNextKey.consumeClick()) NowPlaying.next();
            }
            tickZoom(mc);
        });

        // never leave the FOV option stuck at the zoomed value
        ClientLifecycleEvents.CLIENT_STOPPING.register(HabibClient::stopZoom);
    }

    /** Hold-to-zoom by temporarily lowering the FOV option (no mixin needed). */
    private static void tickZoom(Minecraft mc) {
        var fov = mc.options.fov();
        if (zoomKey.isDown() && mc.player != null) {
            if (!zooming) {
                savedFov = fov.get();
                zooming = true;
            }
            int target = Math.max(30, Math.min(110, Math.round(savedFov * HabibConfig.zoomFactor)));
            if (fov.get() != target) fov.set(target);
        } else {
            stopZoom(mc);
        }
    }

    private static void stopZoom(Minecraft mc) {
        if (zooming) {
            mc.options.fov().set(savedFov);
            zooming = false;
        }
    }
}
