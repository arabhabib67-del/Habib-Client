package dev.habib.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Supplier;

/** Smooth only Habib's oversampled font atlas; vanilla text and item textures keep their original filtering. */
@Mixin(FontTexture.class)
public abstract class UiFontTextureMixin extends AbstractTexture {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void habib$linearFont(Supplier<String> name, GlyphRenderTypes layers, boolean colored, CallbackInfo ci) {
        if (name.get().startsWith("habibclient:ui/") || name.get().startsWith("habibclient:heading/")) {
            sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
        }
    }
}

// Copyright (c) 2026 Bloko. Licensed under the MIT License.
