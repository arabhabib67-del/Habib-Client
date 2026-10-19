package dev.habib.client.hud;

import dev.habib.client.config.HabibConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.util.Arrays;

/**
 * Armor pieces, optionally with the held item. Customisable from the Layout tab:
 * vertical list or horizontal row, held item on/off, durability as uses, percent, the vanilla
 * durability bar on the item, or hidden.
 * Empty slots are skipped so the module never has gaps.
 */
public class ArmorModule extends HudModule {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND
    };
    private static final int ROW = 18;


    // per-slot cache so the text is only rebuilt when the piece, its damage or the display mode changes
    private final ItemStack[] cachedStack = new ItemStack[SLOTS.length];
    private final int[] cachedDamage = new int[SLOTS.length];
    private final int[] cachedMode = new int[SLOTS.length];
    private final String[] cachedText = new String[SLOTS.length];
    private final int[] cachedColor = new int[SLOTS.length];
    private final int[] shown = new int[SLOTS.length];

    public ArmorModule(int x, int y) {
        super("armor", "Armor Status", x, y);
        width = 52;
        height = SLOTS.length * ROW;
        Arrays.fill(cachedDamage, -1);
        Arrays.fill(cachedMode, -1);
    }

    @Override
    protected void render(DrawContext ctx, MinecraftClient mc, boolean editing) {
        if (mc.player == null) return;

        int slots = HabibConfig.armorShowHeld ? SLOTS.length : SLOTS.length - 1;
        int n = 0;
        for (int i = 0; i < slots; i++) {
            if (!mc.player.getEquippedStack(SLOTS[i]).isEmpty()) shown[n++] = i;
        }

        boolean horizontal = HabibConfig.armorLayout == 1;
        int mode = HabibConfig.armorDurability;
        boolean text = mode <= 1; // 2 = vanilla durability bar, 3 = hidden

        if (n == 0) {
            width = 52;
            height = ROW;
            if (editing) {
                box(ctx, 0, 0, width, height - 1, 0x66000000);
                ctx.drawCenteredTextWithShadow(mc.textRenderer, "Armor", width / 2, 5, color);
            }
            return;
        }

        int cell = text ? 26 : ROW;
        if (horizontal) {
            width = n * cell;
            height = text ? ROW + 10 : ROW;
        } else {
            width = text ? 52 : ROW + 2;
            height = n * ROW;
        }
        box(ctx, 0, 0, width, horizontal ? height : height - 1, 0x66000000);

        for (int k = 0; k < n; k++) {
            int i = shown[k];
            ItemStack stack = mc.player.getEquippedStack(SLOTS[i]);
            int x = horizontal ? k * cell + (cell - 16) / 2 : 0;
            int y = horizontal ? 0 : k * ROW;
            ctx.drawItem(stack, x, y);

            if (stack.isDamageable()) {
                if (mode == 2) {
                    ctx.drawStackOverlay(mc.textRenderer, stack, x, y); // the item's own vanilla durability bar
                    continue;
                }
                if (!text) continue;
                refresh(i, stack, mode);
                if (horizontal) {
                    String s = cachedText[i];
                    ctx.drawText(mc.textRenderer, s, k * cell + (cell - mc.textRenderer.getWidth(s)) / 2, ROW,
                            cachedColor[i], true);
                } else {
                    ctx.drawText(mc.textRenderer, cachedText[i], 20, y + 5, cachedColor[i], true);
                }
            } else {
                // stackable items (totems, crystals, ...) still need their count
                ctx.drawStackOverlay(mc.textRenderer, stack, x, y);
            }
        }
    }

    private void refresh(int i, ItemStack stack, int mode) {
        int damage = stack.getDamage();
        if (cachedStack[i] == stack && cachedDamage[i] == damage && cachedMode[i] == mode) return;
        cachedStack[i] = stack;
        cachedDamage[i] = damage;
        cachedMode[i] = mode;
        int max = stack.getMaxDamage();
        int left = max - damage;
        cachedText[i] = mode == 1 ? Math.round(100f * left / max) + "%" : Integer.toString(left);
        float f = 1f - (float) damage / (float) max;
        cachedColor[i] = f > 0.5f ? 0xFF55FF55 : f > 0.2f ? 0xFFFFFF55 : 0xFFFF5555;
    }
}
