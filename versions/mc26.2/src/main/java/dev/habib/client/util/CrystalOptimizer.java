package dev.habib.client.util;

import dev.habib.client.config.HabibConfig;
import dev.habib.client.beta.BetaAccess;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

/**
 * Client-side crystal break prediction: the instant you hit an end crystal it disappears on your
 * screen instead of waiting for the server to confirm. The attack is still sent normally.
 */
public final class CrystalOptimizer {
    private static final double MAX_DIST_SQ = 6.0 * 6.0;

    private CrystalOptimizer() {}

    public static void init() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!BetaAccess.isAuthorized() || !world.isClientSide() || !HabibConfig.crystalOptimizer || !(entity instanceof EndCrystal)) {
                return InteractionResult.PASS;
            }
            // weakness without strength can reduce the hit to 0 damage: the crystal would survive
            if (player.hasEffect(MobEffects.WEAKNESS) && !player.hasEffect(MobEffects.STRENGTH)) {
                return InteractionResult.PASS;
            }
            // never predict a break the server would refuse for range
            if (player.distanceToSqr(entity) > MAX_DIST_SQ) return InteractionResult.PASS;

            // remove after the attack has been sent, so the packet still goes out normally
            Minecraft.getInstance().execute(() -> {
                if (!entity.isRemoved()) entity.remove(Entity.RemovalReason.KILLED);
            });
            return InteractionResult.PASS;
        });
    }
}
