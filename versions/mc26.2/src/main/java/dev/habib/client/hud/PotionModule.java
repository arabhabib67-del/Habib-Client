package dev.habib.client.hud;

import java.util.Collection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/** Active status effects with level and time remaining. */
public class PotionModule extends HudModule {
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    public PotionModule(int x, int y) {
        super("potions", "Potion Status", x, y);
        width = 110;
        height = 14;
    }

    @Override
    protected void render(GuiGraphics ctx, Minecraft mc, boolean editing) {
        if (mc.player == null) return;
        Collection<MobEffectInstance> effects = mc.player.getActiveEffects();
        if (effects.isEmpty()) {
            width = 110;
            height = 14;
            if (editing) {
                box(ctx, 0, 0, width, height, 0x66000000);
                ctx.drawString(mc.font, "Potion Status", 4, 3, color, true);
            }
            return;
        }

        int maxW = 0;
        String[] lines = new String[effects.size()];
        int[] colors = new int[effects.size()];
        int i = 0;
        for (MobEffectInstance inst : effects) {
            MobEffect effect = inst.getEffect().value();
            String lvl = inst.getAmplifier() < ROMAN.length ? ROMAN[inst.getAmplifier()] : String.valueOf(inst.getAmplifier() + 1);
            lines[i] = effect.getDisplayName().getString() + " " + lvl + "  " + time(inst);
            colors[i] = 0xFF000000 | effect.getColor();
            maxW = Math.max(maxW, mc.font.width(lines[i]));
            i++;
        }
        width = maxW + 8;
        height = lines.length * 11 + 3;
        box(ctx, 0, 0, width, height, 0x66000000);
        for (int n = 0; n < lines.length; n++) {
            ctx.drawString(mc.font, lines[n], 4, 3 + n * 11, colors[n], true);
        }
    }

    private String time(MobEffectInstance inst) {
        if (inst.isInfiniteDuration()) return "**:**";
        int secs = inst.getDuration() / 20;
        return String.format("%d:%02d", secs / 60, secs % 60);
    }
}
