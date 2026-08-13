package dev.BloodyDreamsWork.ae2_scheduler.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import dev.BloodyDreamsWork.ae2_scheduler.scheduler.ManagedCpuState;

/**
 * Everything the GUI needs to know about one Crafting CPU.
 *
 * <p>
 * Deliberately flat text and numbers rather than item stacks: the Scheduler's GUI is a status board,
 * and keeping the payload free of registry objects keeps it small and safe across the mod boundary.
 *
 * @param pos             stable CPU key (see {@code CpuKey})
 * @param name            display name, or a generated one for unnamed CPUs
 * @param storageBytes    CPU capacity
 * @param coProcessors    co-processor count
 * @param managed         whether this Scheduler is allowed to pause this CPU
 * @param supported       false for CPU implementations this addon cannot pause safely
 * @param state           where this CPU is in the scheduler state machine
 * @param pausedLabel     the held job, e.g. {@code "Glass x100000"}; empty when nothing is paused
 * @param pausedProgress  progress of the held job in [0, 1]
 * @param inFlight        operations dispatched before the pause that have not returned yet
 * @param activeLabel     the job the CPU is running right now; empty when idle
 * @param activeProgress  progress of the running job in [0, 1]
 * @param activeOperations estimated total operations of the running job
 * @param errorReason     why a resume is currently impossible, or empty
 */
public record CpuStatus(BlockPos pos,
        String name,
        long storageBytes,
        int coProcessors,
        boolean managed,
        boolean supported,
        ManagedCpuState state,
        String pausedLabel,
        float pausedProgress,
        long pausedOperations,
        int inFlight,
        String activeLabel,
        float activeProgress,
        long activeOperations,
        String errorReason) {

    public static final StreamCodec<RegistryFriendlyByteBuf, CpuStatus> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeBlockPos(value.pos);
                buf.writeUtf(value.name, 128);
                buf.writeVarLong(value.storageBytes);
                buf.writeVarInt(value.coProcessors);
                buf.writeBoolean(value.managed);
                buf.writeBoolean(value.supported);
                buf.writeEnum(value.state);
                buf.writeUtf(value.pausedLabel, 128);
                buf.writeFloat(value.pausedProgress);
                buf.writeVarLong(value.pausedOperations);
                buf.writeVarInt(value.inFlight);
                buf.writeUtf(value.activeLabel, 128);
                buf.writeFloat(value.activeProgress);
                buf.writeVarLong(value.activeOperations);
                buf.writeUtf(value.errorReason, 128);
            },
            buf -> new CpuStatus(
                    buf.readBlockPos(),
                    buf.readUtf(128),
                    buf.readVarLong(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readEnum(ManagedCpuState.class),
                    buf.readUtf(128),
                    buf.readFloat(),
                    buf.readVarLong(),
                    buf.readVarInt(),
                    buf.readUtf(128),
                    buf.readFloat(),
                    buf.readVarLong(),
                    buf.readUtf(128)));

    public boolean hasPausedJob() {
        return !pausedLabel.isEmpty();
    }

    public boolean hasActiveJob() {
        return !activeLabel.isEmpty();
    }
}
