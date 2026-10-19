package dev.habib.client.mixin;

import dev.habib.client.util.TotemPops;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Listens for entity status packets so totem pops can be counted. */
@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    // require = 0: if this ever fails to apply, only the pop counter stops working (no crash)
    @Inject(method = "onEntityStatus", at = @At("HEAD"), require = 0)
    private void habibclient$status(EntityStatusS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        Entity e = packet.getEntity(mc.world);
        if (e != null) TotemPops.onEntityStatus(e, packet.getStatus());
    }
}
