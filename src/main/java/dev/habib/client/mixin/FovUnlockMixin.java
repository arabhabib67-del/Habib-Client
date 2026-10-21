package dev.habib.client.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

@Mixin(GameOptions.class)
public abstract class FovUnlockMixin {
    @Shadow @Final @Mutable private SimpleOption<Integer> fov;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void habib$allowHigherFov(MinecraftClient client, File optionsFile, CallbackInfo ci) {
        SimpleOption<Integer> original = fov;
        SimpleOptionAccessor access = (SimpleOptionAccessor) (Object) original;
        fov = new SimpleOption<>(
                "options.fov",
                access.habib$getTooltipFactory(),
                (caption, value) -> access.habib$getTextGetter().apply(value),
                new SimpleOption.ValidatingIntSliderCallbacks(30, 170),
                Codec.intRange(30, 170),
                original.getValue(),
                access.habib$getChangeCallback());
    }
}
