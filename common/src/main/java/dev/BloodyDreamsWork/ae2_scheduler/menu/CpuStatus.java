package dev.BloodyDreamsWork.ae2_scheduler.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import dev.BloodyDreamsWork.ae2_scheduler.scheduler.ManagedCpuState;

/**
 * One row of the Scheduler's CPU table, as computed on the server.
 *
 * <p>
 * Encoded with plain {@link FriendlyByteBuf} calls so the exact same wire format is used on
 * Minecraft 1.20.1 and 1.21.1 (1.21.1's {@code RegistryFriendlyByteBuf} is a subclass).
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

    public static void write(FriendlyByteBuf buf, CpuStatus value) {
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
    }

    public static CpuStatus read(FriendlyByteBuf buf) {
        return new CpuStatus(
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
                buf.readUtf(128));
    }

    public boolean hasPausedJob() {
        return !pausedLabel.isEmpty();
    }

    public boolean hasActiveJob() {
        return !activeLabel.isEmpty();
    }
}
