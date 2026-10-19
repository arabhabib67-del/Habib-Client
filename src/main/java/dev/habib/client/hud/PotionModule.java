package dev.habib.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.Collection;

/** Active status effects with level and time remaining. */
public class PotionModule extends HudModule {
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    public PotionModule(int x, int y) {
        super("potions", "Potion Status", x, y);
        width = 110;
        height = 14;
    }

    @Override
    protected void render(DrawContext ctx, MinecraftClient mc, boolean editing) {
        if (mc.player == null) return;
        Collection<StatusEffectInstance> effects = mc.player.getStatusEffects();
        if (effects.isEmpty()) {
            width = 110;
            height = 14;
            if (editing) {
                box(ctx, 0, 0, width, height, 0x66000000);
                ctx.drawText(mc.textRenderer, "Potion Status", 4, 3, color, true);
            }
            return;
        }

        int maxW = 0;
        String[] lines = new String[effects.size()];
        int[] colors = new int[effects.size()];
        int i = 0;
        for (StatusEffectInstance inst : effects) {
            StatusEffect effect = inst.getEffectType().value();
            String lvl = inst.getAmplifier() < ROMAN.length ? ROMAN[inst.getAmplifier()] : String.valueOf(inst.getAmplifier() + 1);
            lines[i] = effect.getName().getString() + " " + lvl + "  " + time(inst);
            colors[i] = 0xFF000000 | effect.getColor();
            maxW = Math.max(maxW, mc.textRenderer.getWidth(lines[i]));
            i++;
        }
        width = maxW + 8;
        height = lines.length * 11 + 3;
        box(ctx, 0, 0, width, height, 0x66000000);
        for (int n = 0; n < lines.length; n++) {
            ctx.drawText(mc.textRenderer, lines[n], 4, 3 + n * 11, colors[n], true);
        }
    }

    private String time(StatusEffectInstance inst) {
        if (inst.isInfinite()) return "**:**";
        int secs = inst.getDuration() / 20;
        return String.format("%d:%02d", secs / 60, secs % 60);
    }
}
