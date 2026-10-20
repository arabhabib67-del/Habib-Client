package dev.habib.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.habib.client.util.SwingAnimator;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class HeldItemRendererMixin {
    /** Swaps the vanilla "swing outward" for the selected smooth animation (swords and right-click ender pearl throws). */
    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true)
    private void habibclient$smoothSwing(PoseStack matrices, HumanoidArm arm, float swingProgress, CallbackInfo ci) {
        if (SwingAnimator.apply(matrices, arm, swingProgress)) {
            ci.cancel();
        }
    }
}
