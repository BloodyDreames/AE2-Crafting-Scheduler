package dev.BloodyDreamsWork.ae2_scheduler.net;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import dev.BloodyDreamsWork.ae2_scheduler.AE2CraftingScheduler;
import dev.BloodyDreamsWork.ae2_scheduler.menu.SchedulerMenu;

/**
 * Fabric implementation of the networking hooks the shared code calls.
 *
 * <p>
 * Two plain custom-payload channels carrying the shared {@link SchedulerStatus} and
 * {@link SchedulerAction} payload bodies. The bytes on the wire come from the same shared
 * {@code write}/{@code read} methods the other two targets use.
 */
public final class ModNetwork {
    public static final ResourceLocation STATUS_CHANNEL = new ResourceLocation(
            AE2CraftingScheduler.MODID, "scheduler_status");
    public static final ResourceLocation ACTION_CHANNEL = new ResourceLocation(
            AE2CraftingScheduler.MODID, "scheduler_action");

    private ModNetwork() {
    }

    /** Called from the common entry point; registers only the serverbound handler. */
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ACTION_CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    var action = SchedulerAction.read(buf);
                    server.execute(() -> {
                        if (player.containerMenu instanceof SchedulerMenu menu) {
                            menu.handleAction(action);
                        }
                    });
                });
    }

    // --- hooks used by the shared code ---

    public static void sendStatusToPlayer(ServerPlayer player, SchedulerStatus status) {
        var buf = PacketByteBufs.create();
        SchedulerStatus.write(buf, status);
        ServerPlayNetworking.send(player, STATUS_CHANNEL, buf);
    }

    public static void sendActionToServer(SchedulerAction action) {
        ClientNetworkAccess.sendAction(action);
    }
}
