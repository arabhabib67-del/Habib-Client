package dev.habib.client.util;

import dev.habib.client.config.HabibConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

/**
 * Ender pearl optimizer: hides the purple portal particles a thrown pearl leaves behind and the burst when
 * it lands, which is a big source of particle lag in pearl fights. It is purely visual: pearls fly, land and
 * teleport exactly as before, and ordinary portal particles (nether portals, endermen, ...) are left alone
 * because only particles close to a pearl or a teleport are hidden.
 */
public final class PearlOptimizer {
    /** Long enough (1 second) that landing particles still get hidden when they arrive late on a high-ping connection. */
    private static final int ZONE_TICKS = 20;
    private static final double RADIUS_SQ = 5.0 * 5.0;
    private static final double TELEPORT_JUMP_SQ = 3.0 * 3.0;
    private static final int MAX_ZONES = 512;

    /** x, y, z, expiry tick. */
    private static final List<double[]> ZONES = new ArrayList<>();
    private static int tick;
    private static double lastX, lastY, lastZ;
    private static boolean havePos;

    private PearlOptimizer() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(PearlOptimizer::onTick);
    }

    private static void onTick(MinecraftClient mc) {
        tick++;
        if (!HabibConfig.pearlOptimizer || mc.world == null || mc.player == null) {
            ZONES.clear();
            havePos = false;
            return;
        }
        ZONES.removeIf(z -> z[3] < tick);

        // every pearl in flight marks the area around it (covers its trail and where it lands)
        for (Entity e : mc.world.getEntities()) {
            if (e.getType() == EntityType.ENDER_PEARL) addZone(e.getX(), e.getY(), e.getZ());
        }

        // a sudden jump of the player is a pearl teleport: cover both the departure and arrival points
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        if (havePos) {
            double dx = px - lastX, dy = py - lastY, dz = pz - lastZ;
            if (dx * dx + dy * dy + dz * dz > TELEPORT_JUMP_SQ) {
                addZone(lastX, lastY, lastZ);
                addZone(px, py, pz);
            }
        }
        lastX = px;
        lastY = py;
        lastZ = pz;
        havePos = true;
    }

    private static void addZone(double x, double y, double z) {
        if (ZONES.size() >= MAX_ZONES) return;
        // a pearl moves under 2 blocks a tick, so skip a zone if the newest one is already right here
        if (!ZONES.isEmpty()) {
            double[] last = ZONES.get(ZONES.size() - 1);
            double dx = x - last[0], dy = y - last[1], dz = z - last[2];
            if (dx * dx + dy * dy + dz * dz < 0.25 && last[3] >= tick + ZONE_TICKS - 1) return;
        }
        ZONES.add(new double[]{x, y, z, tick + ZONE_TICKS});
    }

    /** True when a portal particle at this position belongs to a pearl or a pearl teleport. */
    public static boolean near(double x, double y, double z) {
        for (int i = 0; i < ZONES.size(); i++) {
            double[] zn = ZONES.get(i);
            double dx = x - zn[0], dy = y - zn[1], dz = z - zn[2];
            if (dx * dx + dy * dy + dz * dz <= RADIUS_SQ) return true;
        }
        return false;
    }
}
