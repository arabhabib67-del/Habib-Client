package dev.habib.client.mixin;

import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only bridge for a menu-local font renderer; never replaces Minecraft's global font. */
@Mixin(Font.class)
public interface UiFontAccessor {
    @Accessor("provider")
    Font.Provider habib$fontProvider();
}

// Copyright (c) 2026 Bloko. Licensed under the MIT License.
