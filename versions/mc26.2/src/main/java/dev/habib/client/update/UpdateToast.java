package dev.habib.client.update;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Top-right update notification styled to match modern Minecraft update toasts. */
final class UpdateToast implements Toast {
    private static final int WIDTH = 190;
    private static final int HEIGHT = 48;
    private static final int TEXT_X = 36;
    private static final long DISPLAY_TIME_MS = 6000L;

    private final Component title;
    private final Component description;
    private long startTime;
    private boolean justAdded = true;
    private Toast.Visibility visibility = Toast.Visibility.SHOW;

    private UpdateToast(String title, String description) {
        this.title = Component.literal(title).withStyle(style -> style.withBold(true));
        this.description = Component.literal(description);
    }

    static void show(Minecraft client, String title, String description) {
        client.getToastManager().addToast(new UpdateToast(title, description));
    }

    @Override
    public Toast.Visibility getWantedVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager manager, long time) {
        if (justAdded) {
            startTime = time;
            justAdded = false;
        }
        double duration = DISPLAY_TIME_MS * manager.getNotificationDisplayTimeMultiplier();
        visibility = time - startTime < duration ? Toast.Visibility.SHOW : Toast.Visibility.HIDE;
    }

    @Override
    public void render(GuiGraphics context, Font textRenderer, long time) {
        roundedRect(context, 0, 0, WIDTH, HEIGHT, 8, 0xFF74747A);
        roundedRect(context, 1, 1, WIDTH - 2, HEIGHT - 2, 7, 0xE62B2B2F);

        drawUpdateIcon(context, 19, HEIGHT / 2);

        String clippedTitle = textRenderer.plainSubstrByWidth(title.getString(), WIDTH - TEXT_X - 9);
        Component visibleTitle = Component.literal(clippedTitle).withStyle(style -> style.withBold(true));
        context.drawString(textRenderer, visibleTitle, TEXT_X, 7, 0xFFF6F6F7, false);

        List<FormattedCharSequence> lines = textRenderer.split(description, WIDTH - TEXT_X - 10);
        for (int i = 0; i < Math.min(lines.size(), 2); i++) {
            context.drawString(textRenderer, lines.get(i), TEXT_X, 20 + i * 10, 0xFFC7C7CB, false);
        }
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    private static void drawUpdateIcon(GuiGraphics context, int centerX, int centerY) {
        drawCircle(context, centerX, centerY, 7, 0xFF35C86A);
        drawCircle(context, centerX, centerY, 5, 0xE62B2B2F);
        int green = 0xFF35C86A;
        context.fill(centerX - 1, centerY - 4, centerX + 2, centerY + 2, green);
        context.fill(centerX - 4, centerY, centerX + 5, centerY + 2, green);
        context.fill(centerX - 3, centerY + 2, centerX + 4, centerY + 3, green);
        context.fill(centerX - 2, centerY + 3, centerX + 3, centerY + 4, green);
    }

    private static void drawCircle(GuiGraphics context, int centerX, int centerY, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int halfWidth = (int) Math.floor(Math.sqrt(radius * radius - y * y));
            context.fill(centerX - halfWidth, centerY + y,
                    centerX + halfWidth + 1, centerY + y + 1, color);
        }
    }

    private static void roundedRect(GuiGraphics context, int x, int y, int width, int height, int radius, int color) {
        context.fill(x + radius, y, x + width - radius, y + height, color);
        context.fill(x, y + radius, x + width, y + height - radius, color);
        for (int row = 0; row < radius; row++) {
            double dy = radius - row - 0.5;
            int inset = radius - (int) Math.floor(Math.sqrt(radius * radius - dy * dy));
            context.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            context.fill(x + inset, y + height - row - 1,
                    x + width - inset, y + height - row, color);
        }
    }
}
