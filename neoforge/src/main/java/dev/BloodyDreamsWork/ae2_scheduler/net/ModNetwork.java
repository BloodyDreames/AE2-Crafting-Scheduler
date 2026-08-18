package dev.BloodyDreamsWork.ae2_scheduler.net;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import dev.BloodyDreamsWork.ae2_scheduler.AE2CraftingScheduler;
import dev.BloodyDreamsWork.ae2_scheduler.menu.SchedulerMenu;

/**
 * NeoForge implementation of the networking hooks the shared code calls.
 */
@EventBusSubscriber(modid = AE2CraftingScheduler.MODID)
public final class ModNetwork {
    private ModNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");

        registrar.playToClient(SchedulerPayloads.Status.TYPE, SchedulerPayloads.Status.STREAM_CODEC,
                ModNetwork::handleStatus);
        registrar.playToServer(SchedulerPayloads.Action.TYPE, SchedulerPayloads.Action.STREAM_CODEC,
                ModNetwork::handleAction);
    }

    private static void handleStatus(SchedulerPayloads.Status payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof SchedulerMenu menu) {
            menu.setStatus(payload.status());
        }
    }

    private static void handleAction(SchedulerPayloads.Action payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof SchedulerMenu menu) {
            menu.handleAction(payload.action());
        }
    }

    // --- hooks used by the shared code ---

    public static void sendStatusToPlayer(ServerPlayer player, SchedulerStatus status) {
        PacketDistributor.sendToPlayer(player, new SchedulerPayloads.Status(status));
    }

    public static void sendActionToServer(SchedulerAction action) {
        PacketDistributor.sendToServer(new SchedulerPayloads.Action(action));
    }
}
