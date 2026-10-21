package dev.habib.client.mixin;

import dev.habib.client.config.HabibConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Raises gamma only while Minecraft calculates its lightmap, leaving the user's gamma setting untouched. */
@Mixin(LightmapTextureManager.class)
public abstract class FullbrightMixin {
    @Redirect(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;")
    )
    private Object habib$fullbrightGamma(SimpleOption<?> option) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (HabibConfig.fullbright && option == client.options.getGamma()) return 16.0;
        return option.getValue();
    }
}
