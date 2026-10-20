package dev.habib.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;

/** Keeps you sprinting while you hold forward, so you never have to double-tap W. */
public class ToggleSprintModule extends TextModule {
    private boolean active;

    public ToggleSprintModule(int x, int y) {
        super("togglesprint", "Toggle Sprint", x, y, mc -> "Sprint: Toggled", null);
        enabled = false; // changes gameplay, so it starts off
        defaultEnabled = false;
    }

    @Override
    public void tick(Minecraft mc) {
        active = false;
        LocalPlayer p = mc.player;
        if (!enabled || p == null || mc.gui.screen() != null) return;
        if (mc.options.keyUp.isDown()
                && !p.isShiftKeyDown()
                && !p.isUsingItem()
                && !p.horizontalCollision
                && !p.hasEffect(MobEffects.BLINDNESS)
                && p.getFoodData().getFoodLevel() > 6) {
            p.setSprinting(true);
            active = true;
        }
    }
}
