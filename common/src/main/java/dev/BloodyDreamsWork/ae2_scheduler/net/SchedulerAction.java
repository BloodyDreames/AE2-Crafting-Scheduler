package dev.BloodyDreamsWork.ae2_scheduler.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -&gt; server request against one Scheduler. Loader-independent payload body.
 */
public record SchedulerAction(Type type, BlockPos cpu) {
    public enum Type {
        TOGGLE_CPU,
        CYCLE_REDSTONE,
        CANCEL_EXPRESS,
        CYCLE_REDSTONE_BACKWARDS
    }

    public static void write(FriendlyByteBuf buf, SchedulerAction value) {
        buf.writeEnum(value.type);
        buf.writeBlockPos(value.cpu);
    }

    public static SchedulerAction read(FriendlyByteBuf buf) {
        return new SchedulerAction(buf.readEnum(Type.class), buf.readBlockPos());
    }
}
