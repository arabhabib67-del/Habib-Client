package dev.habib.client.util;

import dev.habib.client.config.HabibConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Optional screen alerts for crystal fights: a pulsing red edge when you are low, a flash when a
 * totem pops, an orange pulse when armor is nearly broken, and chat notices for pops.
 * Everything here is display-only and every option starts off.
 */
public final class Effects {
    private static final long FLASH_MS = 450L;
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static long flashUntil;
    private static int flashRgb;

    private Effects() {}

    public static void init() {
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> render(ctx));
    }

    /** Called right after a totem pop was counted for this player. */
    public static void onPop(PlayerEntity p) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        boolean self = p.getUuid().equals(mc.player.getUuid());
        PlayerEntity target = TotemPops.target();
        boolean tgt = target != null && p.getUuid().equals(target.getUuid());
        if (!self && !tgt) return;

        if (HabibConfig.popFlash) {
            flashRgb = self ? 0xFFC83C : 0x55FF55; // gold when you pop, green when your target does
            flashUntil = System.currentTimeMillis() + FLASH_MS;
        }
        if (HabibConfig.popMessages) {
            String who = self ? "You" : p.getName().getString();
            mc.inGameHud.getChatHud().addMessage(
                    Text.literal("[Habib] " + who + " popped a totem (" + TotemPops.pops(p) + ")"));
        }
    }

    private static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || mc.currentScreen != null) return;
        long now = System.currentTimeMillis();
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        if (HabibConfig.popFlash && now < flashUntil) {
            vignette(ctx, w, h, flashRgb, 0.55f * (flashUntil - now) / (float) FLASH_MS);
        }
        if (HabibConfig.lowHealthWarning && mc.player.isAlive() && mc.player.getHealth() <= 6f) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 220.0);
            vignette(ctx, w, h, 0xFF3030, 0.25f + 0.30f * pulse);
        }
        if (HabibConfig.lowArmorWarning && armorNearlyBroken(mc.player)) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 350.0);
            vignette(ctx, w, h, 0xFFA020, 0.12f + 0.16f * pulse);
        }
    }

    private static boolean armorNearlyBroken(PlayerEntity p) {
        for (EquipmentSlot slot : ARMOR) {
            ItemStack st = p.getEquippedStack(slot);
            if (st.isEmpty() || !st.isDamageable() || st.getMaxDamage() <= 0) continue;
            if (st.getMaxDamage() - st.getDamage() <= st.getMaxDamage() * 0.10f) return true;
        }
        return false;
    }

    /** Soft coloured edge: stacked translucent frames, thickest layer outermost. */
    private static void vignette(DrawContext ctx, int w, int h, int rgb, float strength) {
        int layers = 6;
        int a = Math.round(255f * Math.max(0f, Math.min(1f, strength)) / layers);
        if (a <= 0) return;
        int col = (a << 24) | (rgb & 0xFFFFFF);
        for (int i = 0; i < layers; i++) {
            int t = (layers - i) * 5;
            ctx.fill(0, 0, w, t, col);
            ctx.fill(0, h - t, w, h, col);
            ctx.fill(0, t, t, h - t, col);
            ctx.fill(w - t, t, w, h - t, col);
        }
    }
}
