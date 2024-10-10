package com.vendoau.alternatekeepalive.mixin;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class AlternateKeepAliveMixin {

    @Shadow
    private long keepAliveTime;

    @Shadow
    private boolean keepAlivePending;

    @Shadow
    public abstract void disconnect(Component reason);

    @Shadow
    @Final
    private static Component TIMEOUT_DISCONNECTION_MESSAGE;

    @Shadow
    protected abstract boolean checkIfClosed(long time);

    @Shadow
    public abstract void send(Packet<?> packet);

    @Shadow
    private int latency;

    @Unique
    private final LongList keepAlives = new LongArrayList();

    @Inject(method = "handleKeepAlive", at = @At("HEAD"), cancellable = true)
    private void handleKeepAlive(ServerboundKeepAlivePacket packet, CallbackInfo ci) {
        if (keepAlivePending && !keepAlives.isEmpty() && keepAlives.contains(packet.getId())) {
            final int ping = (int) (Util.getMillis() - packet.getId());
            latency = (latency * 3 + ping) / 4;
            keepAlivePending = false;
            keepAlives.clear();
        }
        ci.cancel();
    }

    @Inject(method = "keepConnectionAlive", at = @At("HEAD"), cancellable = true)
    private void keepConnectionAlive(CallbackInfo ci) {
        long currentTime = Util.getMillis();
        long elapsedTime = currentTime - this.keepAliveTime;

        if (elapsedTime >= 1000) {
            if (keepAlivePending && keepAlives.size() >= 30) {
                disconnect(TIMEOUT_DISCONNECTION_MESSAGE);
            } else if (checkIfClosed(currentTime)) {
                keepAlivePending = true;
                keepAliveTime = currentTime;
                keepAlives.add(currentTime);
                send(new ClientboundKeepAlivePacket(currentTime));
            }
        }
        ci.cancel();
    }
}
