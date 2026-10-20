package dev.habib.client.hud;

import dev.habib.client.config.HabibConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Base class for every draggable HUD element. */
public abstract class HudModule {
    public static final int[] COLOR_PRESETS = {
            0xFFFFFFFF, 0xFF55FF55, 0xFF55FFFF, 0xFFFFFF55, 0xFFFF5555, 0xFFFF55FF, 0xFFFFAA00
    };

    public final String id;
    public final String label;
    public final int defaultX;
    public final int defaultY;

    public boolean enabled = true;
    /** Whether this module is on for a fresh install / after "Reset layout". */
    public boolean defaultEnabled = true;
    public int x;
    public int y;
    public float scale = 1f;
    public int color = 0xFFFFFFFF;

    public enum Category {
        INFO("Info"), COMBAT("Combat"), GEAR("Gear"), MOVEMENT("Movement");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    public Category category = Category.INFO;

    /** Unscaled size, updated every render so the editor can hit-test accurately. */
    protected int width = 60;
    protected int height = 14;

    protected HudModule(String id, String label, int x, int y) {
        this.id = id;
        this.label = label;
        this.defaultX = x;
        this.defaultY = y;
        this.x = x;
        this.y = y;
    }

    public void tick(Minecraft mc) {}

    public final void draw(GuiGraphicsExtractor ctx, Minecraft mc, boolean editing) {
        var m = ctx.pose();
        m.pushMatrix();
        m.translate((float) x, (float) y);
        m.scale(scale, scale);
        render(ctx, mc, editing);
        m.popMatrix();
    }

    /** Translucent box behind a module; skipped when the user turned boxes off. */
    protected static void box(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
        if (HabibConfig.hudBackground) ctx.fill(x1, y1, x2, y2, color);
    }

    protected abstract void render(GuiGraphicsExtractor ctx, Minecraft mc, boolean editing);

    public int scaledWidth() {
        return Math.round(width * scale);
    }

    public int scaledHeight() {
        return Math.round(height * scale);
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx <= x + scaledWidth() && my >= y && my <= y + scaledHeight();
    }

    public void cycleColor() {
        int idx = 0;
        for (int i = 0; i < COLOR_PRESETS.length; i++) {
            if (COLOR_PRESETS[i] == color) {
                idx = i;
                break;
            }
        }
        color = COLOR_PRESETS[(idx + 1) % COLOR_PRESETS.length];
    }

    public void resetLayout() {
        x = defaultX;
        y = defaultY;
        scale = 1f;
        color = 0xFFFFFFFF;
    }
}
