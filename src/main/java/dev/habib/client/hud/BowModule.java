package dev.habib.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.function.ToIntFunction;
import java.util.function.ToDoubleFunction;

/**
 * Bow / crossbow draw indicator with no numbers: a small bar that fills as you draw and is
 * colour coded red -> yellow -> green (solid green = full draw / crossbow loaded).
 */
public class BowModule extends HudModule {
    private static final int BAR_W = 60;
    private static final int BAR_H = 8;
    private static final int PAD = 3;
    private static final int IDLE = 0xFF555555;

    private final ToDoubleFunction<MinecraftClient> charge;   // 0..1, or < 0 when idle
    private final ToIntFunction<MinecraftClient> colorFor;    // colour for a charge value (as 0..100 percent)
    private final java.util.function.Predicate<MinecraftClient> visible;

    public BowModule(int x, int y, ToDoubleFunction<MinecraftClient> charge,
                     ToIntFunction<MinecraftClient> colorFor,
                     java.util.function.Predicate<MinecraftClient> visible) {
        super("bow", "Bow / Crossbow", x, y);
        this.charge = charge;
        this.colorFor = colorFor;
        this.visible = visible;
    }

    @Override
    protected void render(DrawContext ctx, MinecraftClient mc, boolean editing) {
        boolean show = visible.test(mc);
        if (!show && !editing) return; // only appears while holding a bow or crossbow

        float c = show ? (float) charge.applyAsDouble(mc) : 0.5f; // preview half-drawn in the editor
        int fill = c < 0f ? IDLE : colorFor.applyAsInt(mc);
        if (!show) fill = 0xFFFFFF55;

        width = BAR_W + PAD * 2;
        height = BAR_H + PAD * 2;
        box(ctx, 0, 0, width, height, 0x66000000);

        int x1 = PAD, y1 = PAD, x2 = PAD + BAR_W, y2 = PAD + BAR_H;
        ctx.fill(x1, y1, x2, y2, 0xFF222222);                       // empty track
        int w = Math.round(BAR_W * Math.max(0f, Math.min(1f, c)));
        if (w > 0) ctx.fill(x1, y1, x1 + w, y2, fill);              // coloured fill
        // thin outline
        ctx.fill(x1 - 1, y1 - 1, x2 + 1, y1, 0xFF000000);
        ctx.fill(x1 - 1, y2, x2 + 1, y2 + 1, 0xFF000000);
        ctx.fill(x1 - 1, y1, x1, y2, 0xFF000000);
        ctx.fill(x2, y1, x2 + 1, y2, 0xFF000000);
    }
}
