package dev.habib.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffects;

/** Keeps you sprinting while you hold forward, so you never have to double-tap W. */
public class ToggleSprintModule extends TextModule {
    private boolean active;

    public ToggleSprintModule(int x, int y) {
        super("togglesprint", "Toggle Sprint", x, y, mc -> "Sprint: Toggled", null);
        enabled = false; // changes gameplay, so it starts off
        defaultEnabled = false;
    }

    @Override
    public void tick(MinecraftClient mc) {
        active = false;
        ClientPlayerEntity p = mc.player;
        if (!enabled || p == null || mc.currentScreen != null) return;
        if (mc.options.forwardKey.isPressed()
                && !p.isSneaking()
                && !p.isUsingItem()
                && !p.horizontalCollision
                && !p.hasStatusEffect(StatusEffects.BLINDNESS)
                && p.getHungerManager().getFoodLevel() > 6) {
            p.setSprinting(true);
            active = true;
        }
    }
}
