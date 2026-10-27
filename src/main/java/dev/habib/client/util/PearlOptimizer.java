package dev.habib.client.util;

import dev.habib.client.config.HabibConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reduces pearl particle load without changing server-side pearl movement or teleport behavior.
 * Loaded pearls are tracked directly, and recent particle zones are spatially indexed so particle
 * checks stay cheap during busy fights. The longer zone lifetime covers delayed landing effects.
 */
public final class PearlOptimizer {
    /** Keep pearl and teleport areas covered for four seconds, including delayed effects on high ping. */
    private static final int ZONE_TICKS = 80;
    private static final double CELL_SIZE = 8.0;
    private static final double RADIUS_SQ = 5.0 * 5.0;
    private static final double TELEPORT_JUMP_SQ = 3.0 * 3.0;
    private static final int MAX_ZONES = 768;

    private static final Set<Entity> PEARLS = new HashSet<>();
    private static final ArrayDeque<Zone> ZONES = new ArrayDeque<>();
    private static final Map<CellKey, List<Zone>> BUCKETS = new HashMap<>();
    /** Reused for lookups so each particle check does not allocate 27 temporary keys. */
    private static final CellKey LOOKUP_CELL = new CellKey();

    private static int tick;
    private static double lastX, lastY, lastZ;
    private static boolean havePos;
    private static Object trackedWorld;

    private PearlOptimizer() {}

    public static void init() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            syncWorld(world);
            if (entity.getType() == EntityType.ENDER_PEARL) {
                PEARLS.add(entity);
                if (HabibConfig.pearlOptimizer) addZone(entity.getX(), entity.getY(), entity.getZ());
            }
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> PEARLS.remove(entity));
        ClientTickEvents.END_CLIENT_TICK.register(PearlOptimizer::onTick);
    }

    private static void onTick(MinecraftClient mc) {
        tick++;
        if (mc.world == null) {
            syncWorld(null);
            PEARLS.clear();
            havePos = false;
            return;
        }
        syncWorld(mc.world);
        if (!HabibConfig.pearlOptimizer || mc.player == null) {
            clearZones();
            havePos = false;
            return;
        }
        pruneExpiredZones();

        // Track only pearl entities instead of scanning every entity in the world each tick.
        for (Iterator<Entity> it = PEARLS.iterator(); it.hasNext();) {
            Entity pearl = it.next();
            if (pearl.isRemoved()) {
                it.remove();
                continue;
            }
            addZone(pearl.getX(), pearl.getY(), pearl.getZ());
        }

        // A sudden player jump is a pearl teleport: cover departure and arrival points.
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

    private static void syncWorld(Object world) {
        if (trackedWorld == world) return;
        trackedWorld = world;
        clearZones();
        PEARLS.clear();
        havePos = false;
    }

    private static void addZone(double x, double y, double z) {
        if (!ZONES.isEmpty()) {
            Zone last = ZONES.peekLast();
            double dx = x - last.x, dy = y - last.y, dz = z - last.z;
            if (dx * dx + dy * dy + dz * dz < 0.25 && last.expiresAt >= tick + ZONE_TICKS - 1) return;
        }
        while (ZONES.size() >= MAX_ZONES) removeOldestZone();

        CellKey cell = new CellKey(cell(x), cell(y), cell(z));
        Zone zone = new Zone(x, y, z, tick + ZONE_TICKS, cell);
        ZONES.addLast(zone);
        BUCKETS.computeIfAbsent(cell, ignored -> new ArrayList<>()).add(zone);
    }

    private static void pruneExpiredZones() {
        while (!ZONES.isEmpty() && ZONES.peekFirst().expiresAt < tick) removeOldestZone();
    }

    private static void removeOldestZone() {
        Zone zone = ZONES.removeFirst();
        List<Zone> bucket = BUCKETS.get(zone.cell);
        if (bucket != null) {
            bucket.remove(zone);
            if (bucket.isEmpty()) BUCKETS.remove(zone.cell);
        }
    }

    private static void clearZones() {
        ZONES.clear();
        BUCKETS.clear();
    }

    private static int cell(double coordinate) {
        return (int) Math.floor(coordinate / CELL_SIZE);
    }

    /** True when a portal particle at this position belongs to a pearl or a pearl teleport. */
    public static boolean near(double x, double y, double z) {
        int cx = cell(x), cy = cell(y), cz = cell(z);
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                for (int oz = -1; oz <= 1; oz++) {
                    LOOKUP_CELL.set(cx + ox, cy + oy, cz + oz);
                    List<Zone> bucket = BUCKETS.get(LOOKUP_CELL);
                    if (bucket == null) continue;
                    for (Zone zone : bucket) {
                        double dx = x - zone.x, dy = y - zone.y, dz = z - zone.z;
                        if (dx * dx + dy * dy + dz * dz <= RADIUS_SQ) return true;
                    }
                }
            }
        }
        return false;
    }

    private record Zone(double x, double y, double z, int expiresAt, CellKey cell) {}

    /** Hash-map key with a reusable lookup instance for allocation-free particle queries. */
    private static final class CellKey {
        private int x;
        private int y;
        private int z;

        private CellKey() {}

        private CellKey(int x, int y, int z) {
            set(x, y, z);
        }

        private void set(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof CellKey key && x == key.x && y == key.y && z == key.z;
        }

        @Override
        public int hashCode() {
            return (x * 31 + y) * 31 + z;
        }
    }
}
