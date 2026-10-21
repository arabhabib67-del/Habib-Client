package dev.habib.client.gui;

import dev.habib.client.gui.render.GlassRenderer;
import dev.habib.client.gui.theme.*;
import dev.habib.client.config.HabibConfig;
import dev.habib.client.beta.BetaAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Login panel shown until this Minecraft account is approved for the beta. */
public final class BetaAccessScreen extends Screen implements GlassScreen {
    private int TEXT;
    private int MUTED;
    private int ACCENT;
    private int PANEL;

    private final Screen returnTo;
    private EditBox password;
    private String status = "Enter the shared beta password.";
    private int statusColor;
    private float uiScale = 1f;
    private long lastFrame = System.nanoTime();
    private boolean checking;
    private int panelX, panelY, panelW, panelH;

    public BetaAccessScreen(Screen returnTo) {
        super(Component.literal("Habib Client Beta Access"));
        this.returnTo = returnTo;
    }

    @Override
    protected void init() {
        uiScale = Math.max(0.1f, Math.min(1f, Math.min((width - 12f) / 400f, (height - 12f) / 240f)));
        panelW = Math.min(380, Math.round(width / uiScale) - 24);
        panelH = 216;
        panelX = (Math.round(width / uiScale) - panelW) / 2;
        panelY = (Math.round(height / uiScale) - panelH) / 2;

        password = new EditBox(font, panelX + 18, panelY + 85, panelW - 36, 24,
                Component.literal("Beta password"));
        password.setMaxLength(128);
        password.setBordered(false);
        password.setTextShadow(false);
        password.addFormatter((s, firstCharacterIndex) -> Component.literal("•".repeat(s.length())).getVisualOrderText());
        addWidget(password);
        setInitialFocus(password);

        if (!BetaAccess.isConfigured()) {
            status = BetaAccess.configurationMessage();
            statusColor = 0xFFFF9B91;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) extractPanorama(ctx, delta);
        if (glassBlurAllowed() && HabibConfig.appearance.backgroundBlur && !HabibConfig.appearance.reducedTransparency) extractBlurredBackground(ctx);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        ThemePalette palette = ThemeManager.update((now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        TEXT = palette.text; MUTED = palette.muted; ACCENT = palette.accent; PANEL = palette.panel;
        ctx.pose().pushMatrix();
        try {
        ctx.pose().scale(uiScale, uiScale);
        mouseX = Math.round(mouseX / uiScale); mouseY = Math.round(mouseY / uiScale);
        GlassRenderer.panel(ctx, panelX, panelY, panelW, panelH, 1f);

        rounded(ctx, panelX + 18, panelY + 18, 22, 22, 7, ACCENT);
        drawCentered(ctx, "H", panelX + 29, panelY + 24, 0xFFFFFFFF);
        draw(ctx, "HABIB CLIENT  /  BETA ACCESS", panelX + 50, panelY + 21, TEXT);
        draw(ctx, "Enter the shared beta password to unlock the modules.", panelX + 18, panelY + 51, MUTED);

        draw(ctx, "PASSWORD", panelX + 18, panelY + 75, MUTED);
        rounded(ctx, panelX + 16, panelY + 80, panelW - 32, 28, 9, ThemeManager.palette().edge);
        rounded(ctx, panelX + 17, panelY + 81, panelW - 34, 26, 8, ThemeManager.palette().row);
        password.setX(panelX + 24);
        password.setY(panelY + 86);
        password.setWidth(panelW - 48);
        password.setHeight(18);
        password.setTextColor(TEXT);
        password.extractWidgetRenderState(ctx, mouseX, mouseY, delta);

        draw(ctx, status, panelX + 18, panelY + 116, statusColor == 0 ? MUTED : statusColor);
        boolean unlockOver = inside(mouseX, mouseY, panelX + 16, panelY + 138, panelW - 32, 26);
        drawButton(ctx, panelX + 16, panelY + 138, panelW - 32, 26,
                checking ? "Checking..." : "Unlock modules", unlockOver, ACCENT);
        drawCentered(ctx, "Unlocked until you close the game.",
                panelX + panelW / 2, panelY + 178, MUTED);
        } finally { ctx.pose().popMatrix(); }
    }

    private void tryUnlock() {
        if (checking) return;
        if (!BetaAccess.isConfigured()) {
            status = BetaAccess.configurationMessage();
            statusColor = 0xFFFF9B91;
            return;
        }
        String value = password.getValue();
        password.setValue("");
        checking = true;
        status = "Checking password...";
        statusColor = 0;
        BetaAccess.authenticate(value, result -> {
            checking = false;
            status = result.message();
            statusColor = result.approved() ? 0xFF8CE5A2 : 0xFFFF9B91;
            if (result.approved()) Minecraft.getInstance().gui.setScreen(returnTo);
        });
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        click = new MouseButtonEvent(click.x() / uiScale, click.y() / uiScale, click.buttonInfo());
        if (click.button() == 0 && inside(click.x(), click.y(), panelX + 16, panelY + 138, panelW - 32, 26)) {
            tryUnlock();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent key) {
        if (key.key() == GLFW.GLFW_KEY_ESCAPE) return true;
        if (key.key() == GLFW.GLFW_KEY_ENTER || key.key() == GLFW.GLFW_KEY_KP_ENTER) {
            tryUnlock();
            return true;
        }
        return super.keyPressed(key);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void draw(GuiGraphicsExtractor ctx, String value, int x, int y, int color) {
        ctx.text(font, font.plainSubstrByWidth(value, panelW - 36), x, y, color, false);
    }

    private void drawCentered(GuiGraphicsExtractor ctx, String value, int centerX, int y, int color) {
        ctx.text(font, value, centerX - font.width(value) / 2, y, color, true);
    }

    private void drawButton(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                            String label, boolean over, int base) {
        int fill = over ? mix(base, 0xFFFFFFFF, 0.12f) : base;
        rounded(ctx, x, y, w, h, 9, fill);
        drawCentered(ctx, label, x + w / 2, y + (h - 8) / 2, ThemeManager.palette().accentText);
    }

    private static boolean inside(double x, double y, int left, int top, int w, int h) {
        return x >= left && x < left + w && y >= top && y < top + h;
    }

    private static int mix(int first, int second, float amount) {
        int a = (int) (((first >>> 24) & 255) + (((second >>> 24) & 255) - ((first >>> 24) & 255)) * amount);
        int r = (int) (((first >>> 16) & 255) + (((second >>> 16) & 255) - ((first >>> 16) & 255)) * amount);
        int g = (int) (((first >>> 8) & 255) + (((second >>> 8) & 255) - ((first >>> 8) & 255)) * amount);
        int b = (int) ((first & 255) + ((second & 255) - (first & 255)) * amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void rounded(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int color) { GlassRenderer.rounded(ctx, x, y, w, h, r, color); }
}
