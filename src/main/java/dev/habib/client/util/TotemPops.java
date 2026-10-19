package dev.habib.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Counts totem pops per player (entity status 35) and remembers who you last fought. */
public final class TotemPops {
    private static final Map<UUID, Integer> POPS = new HashMap<>();
    private static PlayerEntity target;

    private TotemPops() {}

    /** Called from the network mixin on the client thread. */
    public static void onEntityStatus(Entity entity, byte status) {
        if (status == 35 && entity instanceof PlayerEntity p) {
            POPS.merge(p.getUuid(), 1, Integer::sum);
            Effects.onPop(p);
        }
    }

    public static void setTarget(Entity e) {
        if (e instanceof PlayerEntity p) target = p;
    }

    public static PlayerEntity target() {
        return target;
    }

    public static int pops(PlayerEntity p) {
        return p == null ? 0 : POPS.getOrDefault(p.getUuid(), 0);
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            POPS.clear();
            target = null;
            return;
        }
        // a dead player starts the next fight at zero pops
        if (target != null && (target.isRemoved() || !target.isAlive())) {
            POPS.remove(target.getUuid());
            target = null;
        }
        if (!mc.player.isAlive()) POPS.remove(mc.player.getUuid());
    }
}
