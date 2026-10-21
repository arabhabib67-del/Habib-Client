package dev.habib.client.util;

import dev.habib.client.beta.BetaAccess;
import dev.habib.client.config.HabibConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

/** Local-only feedback and particle reduction for respawn-anchor interactions. */
public final class AnchorOptimizer {
    private static final long PARTICLE_WINDOW_NANOS = 2_000_000_000L;
    private static final double PARTICLE_RADIUS_SQUARED = 8.0 * 8.0;
    private static final int MAX_ZONES = 32;
    private static final List<Zone> ZONES = new ArrayList<>();

    private record Zone(Object world, double x, double y, double z, long expiresAt) {}

    private AnchorOptimizer() {}

    /** Records an anchor interaction for its smooth hand swing and nearby blast particle filtering. */
    public static boolean markUse(Minecraft mc) {
        if (!BetaAccess.isAuthorized() || !HabibConfig.anchorOptimizer || mc.level == null
                || !(mc.hitResult instanceof BlockHitResult hit)) {
            return false;
        }

        var pos = hit.getBlockPos();
        var state = mc.level.getBlockState(pos);
        if (state.getBlock() != Blocks.RESPAWN_ANCHOR) return false;
        int charges = state.getValue(RespawnAnchorBlock.CHARGE);
        if (charges <= 0 || mc.level.dimension().equals(Level.NETHER)) return true;
        if (charges < RespawnAnchorBlock.MAX_CHARGES && mc.player != null
                && (mc.player.getMainHandItem().is(Items.GLOWSTONE)
                || mc.player.getOffhandItem().is(Items.GLOWSTONE))) {
            return true;
        }

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        long now = System.nanoTime();
        prune(now, mc.level);
        if (ZONES.size() >= MAX_ZONES) ZONES.removeFirst();
        ZONES.add(new Zone(mc.level, x, y, z, now + PARTICLE_WINDOW_NANOS));
        return true;
    }

    /** True when an explosion particle belongs to a recently used respawn anchor. */
    public static boolean near(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (!HabibConfig.anchorOptimizer || mc.level == null) return false;

        long now = System.nanoTime();
        prune(now, mc.level);
        for (Zone zone : ZONES) {
            double dx = x - zone.x;
            double dy = y - zone.y;
            double dz = z - zone.z;
            if (dx * dx + dy * dy + dz * dz <= PARTICLE_RADIUS_SQUARED) return true;
        }
        return false;
    }

    private static void prune(long now, Object world) {
        ZONES.removeIf(zone -> zone.world != world || zone.expiresAt < now);
    }
}
