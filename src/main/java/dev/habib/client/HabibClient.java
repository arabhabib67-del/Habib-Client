package dev.habib.client;

import dev.habib.client.config.HabibConfig;
import dev.habib.client.gui.MenuScreen;
import dev.habib.client.hud.HudManager;
import dev.habib.client.update.Updater;
import dev.habib.client.util.AnchorOptimizer;
import dev.habib.client.util.CrystalOptimizer;
import dev.habib.client.util.Effects;
import dev.habib.client.util.ConsumableOptimizer;
import dev.habib.client.util.PearlOptimizer;
import dev.habib.client.util.NowPlaying;
import dev.habib.client.telemetry.UsageStats;
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
        UsageStats.initialize();
        Updater.init();
        AnchorOptimizer.init();
        CrystalOptimizer.init();
        Effects.init();
        PearlOptimizer.init();
        ConsumableOptimizer.init();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
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

    /** Apply the saved FOV for the active camera, with hold-to-zoom layered on top. */
    private static void tickZoom(MinecraftClient mc) {
        var fov = mc.options.getFov();
        initializeFovDefaults(fov.getValue());
        int baseFov = mc.options.getPerspective().isFirstPerson()
                ? HabibConfig.firstPersonFov : HabibConfig.thirdPersonFov;
        zooming = zoomKey.isPressed() && mc.player != null;
        int targetFov = zooming
                ? Math.max(HabibConfig.MIN_FOV,
                        Math.min(HabibConfig.MAX_FOV, Math.round(baseFov * HabibConfig.zoomFactor)))
                : baseFov;
        if (fov.getValue() != targetFov) {
            fov.setValue(targetFov);
        }
    }

    private static void initializeFovDefaults(int currentFov) {
        boolean changed = false;
        if (HabibConfig.firstPersonFov < 30) {
            HabibConfig.firstPersonFov = currentFov;
            changed = true;
        }
        if (HabibConfig.thirdPersonFov < 30) {
            HabibConfig.thirdPersonFov = currentFov;
            changed = true;
        }
        if (changed) HabibConfig.save();
    }

    private static void stopZoom(MinecraftClient mc) {
        if (zooming) {
            int currentFov = mc.options.getFov().getValue();
            initializeFovDefaults(currentFov);
            int baseFov = mc.options.getPerspective().isFirstPerson()
                    ? HabibConfig.firstPersonFov : HabibConfig.thirdPersonFov;
            mc.options.getFov().setValue(baseFov);
            zooming = false;
        }
    }
}
