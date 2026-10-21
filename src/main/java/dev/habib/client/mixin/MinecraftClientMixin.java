package dev.habib.client.mixin;

import dev.habib.client.util.SwingAnimator;
import dev.habib.client.util.AnchorOptimizer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remembers whether the latest click was a right-click use or a left-click attack. */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "doItemUse", at = @At("HEAD"))
    private void habibclient$markUse(CallbackInfo ci) {
        MinecraftClient mc = (MinecraftClient) (Object) this;
        SwingAnimator.markUse(AnchorOptimizer.markUse(mc));
    }

    @Inject(method = "doAttack", at = @At("HEAD"))
    private void habibclient$markAttack(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient mc = (MinecraftClient) (Object) this;
        HitResult target = mc.crosshairTarget;
        SwingAnimator.markAttack(target != null && target.getType() == HitResult.Type.ENTITY);
    }
}
