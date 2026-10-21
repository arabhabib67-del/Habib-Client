package dev.habib.client.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

@Mixin(Options.class)
public abstract class FovUnlockMixin {
    @Shadow @Final @Mutable private OptionInstance<Integer> fov;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void habib$allowHigherFov(Minecraft client, File optionsFile, CallbackInfo ci) {
        OptionInstance<Integer> original = fov;
        OptionInstanceAccessor access = (OptionInstanceAccessor) (Object) original;
        fov = new OptionInstance<>(
                "options.fov",
                access.habib$getTooltip(),
                (caption, value) -> access.habib$getCaption().apply(value),
                new OptionInstance.IntRange(30, 170),
                Codec.intRange(30, 170),
                original.get(),
                access.habib$getUpdateListener());
    }
}
