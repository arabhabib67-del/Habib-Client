package dev.habib.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.function.Function;
import java.util.function.ToIntFunction;

/** A single line of text on an optional translucent background (FPS, ping, CPS, coords, ...). */
public class TextModule extends HudModule {
    private final Function<MinecraftClient, String> text;
    private final ToIntFunction<MinecraftClient> dynamicColor;

    public TextModule(String id, String label, int x, int y, Function<MinecraftClient, String> text) {
        this(id, label, x, y, text, null);
    }

    /** dynamicColor may be null; when set it overrides the user colour (e.g. green/yellow/red ping). */
    public TextModule(String id, String label, int x, int y, Function<MinecraftClient, String> text,
                      ToIntFunction<MinecraftClient> dynamicColor) {
        super(id, label, x, y);
        this.text = text;
        this.dynamicColor = dynamicColor;
    }

    @Override
    protected void render(DrawContext ctx, MinecraftClient mc, boolean editing) {
        String s = text.apply(mc);
        if (s.isEmpty()) {
            if (!editing) return; // contextual modules (bow timer, arrows) draw nothing when not relevant
            s = label;
        }
        width = mc.textRenderer.getWidth(s) + 8;
        height = 14;
        int c = dynamicColor != null ? dynamicColor.applyAsInt(mc) : color;
        box(ctx, 0, 0, width, height, 0x66000000);
        ctx.drawText(mc.textRenderer, s, 4, 3, c, true);
    }
}
