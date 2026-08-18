package dev.BloodyDreamsWork.ae2_scheduler.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import dev.BloodyDreamsWork.ae2_scheduler.AE2CraftingScheduler;
import dev.BloodyDreamsWork.ae2_scheduler.menu.SchedulerMenu;

/**
 * Forge 1.20.1 implementation of the networking hooks the shared code calls.
 *
 * <p>
 * Minecraft 1.20.1 has no {@code CustomPacketPayload} and Forge 47 has no {@code ChannelBuilder}, so
 * the shared {@link SchedulerStatus} and {@link SchedulerAction} records travel over a
 * {@code SimpleChannel} built through {@code NetworkRegistry}. The bytes on the wire come from the
 * same shared {@code write}/{@code read} methods the 1.21.1 build uses.
 */
public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "2";

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AE2CraftingScheduler.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(SchedulerStatus.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> SchedulerStatus.write(buf, msg))
                .decoder(SchedulerStatus::read)
                .consumerMainThread((msg, ctx) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        ClientStatusHandler.accept(msg);
                    }
                    ctx.get().setPacketHandled(true);
                })
                .add();

        CHANNEL.messageBuilder(SchedulerAction.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder((msg, buf) -> SchedulerAction.write(buf, msg))
                .decoder(SchedulerAction::read)
                .consumerMainThread((msg, ctx) -> {
                    var sender = ctx.get().getSender();
                    if (sender != null && sender.containerMenu instanceof SchedulerMenu menu) {
                        menu.handleAction(msg);
                    }
                    ctx.get().setPacketHandled(true);
                })
                .add();
    }

    // --- hooks used by the shared code ---

    public static void sendStatusToPlayer(ServerPlayer player, SchedulerStatus status) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), status);
    }

    public static void sendActionToServer(SchedulerAction action) {
        CHANNEL.sendToServer(action);
    }
}
