package dev.habib.client.hud;

import dev.habib.client.config.HabibConfig;
import dev.habib.client.beta.BetaAccess;
import dev.habib.client.gui.MenuScreen;
import dev.habib.client.util.CpsTracker;
import dev.habib.client.util.TotemPops;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HudManager {
    public static final List<HudModule> MODULES = new ArrayList<>();

    // combat state
    private static int combo = 0;
    private static int lastHurtTime = 0;
    private static double lastReach = 0;
    private static double speed = 0;
    private static double lastX = 0;
    private static double lastZ = 0;
    private static int hits = 0;
    private static double trip = 0;
    private static final long SESSION_START = System.currentTimeMillis();

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private HudManager() {}

    public static void init() {
        registerModules();
        categorize();
        HabibConfig.load();

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("habibclient", "hud"),
                (ctx, tickCounter) -> {
            if (!BetaAccess.isAuthorized()) return;
            Minecraft mc = Minecraft.getInstance();
            CpsTracker.poll(mc);
            if (mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() instanceof MenuScreen) return;
            for (HudModule m : MODULES) {
                if (m.enabled) m.draw(ctx, mc, false);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!BetaAccess.isAuthorized()) return;
            if (mc.player == null) return;
            Player p = mc.player;

            // combo resets when you take damage
            if (p.hurtTime > lastHurtTime) combo = 0;
            lastHurtTime = p.hurtTime;

            // horizontal speed in blocks per second (smoothed)
            double inst = Math.hypot(p.getX() - lastX, p.getZ() - lastZ) * 20.0;
            lastX = p.getX();
            lastZ = p.getZ();
            speed += (inst - speed) * 0.25;
            trip += inst / 20.0;

            TotemPops.tick(mc);
            for (HudModule m : MODULES) m.tick(mc);
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (BetaAccess.isAuthorized() && world.isClientSide() && entity instanceof LivingEntity) {
                combo++;
                hits++;
                TotemPops.setTarget(entity);
                Vec3 eye = player.getEyePosition();
                AABB box = entity.getBoundingBox();
                Vec3 closest = new Vec3(
                        Mth.clamp(eye.x, box.minX, box.maxX),
                        Mth.clamp(eye.y, box.minY, box.maxY),
                        Mth.clamp(eye.z, box.minZ, box.maxZ));
                lastReach = eye.distanceTo(closest);
            }
            return InteractionResult.PASS;
        });
    }

    private static void registerModules() {
        // left column
        MODULES.add(new TextModule("fps", "FPS", 4, 4,
                mc -> mc.getFps() + " FPS",
                mc -> mc.getFps() >= 120 ? 0xFF55FF55 : mc.getFps() >= 60 ? 0xFFFFFF55 : 0xFFFF5555));
        MODULES.add(new TextModule("ping", "Ping", 4, 20,
                mc -> {
                    int p = ping(mc);
                    return p < 0 ? "Ping: --" : "Ping: " + p + " ms";
                },
                mc -> {
                    int p = ping(mc);
                    if (p < 0) return 0xFFAAAAAA;
                    return p < 60 ? 0xFF55FF55 : p < 120 ? 0xFFFFFF55 : 0xFFFF5555;
                }));
        MODULES.add(new TextModule("cps", "CPS", 4, 36,
                mc -> "CPS: " + CpsTracker.left() + " | " + CpsTracker.right()));
        MODULES.add(new TextModule("coords", "Coordinates", 4, 52,
                mc -> mc.player == null ? "XYZ" : String.format(Locale.ROOT, "XYZ: %d %d %d",
                        (int) Math.floor(mc.player.getX()), (int) Math.floor(mc.player.getY()), (int) Math.floor(mc.player.getZ()))));
        MODULES.add(new TextModule("direction", "Direction", 4, 68,
                mc -> mc.player == null ? "Facing" : "Facing: " + capitalize(mc.player.getDirection().getSerializedName())));
        MODULES.add(new TextModule("speed", "Speed", 4, 84,
                mc -> String.format(Locale.ROOT, "Speed: %.1f b/s", speed)));

        // second column
        MODULES.add(new TextModule("combo", "Combo", 100, 4,
                mc -> "Combo: " + combo));
        MODULES.add(new TextModule("reach", "Reach", 100, 20,
                mc -> lastReach <= 0 ? "Reach: --" : String.format(Locale.ROOT, "Reach: %.2f", lastReach)));
        MODULES.add(new TextModule("status", "Movement Status", 100, 132,
                mc -> {
                    if (mc.player == null) return "Status";
                    if (mc.player.isShiftKeyDown()) return "Sneaking";
                    if (mc.player.isSprinting()) return "Sprinting";
                    return "Walking";
                }));
        MODULES.add(new TextModule("clock", "Clock", 100, 164,
                mc -> LocalTime.now().format(CLOCK)));
        MODULES.add(new TextModule("memory", "Memory", 100, 180,
                mc -> {
                    Runtime r = Runtime.getRuntime();
                    long used = (r.totalMemory() - r.freeMemory()) / 1048576L;
                    long max = r.maxMemory() / 1048576L;
                    return "Mem: " + used + "/" + max + " MB";
                }));

        // rich modules
        MODULES.add(new KeystrokesModule(4, 104));
        MODULES.add(new ArmorModule(200, 4));
        MODULES.add(new PotionModule(200, 176));
        MODULES.add(new TargetModule(200, 126));
        MODULES.add(new ItemCountModule(200, 100));

        // crystal PvP extras
        MODULES.add(new TextModule("health", "Health", 100, 36,
                mc -> {
                    if (mc.player == null) return "HP";
                    float abs = mc.player.getAbsorptionAmount();
                    String s = String.format(Locale.ROOT, "HP: %.1f", mc.player.getHealth());
                    return abs > 0f ? s + String.format(Locale.ROOT, " +%.1f", abs) : s;
                },
                mc -> {
                    if (mc.player == null) return 0xFFFFFFFF;
                    float hp = mc.player.getHealth();
                    return hp <= 7f ? 0xFFFF5555 : hp <= 14f ? 0xFFFFFF55 : 0xFF55FF55;
                }));
        MODULES.add(new TextModule("distance", "Target Distance", 100, 52,
                mc -> {
                    var t = TotemPops.target();
                    return mc.player == null || t == null
                            ? "Dist: --" : String.format(Locale.ROOT, "Dist: %.1f", mc.player.distanceTo(t));
                }));
        MODULES.add(new TextModule("pops", "Totem Pops", 100, 68,
                mc -> {
                    String s = "Pops: " + TotemPops.pops(mc.player);
                    var t = TotemPops.target();
                    return t == null ? s : s + " | " + t.getName().getString() + ": " + TotemPops.pops(t);
                }));
        MODULES.add(new ToggleSprintModule(100, 148));

        // ranged weapons and food
        MODULES.add(new BowModule(100, 84,
                HudManager::bowCharge,
                mc -> {
                    float c = bowCharge(mc);
                    if (c < 0f) return 0xFFAAAAAA;        // not drawing (ready / empty)
                    if (c >= 1f) return 0xFF55FF55;       // full draw or loaded
                    // red -> yellow -> green as the draw builds
                    return c < 0.5f ? lerpColor(0xFFFF5555, 0xFFFFFF55, c * 2f)
                            : lerpColor(0xFFFFFF55, 0xFF55FF55, (c - 0.5f) * 2f);
                },
                mc -> rangedStack(mc) != null));
        MODULES.add(new TextModule("arrows", "Arrows", 100, 100,
                mc -> {
                    if (mc.player == null || rangedStack(mc) == null) return "";
                    return "Arrows: " + countArrows(mc);
                },
                mc -> countArrows(mc) == 0 ? 0xFFFF5555 : 0xFFFFFFFF));
        MODULES.add(new TextModule("hunger", "Hunger", 100, 116,
                mc -> mc.player == null ? "Food" : String.format(Locale.ROOT, "Food: %d | Sat: %.1f",
                        mc.player.getFoodData().getFoodLevel(), mc.player.getFoodData().getSaturationLevel())));

        // more info
        MODULES.add(new TextModule("session", "Session Timer", 4, 124,
                mc -> {
                    long sec = (System.currentTimeMillis() - SESSION_START) / 1000L;
                    return String.format(Locale.ROOT, "Playtime: %d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60);
                }));
        MODULES.add(new SpotifyModule(4, 188));
        MODULES.add(new TextModule("date", "Date", 4, 140,
                mc -> java.time.LocalDate.now().format(DATE)));

        // more combat
        MODULES.add(new TextModule("hits", "Hit Counter", 100, 196,
                mc -> "Hits: " + hits));
        MODULES.add(new TextModule("cooldown", "Attack Cooldown", 100, 212,
                mc -> mc.player == null ? "Cooldown"
                        : "Cooldown: " + Math.round(Math.min(1f, mc.player.getAttackStrengthScale(0f)) * 100f) + "%",
                mc -> mc.player != null && mc.player.getAttackStrengthScale(0f) >= 1f ? 0xFF55FF55 : 0xFFFFAA00));

        // more gear
        MODULES.add(new TextModule("held", "Held Durability", 200, 196,
                mc -> {
                    if (mc.player == null) return "";
                    ItemStack st = mc.player.getMainHandItem();
                    if (st.isEmpty() || !st.isDamageableItem()) return "";
                    return "Held: " + (st.getMaxDamage() - st.getDamageValue()) + "/" + st.getMaxDamage();
                },
                mc -> {
                    if (mc.player == null) return 0xFFFFFFFF;
                    ItemStack st = mc.player.getMainHandItem();
                    if (st.isEmpty() || !st.isDamageableItem()) return 0xFFFFFFFF;
                    float left = (st.getMaxDamage() - st.getDamageValue()) / (float) st.getMaxDamage();
                    return left <= 0.15f ? 0xFFFF5555 : left <= 0.4f ? 0xFFFFFF55 : 0xFF55FF55;
                }));
        MODULES.add(new TextModule("totems", "Totem Count", 200, 212,
                mc -> mc.player == null ? "Totems" : "Totems: " + countItem(mc, Items.TOTEM_OF_UNDYING),
                mc -> mc.player != null && countItem(mc, Items.TOTEM_OF_UNDYING) == 0 ? 0xFFFF5555 : 0xFFFFFFFF));

        // more movement
        MODULES.add(new TextModule("look", "Yaw / Pitch", 4, 156,
                mc -> mc.player == null ? "Look" : String.format(Locale.ROOT, "Yaw %.0f  Pitch %.0f",
                        Mth.wrapDegrees(mc.player.getYRot()), mc.player.getXRot())));
        MODULES.add(new TextModule("trip", "Distance Travelled", 4, 172,
                mc -> String.format(Locale.ROOT, "Travelled: %.0f m", trip)));

        // keep the default screen tidy: only the essentials start on, the rest are one click away
        for (HudModule m : MODULES) {
            switch (m.id) {
                case "bow", "spotify", "session", "date", "hits", "cooldown", "held", "totems", "look", "trip", "coords", "direction", "speed", "memory", "clock", "status", "distance", "pops", "hunger" -> {
                    m.enabled = false;
                    m.defaultEnabled = false;
                }
                default -> { }
            }
        }
    }

    // ------------------------------------------------------------------ bow / crossbow

    /** The bow or crossbow in your main hand (or offhand), or null. */
    private static ItemStack rangedStack(Minecraft mc) {
        if (mc.player == null) return null;
        ItemStack main = mc.player.getMainHandItem();
        if (main.is(Items.BOW) || main.is(Items.CROSSBOW)) return main;
        ItemStack off = mc.player.getOffhandItem();
        if (off.is(Items.BOW) || off.is(Items.CROSSBOW)) return off;
        return null;
    }

    /** 0..1 while drawing/charging, 1 when a crossbow is loaded, -1 when idle or not holding one. */
    private static float bowCharge(Minecraft mc) {
        ItemStack stack = rangedStack(mc);
        if (stack == null) return -1f;
        var p = mc.player;
        boolean using = p.isUsingItem() && p.getUseItem().is(stack.getItem());
        if (stack.is(Items.CROSSBOW)) {
            var loaded = stack.get(DataComponents.CHARGED_PROJECTILES);
            if (loaded != null && !loaded.isEmpty()) return 1f;
            if (!using) return -1f;
            int pull = Math.max(1, stack.getUseDuration(p) - 3);
            return Math.min(1f, p.getTicksUsingItem() / (float) pull);
        }
        return using ? Math.min(1f, p.getTicksUsingItem() / 20f) : -1f;
    }

    private static int lerpColor(int a, int b, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int out = 0xFF000000;
        for (int s = 0; s <= 16; s += 8) {
            int ca = (a >>> s) & 255;
            int cb = (b >>> s) & 255;
            out |= (Math.round(ca + (cb - ca) * k) & 255) << s;
        }
        return out;
    }

    private static int countItem(Minecraft mc, net.minecraft.world.item.Item item) {
        if (mc.player == null) return 0;
        var inv = mc.player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(item)) n += st.getCount();
        }
        return n;
    }

    private static int countArrows(Minecraft mc) {
        if (mc.player == null) return 0;
        var inv = mc.player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ItemTags.ARROWS)) n += s.getCount();
        }
        return n;
    }

    private static void categorize() {
        for (HudModule m : MODULES) {
            m.category = switch (m.id) {
                case "fps", "ping", "coords", "direction", "clock", "memory", "session", "date", "spotify" -> HudModule.Category.INFO;
                case "armor", "potions", "items", "hunger", "bow", "arrows", "held", "totems" -> HudModule.Category.GEAR;
                case "keystrokes", "speed", "status", "togglesprint", "look", "trip" -> HudModule.Category.MOVEMENT;
                default -> HudModule.Category.COMBAT; // cps, combo, reach, health, target, distance, pops, hits, cooldown
            };
        }
    }

    private static int ping(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null || mc.isLocalServer()) return -1;
        PlayerInfo entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return entry == null ? -1 : entry.getLatency();
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static void resetAll() {
        for (HudModule m : MODULES) {
            m.resetLayout();
            m.enabled = m.defaultEnabled;
        }
    }
}
