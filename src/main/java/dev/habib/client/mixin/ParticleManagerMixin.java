package dev.habib.client.mixin;

import dev.habib.client.config.HabibConfig;
import dev.habib.client.util.PearlOptimizer;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optionally drops purely visual combat particles (explosions, totem pops, hit effects) to save FPS. */
@Mixin(ParticleManager.class)
public class ParticleManagerMixin {
    // require = 0: if this ever fails to apply, only this option stops working (no crash)
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void habibclient$noExplosions(ParticleEffect effect, double x, double y, double z,
                                          double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
        var type = effect.getType();
        boolean hide = (HabibConfig.noExplosionParticles
                && (type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER))
                || (HabibConfig.noTotemParticles && type == ParticleTypes.TOTEM_OF_UNDYING)
                || (HabibConfig.pearlOptimizer && type == ParticleTypes.PORTAL && PearlOptimizer.near(x, y, z))
                || (HabibConfig.noHitParticles
                && (type == ParticleTypes.CRIT || type == ParticleTypes.ENCHANTED_HIT
                || type == ParticleTypes.DAMAGE_INDICATOR));
        if (hide) cir.setReturnValue(null);
    }
}
