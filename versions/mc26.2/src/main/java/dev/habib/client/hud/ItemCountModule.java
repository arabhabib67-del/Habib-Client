package dev.habib.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Inventory counts for the crystal PvP essentials: totems, crystals, obsidian, gapples, pearls, XP bottles. */
public class ItemCountModule extends HudModule {
    private static final Item[][] GROUPS = {
            {Items.TOTEM_OF_UNDYING},
            {Items.END_CRYSTAL},
            {Items.OBSIDIAN},
            {Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE},
            {Items.ENDER_PEARL},
            {Items.EXPERIENCE_BOTTLE}
    };

    public ItemCountModule(int x, int y) {
        super("items", "Item Counter", x, y);
        width = GROUPS.length * 24;
        height = 20;
    }

    @Override
    protected void render(GuiGraphicsExtractor ctx, Minecraft mc, boolean editing) {
        if (mc.player == null) return;
        Inventory inv = mc.player.getInventory();
        box(ctx, 0, 0, width, height, 0x66000000);
        for (int i = 0; i < GROUPS.length; i++) {
            int count = 0;
            for (int slot = 0; slot < inv.getContainerSize(); slot++) {
                ItemStack s = inv.getItem(slot);
                for (Item it : GROUPS[i]) {
                    if (s.is(it)) {
                        count += s.getCount();
                    }
                }
            }
            int x = i * 24 + 3;
            ctx.item(new ItemStack(GROUPS[i][0]), x, 0);
            String c = String.valueOf(count);
            ctx.text(mc.font, c, x + 16 - mc.font.width(c) + 2, 11,
                    count == 0 ? 0xFFFF5555 : color, true);
        }
    }
}
