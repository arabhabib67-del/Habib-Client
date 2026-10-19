package dev.habib.client.util;

import dev.habib.client.config.HabibConfig;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.ActionResult;

/**
 * Client-side crystal break prediction: the instant you hit an end crystal it disappears on your
 * screen instead of waiting for the server to confirm. The attack is still sent normally.
 */
public final class CrystalOptimizer {
    private static final double MAX_DIST_SQ = 6.0 * 6.0;

    private CrystalOptimizer() {}

    public static void init() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient() || !HabibConfig.crystalOptimizer || !(entity instanceof EndCrystalEntity)) {
                return ActionResult.PASS;
            }
            // weakness without strength can reduce the hit to 0 damage: the crystal would survive
            if (player.hasStatusEffect(StatusEffects.WEAKNESS) && !player.hasStatusEffect(StatusEffects.STRENGTH)) {
                return ActionResult.PASS;
            }
            // never predict a break the server would refuse for range
            if (player.squaredDistanceTo(entity) > MAX_DIST_SQ) return ActionResult.PASS;

            // remove after the attack has been sent, so the packet still goes out normally
            MinecraftClient.getInstance().execute(() -> {
                if (!entity.isRemoved()) entity.remove(Entity.RemovalReason.KILLED);
            });
            return ActionResult.PASS;
        });
    }
}
