package dev.BloodyDreamsWork.ae2_scheduler.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import dev.BloodyDreamsWork.ae2_scheduler.AE2CraftingScheduler;

/**
 * Client to server: one GUI action. Every action is validated against the open menu on arrival, so a
 * crafted packet can only ever affect a Scheduler the player actually has open.
 */
public record SchedulerActionPayload(Action action, BlockPos cpu) implements CustomPacketPayload {

    public enum Action {
        /** Add or remove a CPU from this Scheduler's managed set. */
        TOGGLE_CPU,
        /** Cycle Ignore Redstone / Active With Signal / Active Without Signal. */
        CYCLE_REDSTONE,
        /** Cancel the express job on a CPU and resume the original one immediately. */
        CANCEL_EXPRESS
    }

    public static final Type<SchedulerActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AE2CraftingScheduler.MODID, "scheduler_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SchedulerActionPayload> STREAM_CODEC = StreamCodec
            .of(
                    (buf, value) -> {
                        buf.writeEnum(value.action);
                        buf.writeBlockPos(value.cpu);
                    },
                    buf -> new SchedulerActionPayload(buf.readEnum(Action.class), buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
