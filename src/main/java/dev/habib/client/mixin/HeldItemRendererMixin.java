package dev.habib.client.mixin;

import dev.habib.client.util.SwingAnimator;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
    /** Swaps the vanilla "swing outward" for the selected smooth animation (swords and right-click ender pearl throws). */
    @Inject(method = "applySwingOffset", at = @At("HEAD"), cancellable = true)
    private void habibclient$smoothSwing(MatrixStack matrices, Arm arm, float swingProgress, CallbackInfo ci) {
        if (SwingAnimator.apply(matrices, arm, swingProgress)) {
            ci.cancel();
        }
    }
}
