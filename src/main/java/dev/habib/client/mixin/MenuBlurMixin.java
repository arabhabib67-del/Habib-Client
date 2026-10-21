package dev.habib.client.mixin;

import dev.habib.client.config.HabibConfig;
import dev.habib.client.gui.theme.GlassScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Uses the vanilla blur pipeline only for Habib screens, without changing saved video options. */
@Mixin(GameOptions.class)
public abstract class MenuBlurMixin {
    @Inject(method = "getMenuBackgroundBlurrinessValue", at = @At("HEAD"), cancellable = true)
    private void habib$blurStrength(CallbackInfoReturnable<Integer> result) {
        var client = MinecraftClient.getInstance();
        if (client == null) return;
        var screen = client.currentScreen;
        if (screen instanceof GlassScreen glass) {
            var appearance = HabibConfig.appearance;
            result.setReturnValue(glass.glassBlurAllowed() && appearance.backgroundBlur && !appearance.reducedTransparency
                    ? appearance.blurStrength : 0);
        }
    }
}

// Copyright (c) 2026 Bloko. UI contributions licensed under the MIT License.
