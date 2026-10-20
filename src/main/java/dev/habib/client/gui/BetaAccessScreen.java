package dev.habib.client.gui;

import dev.habib.client.beta.BetaAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Login panel shown until this Minecraft account is approved for the beta. */
public final class BetaAccessScreen extends Screen {
    private static final int TEXT = 0xFFF2F4F8;
    private static final int MUTED = 0xFFBBC6D3;
    private static final int ACCENT = 0xFFFF6A2B;
    private static final int PANEL = 0xEC1A1E27;

    private final Screen returnTo;
    private TextFieldWidget password;
    private String status = "Enter the password assigned to this account.";
    private int statusColor = MUTED;
    private boolean checking;
    private int panelX, panelY, panelW, panelH;

    public BetaAccessScreen(Screen returnTo) {
        super(Text.literal("Habib Client Beta Access"));
        this.returnTo = returnTo;
    }

    @Override
    protected void init() {
        panelW = Math.min(380, width - 24);
        panelH = 232;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        password = new TextFieldWidget(textRenderer, panelX + 18, panelY + 108, panelW - 36, 24,
                Text.literal("Beta password"));
        password.setMaxLength(128);
        password.setTextPredicate(s -> s.length() <= 128);
        password.setDrawsBackground(false);
        password.setTextShadow(true);
        password.addFormatter((s, firstCharacterIndex) -> Text.literal("•".repeat(s.length())).asOrderedText());
        addSelectableChild(password);
        setInitialFocus(password);

        if (!BetaAccess.isConfigured()) {
            status = BetaAccess.configurationMessage();
            statusColor = 0xFFFF9B91;
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(0, 0, width, height, 0x6806090F);
        rounded(ctx, panelX - 2, panelY + 3, panelW + 4, panelH + 2, 18, 0x50000000);
        rounded(ctx, panelX - 1, panelY - 1, panelW + 2, panelH + 2, 18, 0x40FFFFFF);
        rounded(ctx, panelX, panelY, panelW, panelH, 17, PANEL);
        ctx.fill(panelX + 16, panelY + 1, panelX + panelW - 16, panelY + 2, 0x50FFFFFF);

        rounded(ctx, panelX + 18, panelY + 18, 22, 22, 7, ACCENT);
        drawCentered(ctx, "H", panelX + 29, panelY + 24, 0xFFFFFFFF);
        draw(ctx, "HABIB CLIENT  /  BETA ACCESS", panelX + 50, panelY + 21, TEXT);
        draw(ctx, "This account needs approval to use the beta.", panelX + 18, panelY + 51, MUTED);

        draw(ctx, "ACCOUNT  " + BetaAccess.currentUsername(), panelX + 18, panelY + 73, TEXT);
        String uuid = BetaAccess.currentUserId();
        draw(ctx, "UUID  " + (uuid.isBlank() ? "Account ID unavailable" : uuid), panelX + 18, panelY + 87, MUTED);
        drawButton(ctx, panelX + panelW - 76, panelY + 80, 58, 19, "Copy ID",
                inside(mouseX, mouseY, panelX + panelW - 76, panelY + 80, 58, 19), 0x30343E4B);

        draw(ctx, "PASSWORD", panelX + 18, panelY + 101, MUTED);
        rounded(ctx, panelX + 16, panelY + 106, panelW - 32, 28, 9, 0x55FFFFFF);
        rounded(ctx, panelX + 17, panelY + 107, panelW - 34, 26, 8, 0xB8090C12);
        password.setX(panelX + 24);
        password.setY(panelY + 112);
        password.setWidth(panelW - 48);
        password.setHeight(18);
        password.render(ctx, mouseX, mouseY, delta);

        draw(ctx, status, panelX + 18, panelY + 143, statusColor);
        boolean unlockOver = inside(mouseX, mouseY, panelX + 16, panelY + 163, panelW - 32, 26);
        drawButton(ctx, panelX + 16, panelY + 163, panelW - 32, 26,
                checking ? "Checking..." : "Unlock client", unlockOver, ACCENT);
        drawCentered(ctx, "Your password is checked online and isn't saved here.",
                panelX + panelW / 2, panelY + 202, 0xFFABB6C4);
    }

    private void tryUnlock() {
        if (checking) return;
        if (!BetaAccess.isConfigured()) {
            status = BetaAccess.configurationMessage();
            statusColor = 0xFFFF9B91;
            return;
        }
        String value = password.getText();
        password.setText("");
        checking = true;
        status = "Checking your account and password...";
        statusColor = MUTED;
        BetaAccess.authenticate(value, result -> {
            checking = false;
            status = result.message();
            statusColor = result.approved() ? 0xFF8CE5A2 : 0xFFFF9B91;
            if (result.approved()) MinecraftClient.getInstance().setScreen(returnTo);
        });
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0 && inside(click.x(), click.y(), panelX + panelW - 76, panelY + 80, 58, 19)) {
            String uuid = BetaAccess.currentUserId();
            if (!uuid.isBlank()) {
                MinecraftClient.getInstance().keyboard.setClipboard(uuid);
                status = "Account UUID copied.";
                statusColor = 0xFF8CE5A2;
            }
            return true;
        }
        if (click.button() == 0 && inside(click.x(), click.y(), panelX + 16, panelY + 163, panelW - 32, 26)) {
            tryUnlock();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput key) {
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
    public boolean shouldPause() {
        return false;
    }

    private void draw(DrawContext ctx, String value, int x, int y, int color) {
        ctx.drawText(textRenderer, textRenderer.trimToWidth(value, panelW - 36), x, y, color, true);
    }

    private void drawCentered(DrawContext ctx, String value, int centerX, int y, int color) {
        ctx.drawText(textRenderer, value, centerX - textRenderer.getWidth(value) / 2, y, color, true);
    }

    private void drawButton(DrawContext ctx, int x, int y, int w, int h, String label, boolean over, int base) {
        int fill = over ? mix(base, 0xFFFFFFFF, 0.12f) : base;
        rounded(ctx, x, y, w, h, 9, fill);
        drawCentered(ctx, label, x + w / 2, y + (h - 8) / 2, TEXT);
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

    private static void rounded(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        int radius = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (radius == 0) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }
        ctx.fill(x + radius, y, x + w - radius, y + h, color);
        ctx.fill(x, y + radius, x + w, y + h - radius, color);
        for (int i = 0; i < radius; i++) {
            double dy = radius - i - 0.5;
            int inset = radius - (int) Math.round(Math.sqrt(radius * radius - dy * dy));
            ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            ctx.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }
}
