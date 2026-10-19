package dev.habib.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;

/** Name + health bar of the last living entity you looked at (stays for 3 seconds). */
public class TargetModule extends HudModule {
    private LivingEntity target;
    private long lastSeen;

    public TargetModule(int x, int y) {
        super("target", "Target HUD", x, y);
        width = 110;
        height = 28;
    }

    @Override
    public void tick(MinecraftClient mc) {
        if (mc.targetedEntity instanceof LivingEntity living && living.isAlive()) {
            target = living;
            lastSeen = System.currentTimeMillis();
        } else if (System.currentTimeMillis() - lastSeen > 3000L) {
            target = null;
        }
    }

    @Override
    protected void render(DrawContext ctx, MinecraftClient mc, boolean editing) {
        String name;
        float hp;
        float max;
        if (target != null && target.isAlive()) {
            name = target.getName().getString();
            hp = target.getHealth();
            max = Math.max(1f, target.getMaxHealth());
        } else if (editing) {
            name = "Target";
            hp = 14f;
            max = 20f;
        } else {
            return;
        }

        box(ctx, 0, 0, width, height, 0x66000000);
        ctx.drawText(mc.textRenderer, name, 4, 3, color, true);
        String hpText = String.format("%.1f/%.0f", hp, max);
        ctx.drawText(mc.textRenderer, hpText, width - 4 - mc.textRenderer.getWidth(hpText), 3, 0xFFFFFFFF, true);

        float frac = Math.max(0f, Math.min(1f, hp / max));
        int barW = width - 8;
        int barColor = frac > 0.5f ? 0xFF55FF55 : frac > 0.25f ? 0xFFFFFF55 : 0xFFFF5555;
        ctx.fill(4, 16, 4 + barW, 23, 0xAA000000);
        ctx.fill(4, 16, 4 + Math.round(barW * frac), 23, barColor);
    }
}
