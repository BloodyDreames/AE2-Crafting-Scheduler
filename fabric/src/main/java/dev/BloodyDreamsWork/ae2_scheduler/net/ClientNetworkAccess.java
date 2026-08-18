package dev.BloodyDreamsWork.ae2_scheduler.net;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

import net.minecraft.client.Minecraft;

import dev.BloodyDreamsWork.ae2_scheduler.menu.SchedulerMenu;

/**
 * Client-only half of the Fabric networking. Kept separate so a dedicated server never loads
 * {@link Minecraft} or the client networking API.
 */
@Environment(EnvType.CLIENT)
public final class ClientNetworkAccess {
    private ClientNetworkAccess() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.STATUS_CHANNEL,
                (client, handler, buf, responseSender) -> {
                    var status = SchedulerStatus.read(buf);
                    client.execute(() -> {
                        var player = client.player;
                        if (player != null && player.containerMenu instanceof SchedulerMenu menu) {
                            menu.setStatus(status);
                        }
                    });
                });
    }

    static void sendAction(SchedulerAction action) {
        var buf = PacketByteBufs.create();
        SchedulerAction.write(buf, action);
        ClientPlayNetworking.send(ModNetwork.ACTION_CHANNEL, buf);
    }
}
