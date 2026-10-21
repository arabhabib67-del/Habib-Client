package dev.habib.client.config;

import dev.habib.client.gui.theme.AppearanceSettings;
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
    /** Hides eating, drinking and splash potion particles (visual only). */
    public static boolean consumableOptimizer = false;

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

    public static volatile AppearanceSettings appearance = new AppearanceSettings();

    private static class ModuleData {
        boolean enabled = true;
        int x;
        int y;
        float scale = 1f;
        int color = 0xFFFFFFFF;
    }

    private static class Data {
        AppearanceSettings appearance = new AppearanceSettings();
        float zoomFactor = 0.25f;
        int swingStyle = 1;
        float swingIntensity = 1f;
        boolean swingOnlyOnHit = true;
        boolean crystalOptimizer = false;
        boolean noExplosionParticles = false;
        boolean noTotemParticles = false;
        boolean noHitParticles = false;
        boolean pearlOptimizer = false;
        boolean consumableOptimizer = false;
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
            apply(data, true);
        } catch (Exception e) {
            System.err.println("[Habib Client] Failed to read config, using defaults: " + e);
        }
    }

    private static Data capture() {
        Data data = new Data();
        data.appearance = appearance;
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
        data.consumableOptimizer = consumableOptimizer;
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
        return data;
    }

    /** Current settings as compact JSON for profiles and share codes. Never includes the updater settings. */
    static String snapshotJson() {
        Data data = capture();
        data.appearance = null; // appearance belongs to this installation, not a share code
        data.updateRepo = "";
        data.autoUpdate = true;
        return new Gson().toJson(data);
    }

    /** True if this JSON reads as a settings snapshot of a sensible size. */
    static boolean snapshotValid(String json) {
        try {
            Data data = new Gson().fromJson(json, Data.class);
            return data != null && (data.modules == null || data.modules.size() <= 200);
        } catch (Exception e) {
            return false;
        }
    }

    /** Applies a profile's JSON. Returns false (and changes nothing) if it can't be read. */
    static boolean applySnapshotJson(String json) {
        try {
            Data data = new Gson().fromJson(json, Data.class);
            if (data == null) return false;
            if (data.modules != null && data.modules.size() > 200) return false;
            apply(data, false);
            save();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Applies a settings snapshot. Updater settings are only taken from the config file, never from a profile. */
    private static void apply(Data data, boolean updater) {
        zoomFactor = Math.max(0.05f, Math.min(1f, data.zoomFactor));
        swingStyle = Math.max(0, Math.min(SwingAnimator.COUNT - 1, data.swingStyle));
        swingIntensity = Math.max(0.25f, Math.min(2f, data.swingIntensity));
        hudBackground = data.hudBackground;
        if (updater) {
            appearance = data.appearance == null ? new AppearanceSettings() : data.appearance;
            appearance.sanitize();
            updateRepo = data.updateRepo == null ? "" : data.updateRepo;
            autoUpdate = data.autoUpdate;
        }
        armorLayout = Math.max(0, Math.min(1, data.armorLayout));
        armorShowHeld = data.armorShowHeld;
        armorDurability = Math.max(0, Math.min(3, data.armorDurability));
        swingOnlyOnHit = data.swingOnlyOnHit;
        crystalOptimizer = data.crystalOptimizer;
        noExplosionParticles = data.noExplosionParticles;
        noTotemParticles = data.noTotemParticles;
        noHitParticles = data.noHitParticles;
        pearlOptimizer = data.pearlOptimizer;
        consumableOptimizer = data.consumableOptimizer;
        lowHealthWarning = data.lowHealthWarning;
        popFlash = data.popFlash;
        popMessages = data.popMessages;
        lowArmorWarning = data.lowArmorWarning;
        for (HudModule m : HudManager.MODULES) {
            ModuleData d = data.modules == null ? null : data.modules.get(m.id);
            if (d == null) continue;
            m.enabled = d.enabled;
            m.x = Math.max(-2000, Math.min(8000, d.x));
            m.y = Math.max(-2000, Math.min(8000, d.y));
            m.scale = Math.max(0.5f, Math.min(3f, d.scale));
            m.color = d.color;
        }
    }

    public static void save() {
        Data data = capture();
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(data));
        } catch (IOException e) {
            System.err.println("[Habib Client] Failed to save config: " + e);
        }
    }
}
