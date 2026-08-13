package dev.BloodyDreamsWork.ae2_scheduler.net;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import dev.BloodyDreamsWork.ae2_scheduler.AE2CraftingScheduler;
import dev.BloodyDreamsWork.ae2_scheduler.menu.CpuStatus;
import dev.BloodyDreamsWork.ae2_scheduler.scheduler.SchedulerRedstoneMode;

/**
 * Server to client: the full state of one Scheduler, sent while its GUI is open.
 *
 * @param operational  whether the Scheduler is online, powered and enabled by redstone
 * @param preemption   whether automatic preemption is enabled in the server config
 * @param maxExpress   the express complexity limit from the server config
 * @param minPreempt   the minimum job complexity that may be interrupted
 * @param redstoneMode the Scheduler's redstone setting
 * @param cpus         every Crafting CPU on the network, managed or not
 */
public record SchedulerStatusPayload(boolean operational,
        boolean preemption,
        long maxExpress,
        long minPreempt,
        SchedulerRedstoneMode redstoneMode,
        List<CpuStatus> cpus) implements CustomPacketPayload {

    public static final Type<SchedulerStatusPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AE2CraftingScheduler.MODID, "scheduler_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SchedulerStatusPayload> STREAM_CODEC = StreamCodec
            .of(
                    (buf, value) -> {
                        buf.writeBoolean(value.operational);
                        buf.writeBoolean(value.preemption);
                        buf.writeVarLong(value.maxExpress);
                        buf.writeVarLong(value.minPreempt);
                        buf.writeEnum(value.redstoneMode);
                        CpuStatus.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, value.cpus);
                    },
                    buf -> new SchedulerStatusPayload(
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readVarLong(),
                            buf.readVarLong(),
                            buf.readEnum(SchedulerRedstoneMode.class),
                            CpuStatus.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
