package dev.habib.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Named profiles of your settings (HUD layout, colours, optimizers, effects, animation ...) saved in
 * config/habibclient-profiles.json, plus share codes: a profile packed into one line of text that anyone can
 * paste back in. A profile never carries the updater settings, so a code can't change where the mod updates from.
 */
public final class ProfileStore {
    public static final class Profile {
        public String name = "";
        public String data = "{}";
    }

    private static final class FileData {
        List<Profile> profiles = new ArrayList<>();
    }

    public static final int MAX_PROFILES = 50;
    public static final int MAX_NAME = 24;
    private static final String PREFIX = "HC1-";
    private static final int MAX_CODE_CHARS = 30_000;
    private static final int MAX_JSON_BYTES = 120_000;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("habibclient-profiles.json");
    private static final List<Profile> PROFILES = new ArrayList<>();
    private static boolean loaded;

    private ProfileStore() {}

    /** The saved profiles, in the order they were made. Don't modify the list directly. */
    public static List<Profile> list() {
        ensureLoaded();
        return PROFILES;
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(FILE)) return;
            FileData fd = GSON.fromJson(Files.readString(FILE), FileData.class);
            if (fd == null || fd.profiles == null) return;
            for (Profile p : fd.profiles) {
                if (p == null || p.data == null) continue;
                String name = cleanName(p.name);
                if (name.isEmpty() || PROFILES.size() >= MAX_PROFILES) continue;
                Profile q = new Profile();
                q.name = name;
                q.data = p.data;
                PROFILES.add(q);
            }
        } catch (Exception e) {
            System.err.println("[Habib Client] Failed to read profiles: " + e);
        }
    }

    private static void save() {
        FileData fd = new FileData();
        fd.profiles = new ArrayList<>(PROFILES);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(fd));
        } catch (IOException e) {
            System.err.println("[Habib Client] Failed to save profiles: " + e);
        }
    }

    /** Printable characters only, trimmed and capped; empty when nothing usable is left. */
    public static String cleanName(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length() && sb.length() < MAX_NAME; i++) {
            char c = raw.charAt(i);
            if (c >= 32 && c != 127 && c != '\u00A7' && !Character.isISOControl(c)) sb.append(c);
        }
        return sb.toString().trim();
    }

    private static String uniqueName(String name) {
        String base = name;
        int n = 2;
        while (indexOf(name) >= 0) {
            String suffix = " (" + n++ + ")";
            name = (base.length() + suffix.length() > MAX_NAME ? base.substring(0, MAX_NAME - suffix.length()) : base) + suffix;
        }
        return name;
    }

    private static int indexOf(String name) {
        for (int i = 0; i < PROFILES.size(); i++) {
            if (PROFILES.get(i).name.equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    /** Saves your current settings as a new profile. Returns a message for the menu. */
    public static String create(String rawName) {
        ensureLoaded();
        String name = cleanName(rawName);
        if (name.isEmpty()) return "Type a name for the profile first.";
        if (PROFILES.size() >= MAX_PROFILES) return "You can keep up to " + MAX_PROFILES + " profiles.";
        Profile p = new Profile();
        p.name = uniqueName(name);
        p.data = HabibConfig.snapshotJson();
        PROFILES.add(p);
        save();
        return "Saved \"" + p.name + "\".";
    }

    /** Overwrites a profile with your current settings. */
    public static String overwrite(int index) {
        ensureLoaded();
        if (index < 0 || index >= PROFILES.size()) return "";
        Profile p = PROFILES.get(index);
        p.data = HabibConfig.snapshotJson();
        save();
        return "Updated \"" + p.name + "\" with your current settings.";
    }

    /** Switches your settings to a profile. */
    public static String apply(int index) {
        ensureLoaded();
        if (index < 0 || index >= PROFILES.size()) return "";
        Profile p = PROFILES.get(index);
        return HabibConfig.applySnapshotJson(p.data)
                ? "Loaded \"" + p.name + "\"."
                : "That profile couldn't be read.";
    }

    public static String delete(int index) {
        ensureLoaded();
        if (index < 0 || index >= PROFILES.size()) return "";
        Profile p = PROFILES.remove(index);
        save();
        return "Deleted \"" + p.name + "\".";
    }

    // ------------------------------------------------------------------ share codes

    /** One line of text holding the profile's name and settings: HC1-... */
    public static String codeFor(int index) {
        ensureLoaded();
        if (index < 0 || index >= PROFILES.size()) return null;
        Profile p = PROFILES.get(index);
        try {
            Wrapper w = new Wrapper();
            w.n = p.name;
            w.d = new Gson().fromJson(p.data, com.google.gson.JsonElement.class);
            byte[] raw = new Gson().toJson(w).getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
                gz.write(raw);
            }
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bos.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    private static final class Wrapper {
        String n;
        com.google.gson.JsonElement d;
    }

    /** Adds the profile in a share code to your list (it does not change your settings until you load it). */
    public static String importCode(String code) {
        ensureLoaded();
        if (code == null) return "Copy a profile code first, then try again.";
        String c = code.trim().replaceAll("\\s+", "");
        if (c.isEmpty()) return "Copy a profile code first, then try again.";
        if (!c.startsWith(PREFIX)) return "That doesn't look like a Habib Client profile code.";
        if (c.length() > MAX_CODE_CHARS) return "That code is too long to be a profile.";
        if (PROFILES.size() >= MAX_PROFILES) return "You can keep up to " + MAX_PROFILES + " profiles.";
        try {
            byte[] packed = Base64.getUrlDecoder().decode(c.substring(PREFIX.length()));
            byte[] raw;
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(packed))) {
                raw = gz.readNBytes(MAX_JSON_BYTES + 1);
            }
            if (raw.length > MAX_JSON_BYTES) return "That code is too large to be a profile.";
            Wrapper w = new Gson().fromJson(new String(raw, StandardCharsets.UTF_8), Wrapper.class);
            if (w == null || w.d == null || !w.d.isJsonObject()) return "That code is damaged or incomplete.";
            String data = w.d.toString();
            // check it really reads as settings before keeping it
            if (!HabibConfig.snapshotValid(data)) return "That code is damaged or incomplete.";
            String name = cleanName(w.n);
            if (name.isEmpty()) name = "Imported";
            Profile p = new Profile();
            p.name = uniqueName(name);
            p.data = data;
            PROFILES.add(p);
            save();
            return "Added \"" + p.name + "\". Press Load to use it.";
        } catch (Exception e) {
            return "That code is damaged or incomplete.";
        }
    }
}
