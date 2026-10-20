package dev.habib.client.hud;

import dev.habib.client.config.HabibConfig;
import dev.habib.client.util.CpsTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** WASD + mouse buttons + space, with CPS on the mouse buttons. */
public class KeystrokesModule extends HudModule {
    /** Text colour of a held key when there is no box to light up. */
    private static final int PRESSED = 0xFFFFFF55;

    public KeystrokesModule(int x, int y) {
        super("keystrokes", "Keystrokes", x, y);
        width = 58;
        height = 72;
    }

    @Override
    protected void render(GuiGraphicsExtractor ctx, Minecraft mc, boolean editing) {
        var o = mc.options;
        key(ctx, mc, 20, 0, 18, 18, "W", o.keyUp.isDown());
        key(ctx, mc, 0, 20, 18, 18, "A", o.keyLeft.isDown());
        key(ctx, mc, 20, 20, 18, 18, "S", o.keyDown.isDown());
        key(ctx, mc, 40, 20, 18, 18, "D", o.keyRight.isDown());
        key(ctx, mc, 0, 40, 28, 18, "L " + CpsTracker.left(), o.keyAttack.isDown());
        key(ctx, mc, 30, 40, 28, 18, "R " + CpsTracker.right(), o.keyUse.isDown());
        key(ctx, mc, 0, 60, 58, 12, "", o.keyJump.isDown());
        // space bar glyph
        ctx.fill(18, 65, 40, 66, o.keyJump.isDown() ? (HabibConfig.hudBackground ? 0xFF000000 : PRESSED) : color);
    }

    private void key(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, int w, int h, String label, boolean down) {
        if (HabibConfig.hudBackground) ctx.fill(x, y, x + w, y + h, down ? 0xAAFFFFFF : 0x66000000);
        if (!label.isEmpty()) {
            int tx = x + w / 2;
            int ty = y + (h - 8) / 2;
            ctx.centeredText(mc.font, label, tx, ty, down ? (HabibConfig.hudBackground ? 0xFF000000 : PRESSED) : color);
        }
    }
}
