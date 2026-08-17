package dev.BloodyDreamsWork.ae2_scheduler.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import dev.BloodyDreamsWork.ae2_scheduler.scheduler.ManagedCpuState;

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
