package dev.habib.client.mixin;

import dev.habib.client.config.HabibConfig;
import dev.habib.client.util.TierTagger;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds a cached MCTiers badge to player display names. */
@Mixin(Entity.class)
public abstract class TierNameTagMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void habibclient$appendTier(CallbackInfoReturnable<net.minecraft.network.chat.Component> cir) {
        if (!HabibConfig.tierTagger || !((Object) this instanceof Player player)) return;
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player) return;
        String tier = TierTagger.tier(player.getUUID(), player.getName().getString());
        if (tier == null || cir.getReturnValue() == null) return;
        MutableComponent badge = net.minecraft.network.chat.Component.literal("[" + tier + "] ")
                .withStyle(style -> style.withColor(TierTagger.color(tier)));
        cir.setReturnValue(badge.copy().append(cir.getReturnValue()));
    }
}
