package dev.habib.client.mixin;

import dev.habib.client.util.SwingAnimator;
import dev.habib.client.util.AnchorOptimizer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remembers whether the latest click was a right-click use or a left-click attack. */
@Mixin(Minecraft.class)
public class MinecraftClientMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void habibclient$markUse(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        SwingAnimator.markUse(AnchorOptimizer.markUse(mc));
    }

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void habibclient$markAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        HitResult target = mc.hitResult;
        SwingAnimator.markAttack(target != null && target.getType() == HitResult.Type.ENTITY);
    }
}
