package dev.BloodyDreamsWork.ae2_scheduler.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import dev.BloodyDreamsWork.ae2_scheduler.AE2CraftingScheduler;

public record SchedulerActionPayload(Action action, BlockPos cpu) implements CustomPacketPayload {
    public enum Action {
        TOGGLE_CPU,
        CYCLE_REDSTONE,
        CANCEL_EXPRESS,
        CYCLE_REDSTONE_BACKWARDS
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
