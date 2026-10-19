package dev.habib.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.habib.client.hud.HudManager;
import dev.habib.client.hud.HudModule;
import dev.habib.client.util.SwingAnimator;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Saves/loads module positions, scale, colour and enabled state to config/habibclient.json. */
public final class HabibConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("habibclient.json");

    /** Hold-to-zoom FOV multiplier (smaller = more zoom). */
    public static float zoomFactor = 0.25f;

    /** First-person swing animation: index into SwingAnimator.NAMES (0 = vanilla). */
    public static int swingStyle = 1;
    public static float swingIntensity = 1f;
    /** Sword: play the custom animation only when the swing actually hits an entity. */
    public static boolean swingOnlyOnHit = true;


    /** Optimizers (both off by default): instant client-side crystal break, and hiding explosion particles. */
    public static boolean crystalOptimizer = false;
    public static boolean noExplosionParticles = false;
    public static boolean noTotemParticles = false;
    public static boolean noHitParticles = false;
    /** Hides pearl trail and landing particles (visual only). */
    public static boolean pearlOptimizer = false;

    /** Screen alerts (all off by default). */
    public static boolean lowHealthWarning = false;
    public static boolean popFlash = false;
    public static boolean popMessages = false;
    public static boolean lowArmorWarning = false;

    /** Auto-update: GitHub repo (owner/name) to check, empty = use the one built into the jar; and on/off. */
    public static String updateRepo = "";
    public static boolean autoUpdate = true;

    /** Draw the translucent box behind HUD modules. Off = plain text only. */
    public static boolean hudBackground = true;

    /** Armor HUD: 0 = vertical list, 1 = horizontal row. */
    public static int armorLayout = 0;
    /** Armor HUD: also show the item in your main hand. */
    public static boolean armorShowHeld = true;
    /** Armor HUD durability: 0 = uses left, 1 = percent, 2 = vanilla durability bar, 3 = hidden. */
    public static int armorDurability = 0;

    private static class ModuleData {
        boolean enabled = true;
        int x;
        int y;
        float scale = 1f;
        int color = 0xFFFFFFFF;
    }

    private static class Data {
        float zoomFactor = 0.25f;
        int swingStyle = 1;
        float swingIntensity = 1f;
        boolean swingOnlyOnHit = true;
        boolean crystalOptimizer = false;
        boolean noExplosionParticles = false;
        boolean noTotemParticles = false;
        boolean noHitParticles = false;
        boolean pearlOptimizer = false;
        boolean lowHealthWarning = false;
        boolean popFlash = false;
        boolean popMessages = false;
        boolean lowArmorWarning = false;
        boolean hudBackground = true;
        String updateRepo = "";
        boolean autoUpdate = true;
        int armorLayout = 0;
        boolean armorShowHeld = true;
        int armorDurability = 0;
        Map<String, ModuleData> modules = new LinkedHashMap<>();
    }

    private HabibConfig() {}

    public static void load() {
        if (!Files.exists(FILE)) {
            save();
            return;
        }
        try {
            Data data = GSON.fromJson(Files.readString(FILE), Data.class);
            if (data == null) return;
            zoomFactor = Math.max(0.05f, Math.min(1f, data.zoomFactor));
            swingStyle = Math.max(0, Math.min(SwingAnimator.COUNT - 1, data.swingStyle));
            swingIntensity = Math.max(0.25f, Math.min(2f, data.swingIntensity));
            hudBackground = data.hudBackground;
            updateRepo = data.updateRepo == null ? "" : data.updateRepo;
            autoUpdate = data.autoUpdate;
            armorLayout = Math.max(0, Math.min(1, data.armorLayout));
            armorShowHeld = data.armorShowHeld;
            armorDurability = Math.max(0, Math.min(3, data.armorDurability));
            swingOnlyOnHit = data.swingOnlyOnHit;
            crystalOptimizer = data.crystalOptimizer;
            noExplosionParticles = data.noExplosionParticles;
            noTotemParticles = data.noTotemParticles;
            noHitParticles = data.noHitParticles;
            pearlOptimizer = data.pearlOptimizer;
            lowHealthWarning = data.lowHealthWarning;
            popFlash = data.popFlash;
            popMessages = data.popMessages;
            lowArmorWarning = data.lowArmorWarning;
            for (HudModule m : HudManager.MODULES) {
                ModuleData d = data.modules == null ? null : data.modules.get(m.id);
                if (d == null) continue;
                m.enabled = d.enabled;
                m.x = d.x;
                m.y = d.y;
                m.scale = Math.max(0.5f, Math.min(3f, d.scale));
                m.color = d.color;
            }
        } catch (Exception e) {
            System.err.println("[Habib Client] Failed to read config, using defaults: " + e);
        }
    }

    public static void save() {
        Data data = new Data();
        data.zoomFactor = zoomFactor;
        data.swingStyle = swingStyle;
        data.swingIntensity = swingIntensity;
        data.hudBackground = hudBackground;
        data.updateRepo = updateRepo;
        data.autoUpdate = autoUpdate;
        data.armorLayout = armorLayout;
        data.armorShowHeld = armorShowHeld;
        data.armorDurability = armorDurability;
        data.swingOnlyOnHit = swingOnlyOnHit;
        data.crystalOptimizer = crystalOptimizer;
        data.noExplosionParticles = noExplosionParticles;
        data.noTotemParticles = noTotemParticles;
        data.noHitParticles = noHitParticles;
        data.pearlOptimizer = pearlOptimizer;
        data.lowHealthWarning = lowHealthWarning;
        data.popFlash = popFlash;
        data.popMessages = popMessages;
        data.lowArmorWarning = lowArmorWarning;
        for (HudModule m : HudManager.MODULES) {
            ModuleData d = new ModuleData();
            d.enabled = m.enabled;
            d.x = m.x;
            d.y = m.y;
            d.scale = m.scale;
            d.color = m.color;
            data.modules.put(m.id, d);
        }
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(data));
        } catch (IOException e) {
            System.err.println("[Habib Client] Failed to save config: " + e);
        }
    }
}
