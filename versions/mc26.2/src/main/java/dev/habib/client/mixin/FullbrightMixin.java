package dev.habib.client.mixin;

import dev.habib.client.config.HabibConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Raises gamma only while Minecraft calculates its lightmap, leaving the user's gamma setting untouched. */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class FullbrightMixin {
    @Redirect(
            method = "extract",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;")
    )
    private Object habib$fullbrightGamma(OptionInstance<?> option) {
        Minecraft client = Minecraft.getInstance();
        if (HabibConfig.fullbright && option == client.options.gamma()) return 16.0;
        return option.get();
    }
}
