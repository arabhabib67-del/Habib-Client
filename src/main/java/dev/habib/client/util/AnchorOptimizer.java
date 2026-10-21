package dev.habib.client.util;

import dev.habib.client.config.HabibConfig;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.ArrayList;
import java.util.List;

/** Client-side anchor feedback and replaceable ghost-anchor prediction. */
public final class AnchorOptimizer {
    private static final long PARTICLE_WINDOW_NANOS = 2_000_000_000L;
    private static final double PARTICLE_RADIUS_SQUARED = 8.0 * 8.0;
    private static final int MAX_ZONES = 32;
    private static final List<Zone> ZONES = new ArrayList<>();
    private static final List<PendingFake> PENDING_FAKES = new ArrayList<>();

    private record Zone(Object world, double x, double y, double z, long expiresAt) {}
    private record PendingFake(Object world, int x, int y, int z) {}

    private AnchorOptimizer() {}

    /** Applies ghost anchors after vanilla has handled the click and sent its interaction. */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(AnchorOptimizer::applyPendingFakes);
    }

    /** Records anchor feedback, nearby blast filtering, and eligible ghost-anchor replacements. */
    public static boolean markUse(MinecraftClient mc) {
        if (!HabibConfig.anchorOptimizer || mc.world == null
                || !(mc.crosshairTarget instanceof BlockHitResult hit)) {
            return false;
        }

        var pos = hit.getBlockPos();
        var state = mc.world.getBlockState(pos);
        if (state.getBlock() != Blocks.RESPAWN_ANCHOR) return false;
        int charges = state.get(RespawnAnchorBlock.CHARGES);
        if (charges <= 0 || mc.world.getRegistryKey().equals(World.NETHER)) return true;
        if (charges < RespawnAnchorBlock.MAX_CHARGES && mc.player != null
                && (mc.player.getMainHandStack().isOf(Items.GLOWSTONE)
                || mc.player.getOffHandStack().isOf(Items.GLOWSTONE))) {
            return true;
        }

        // In multiplayer, replace an exploding anchor locally after the click has been
        // processed. Short grass is a clear, replaceable placeholder; the server's next
        // block update replaces it with the authoritative state.
        if (mc.player != null && !mc.player.isSneaking()
                && mc.getNetworkHandler() != null && !mc.isInSingleplayer()) {
            PENDING_FAKES.add(new PendingFake(mc.world, pos.getX(), pos.getY(), pos.getZ()));
        }

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        long now = System.nanoTime();
        prune(now, mc.world);
        if (ZONES.size() >= MAX_ZONES) ZONES.removeFirst();
        ZONES.add(new Zone(mc.world, x, y, z, now + PARTICLE_WINDOW_NANOS));
        return true;
    }

    private static void applyPendingFakes(MinecraftClient mc) {
        if (PENDING_FAKES.isEmpty()) return;
        if (HabibConfig.anchorOptimizer && mc.world != null) {
            for (PendingFake pending : PENDING_FAKES) {
                if (pending.world != mc.world) continue;
                BlockPos pos = new BlockPos(pending.x, pending.y, pending.z);
                if (mc.world.getBlockState(pos).getBlock() == Blocks.RESPAWN_ANCHOR) {
                    mc.world.setBlockState(pos, Blocks.SHORT_GRASS.getDefaultState(), 3);
                }
            }
        }
        PENDING_FAKES.clear();
    }

    /** True when an explosion particle belongs to a recently used respawn anchor. */
    public static boolean near(double x, double y, double z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!HabibConfig.anchorOptimizer || mc.world == null) return false;

        long now = System.nanoTime();
        prune(now, mc.world);
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
