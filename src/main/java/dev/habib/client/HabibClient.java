package dev.habib.client;

import dev.habib.client.config.HabibConfig;
import dev.habib.client.beta.BetaAccess;
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
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class HabibClient implements ClientModInitializer {
    public static final String NAME = "Habib Client";

    public static KeyBinding openEditorKey;
    public static KeyBinding zoomKey;
    public static KeyBinding spotifyPreviousKey;
    public static KeyBinding spotifyPlayPauseKey;
    public static KeyBinding spotifyNextKey;

    private static boolean zooming = false;
    private static int savedFov = 70;

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("habibclient", "main"));
        openEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.habibclient.editor", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.habibclient.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, category));
        spotifyPreviousKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.habibclient.spotify_previous", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F7, category));
        spotifyPlayPauseKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.habibclient.spotify_play_pause", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, category));
        spotifyNextKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.habibclient.spotify_next", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F9, category));

        HudManager.init();
        Updater.init();
        CrystalOptimizer.init();
        Effects.init();
        PearlOptimizer.init();
        ConsumableOptimizer.init();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!BetaAccess.isAuthorized()) {
                stopZoom(mc);
                if (!(mc.currentScreen instanceof BetaAccessScreen)) {
                    mc.setScreen(new BetaAccessScreen(mc.currentScreen));
                }
                return;
            }
            while (openEditorKey.wasPressed()) {
                if (mc.currentScreen == null) {
                    mc.setScreen(new MenuScreen());
                }
            }
            if (mc.currentScreen == null && mc.player != null) {
                while (spotifyPreviousKey.wasPressed()) NowPlaying.previous();
                while (spotifyPlayPauseKey.wasPressed()) NowPlaying.playPause();
                while (spotifyNextKey.wasPressed()) NowPlaying.next();
            }
            tickZoom(mc);
        });

        // never leave the FOV option stuck at the zoomed value
        ClientLifecycleEvents.CLIENT_STOPPING.register(HabibClient::stopZoom);
    }

    /** Hold-to-zoom by temporarily lowering the FOV option (no mixin needed). */
    private static void tickZoom(MinecraftClient mc) {
        var fov = mc.options.getFov();
        if (zoomKey.isPressed() && mc.player != null) {
            if (!zooming) {
                savedFov = fov.getValue();
                zooming = true;
            }
            int target = Math.max(30, Math.min(110, Math.round(savedFov * HabibConfig.zoomFactor)));
            if (fov.getValue() != target) fov.setValue(target);
        } else {
            stopZoom(mc);
        }
    }

    private static void stopZoom(MinecraftClient mc) {
        if (zooming) {
            mc.options.getFov().setValue(savedFov);
            zooming = false;
        }
    }
}
