package dev.habib.client.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Counts totem pops per player (entity status 35) and remembers who you last fought. */
public final class TotemPops {
    private static final Map<UUID, Integer> POPS = new HashMap<>();
    private static Player target;

    private TotemPops() {}

    /** Called from the network mixin on the client thread. */
    public static void onEntityStatus(Entity entity, byte status) {
        if (status == 35 && entity instanceof Player p) {
            POPS.merge(p.getUUID(), 1, Integer::sum);
            Effects.onPop(p);
        }
    }

    public static void setTarget(Entity e) {
        if (e instanceof Player p) target = p;
    }

    public static Player target() {
        return target;
    }

    public static int pops(Player p) {
        return p == null ? 0 : POPS.getOrDefault(p.getUUID(), 0);
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            POPS.clear();
            target = null;
            return;
        }
        // a dead player starts the next fight at zero pops
        if (target != null && (target.isRemoved() || !target.isAlive())) {
            POPS.remove(target.getUUID());
            target = null;
        }
        if (!mc.player.isAlive()) POPS.remove(mc.player.getUUID());
    }
}
