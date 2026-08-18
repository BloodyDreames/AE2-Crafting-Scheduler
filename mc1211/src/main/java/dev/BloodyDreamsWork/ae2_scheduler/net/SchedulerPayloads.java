package dev.BloodyDreamsWork.ae2_scheduler.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import dev.BloodyDreamsWork.ae2_scheduler.AE2CraftingScheduler;

/**
 * Minecraft 1.21.1 packet wrappers around the shared {@link SchedulerStatus} and
 * {@link SchedulerAction} payload bodies. The wire format is produced by the shared
 * {@code write}/{@code read} methods, so it is byte-identical to the 1.20.1 builds.
 */
public final class SchedulerPayloads {
    private SchedulerPayloads() {
    }

    public record Status(SchedulerStatus status) implements CustomPacketPayload {
        public static final Type<Status> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(AE2CraftingScheduler.MODID, "scheduler_status"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Status> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> SchedulerStatus.write(buf, value.status),
                buf -> new Status(SchedulerStatus.read(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Action(SchedulerAction action) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(AE2CraftingScheduler.MODID, "scheduler_action"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> SchedulerAction.write(buf, value.action),
                buf -> new Action(SchedulerAction.read(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
