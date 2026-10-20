package dev.habib.client.util;

import dev.habib.client.config.HabibConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/**
 * Consumable optimizer: hides the particles from eating and drinking (food crumbs, potion sips) and from
 * splash / lingering potions bursting, which add a lot of particle lag when several players heal at once.
 * Purely visual: nothing about eating, drinking or potion effects changes. Only particles close to someone
 * who is eating or drinking, or close to a thrown potion, are hidden.
 */
public final class ConsumableOptimizer {
    private static final int EAT_TICKS = 3;
    private static final double EAT_RADIUS_SQ = 2.5 * 2.5;
    /** A full second, so a potion burst that arrives late on a high-ping connection is still hidden. */
    private static final int POTION_TICKS = 20;
    private static final double POTION_RADIUS_SQ = 5.0 * 5.0;
    private static final int MAX_ZONES = 512;

    /** x, y, z, expiry tick, radius squared, kind (0 = eating or drinking, 1 = potion in the air). */
    private static final List<double[]> ZONES = new ArrayList<>();
    private static int tick;

    private ConsumableOptimizer() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(ConsumableOptimizer::onTick);
    }

    private static void onTick(Minecraft mc) {
        tick++;
        if (!HabibConfig.consumableOptimizer || mc.level == null) {
            ZONES.clear();
            return;
        }
        ZONES.removeIf(z -> z[3] < tick);

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.getType() == EntityType.SPLASH_POTION || e.getType() == EntityType.LINGERING_POTION) {
                addZone(e.getX(), e.getY(), e.getZ(), POTION_TICKS, POTION_RADIUS_SQ, 1);
            } else if (e instanceof LivingEntity le && le.isUsingItem() && isEatOrDrink(le.getUseItem())) {
                addZone(le.getX(), le.getEyeY(), le.getZ(), EAT_TICKS, EAT_RADIUS_SQ, 0);
            }
        }
    }

    private static boolean isEatOrDrink(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String action = stack.getUseAnimation().name();
        return action.equals("EAT") || action.equals("DRINK");
    }

    private static void addZone(double x, double y, double z, int ticks, double radiusSq, int kind) {
        if (ZONES.size() >= MAX_ZONES) return;
        // a potion moves under 2 blocks a tick, so skip a zone if the newest one is already right here
        if (!ZONES.isEmpty()) {
            double[] last = ZONES.get(ZONES.size() - 1);
            double dx = x - last[0], dy = y - last[1], dz = z - last[2];
            if (last[5] == kind && dx * dx + dy * dy + dz * dz < 0.25 && last[3] >= tick + ticks - 1) return;
        }
        ZONES.add(new double[]{x, y, z, tick + ticks, radiusSq, kind});
    }

    /** True when this particle is a crumb, sip or potion burst that belongs to a consumable and should be hidden. */
    public static boolean hides(ParticleType<?> type, double x, double y, double z) {
        boolean item = type == ParticleTypes.ITEM;
        boolean burst = type == ParticleTypes.EFFECT || type == ParticleTypes.INSTANT_EFFECT;
        if (!item && !burst) return false;
        for (int i = 0; i < ZONES.size(); i++) {
            double[] zn = ZONES.get(i);
            // eating zones only hide item crumbs; potion zones also hide the splash burst
            if (zn[5] == 0 && !item) continue;
            double dx = x - zn[0], dy = y - zn[1], dz = z - zn[2];
            if (dx * dx + dy * dy + dz * dz <= zn[4]) return true;
        }
        return false;
    }
}
