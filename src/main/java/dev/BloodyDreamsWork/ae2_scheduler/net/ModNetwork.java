package dev.BloodyDreamsWork.ae2_scheduler.net;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import dev.BloodyDreamsWork.ae2_scheduler.AE2CraftingScheduler;
import dev.BloodyDreamsWork.ae2_scheduler.menu.SchedulerMenu;

@EventBusSubscriber(modid = AE2CraftingScheduler.MODID)
public final class ModNetwork {
    private ModNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");

        registrar.playToClient(SchedulerStatusPayload.TYPE, SchedulerStatusPayload.STREAM_CODEC,
                ModNetwork::handleStatus);
        registrar.playToServer(SchedulerActionPayload.TYPE, SchedulerActionPayload.STREAM_CODEC,
                ModNetwork::handleAction);
    }

    private static void handleStatus(SchedulerStatusPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof SchedulerMenu menu) {
            menu.setStatus(payload);
        }
    }

    private static void handleAction(SchedulerActionPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof SchedulerMenu menu) {
            menu.handleAction(payload);
        }
    }
}
