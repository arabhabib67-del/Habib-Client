package dev.habib.client.hud;

import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A single line of text on an optional translucent background (FPS, ping, CPS, coords, ...). */
public class TextModule extends HudModule {
    private final Function<Minecraft, String> text;
    private final ToIntFunction<Minecraft> dynamicColor;

    public TextModule(String id, String label, int x, int y, Function<Minecraft, String> text) {
        this(id, label, x, y, text, null);
    }

    /** dynamicColor may be null; when set it overrides the user colour (e.g. green/yellow/red ping). */
    public TextModule(String id, String label, int x, int y, Function<Minecraft, String> text,
                      ToIntFunction<Minecraft> dynamicColor) {
        super(id, label, x, y);
        this.text = text;
        this.dynamicColor = dynamicColor;
    }

    @Override
    protected void render(GuiGraphicsExtractor ctx, Minecraft mc, boolean editing) {
        String s = text.apply(mc);
        if (s.isEmpty()) {
            if (!editing) return; // contextual modules (bow timer, arrows) draw nothing when not relevant
            s = label;
        }
        width = mc.font.width(s) + 8;
        height = 14;
        int c = dynamicColor != null ? dynamicColor.applyAsInt(mc) : color;
        box(ctx, 0, 0, width, height, 0x66000000);
        ctx.text(mc.font, s, 4, 3, c, true);
    }
}
