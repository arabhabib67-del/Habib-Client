package dev.habib.client.mixin;

import dev.habib.client.config.HabibConfig;
import dev.habib.client.util.TierTagger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds a cached MCTiers badge to player display names. */
@Mixin(Entity.class)
public abstract class TierNameTagMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void habibclient$appendTier(CallbackInfoReturnable<net.minecraft.text.Text> cir) {
        if (!HabibConfig.tierTagger || !((Object) this instanceof PlayerEntity player)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (player == mc.player) return;
        String tier = TierTagger.tier(player.getUuid(), player.getName().getString());
        if (tier == null || cir.getReturnValue() == null) return;
        MutableText badge = net.minecraft.text.Text.literal("[" + tier + "] ")
                .styled(style -> style.withColor(TierTagger.color(tier)));
        cir.setReturnValue(badge.append(cir.getReturnValue()));
    }
}
