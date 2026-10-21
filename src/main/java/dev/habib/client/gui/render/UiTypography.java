package dev.habib.client.gui.render;

import dev.habib.client.mixin.UiFontAccessor;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.font.GlyphProvider;
import net.minecraft.client.font.EffectGlyph;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.util.Identifier;

/** Reuses Minecraft-managed glyph atlases while measuring and drawing every menu control in Inter. */
public final class UiTypography {
    private static final StyleSpriteSource FACE = new StyleSpriteSource.Font(Identifier.of("habibclient", "ui"));
    private static final net.minecraft.text.Style HEADING_STYLE = net.minecraft.text.Style.EMPTY.withFont(
            new StyleSpriteSource.Font(Identifier.of("habibclient", "heading")));
    public static net.minecraft.text.Text heading(String label) {
        return net.minecraft.text.Text.literal(label).setStyle(HEADING_STYLE);
    }
    private UiTypography() {}
    public static TextRenderer create(TextRenderer vanilla) {
        TextRenderer.GlyphsProvider original = ((UiFontAccessor) vanilla).habib$fontProvider();
        return new TextRenderer(new TextRenderer.GlyphsProvider() {
            @Override public GlyphProvider getGlyphs(StyleSpriteSource source) {
                return original.getGlyphs(source.equals(StyleSpriteSource.DEFAULT) ? FACE : source);
            }
            @Override public EffectGlyph getRectangleGlyph() { return original.getRectangleGlyph(); }
        });
    }
}

// Copyright (c) 2026 Bloko. Licensed under the MIT License.
