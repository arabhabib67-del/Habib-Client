package dev.habib.client.mixin;

import dev.habib.client.beta.BetaAccess;
import dev.habib.client.util.TotemPops;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Listens for entity status packets so totem pops can be counted. */
@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {
    // require = 0: if this ever fails to apply, only the pop counter stops working (no crash)
    @Inject(method = "handleEntityEvent", at = @At("HEAD"), require = 0)
    private void habibclient$status(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!BetaAccess.isAuthorized() || mc.level == null) return;
        Entity e = packet.getEntity(mc.level);
        if (e != null) TotemPops.onEntityStatus(e, packet.getEventId());
    }
}
