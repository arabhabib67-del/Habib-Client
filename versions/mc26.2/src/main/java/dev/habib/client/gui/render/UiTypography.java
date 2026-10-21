package dev.habib.client.gui.render;

import dev.habib.client.mixin.UiFontAccessor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** Reuses Minecraft-managed glyph atlases while measuring and drawing every menu control in Inter. */
public final class UiTypography {
    private static final FontDescription FACE = new FontDescription.Resource(Identifier.fromNamespaceAndPath("habibclient", "ui"));
    private static final net.minecraft.network.chat.Style HEADING_STYLE = net.minecraft.network.chat.Style.EMPTY.withFont(
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("habibclient", "heading")));
    public static net.minecraft.network.chat.Component heading(String label) {
        return net.minecraft.network.chat.Component.literal(label).withStyle(HEADING_STYLE);
    }
    private UiTypography() {}
    public static Font create(Font vanilla) {
        Font.Provider original = ((UiFontAccessor) vanilla).habib$fontProvider();
        return new Font(new Font.Provider() {
            @Override public GlyphSource glyphs(FontDescription description) {
                return original.glyphs(description.equals(FontDescription.DEFAULT) ? FACE : description);
            }
            @Override public EffectGlyph effect() { return original.effect(); }
        });
    }
}

// Copyright (c) 2026 Bloko. Licensed under the MIT License.
