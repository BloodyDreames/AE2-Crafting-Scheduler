package dev.BloodyDreamsWork.ae2_scheduler.scheduler;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

public enum ManagedCpuState implements StringRepresentable {
    IDLE("idle"),
    RUNNING("running"),
    PAUSE_REQUESTED("pause_requested"),
    DRAINING_IN_FLIGHT_WORK("draining_in_flight_work"),
    PAUSED("paused"),
    RUNNING_EXPRESS_JOB("running_express_job"),
    EXPRESS_COMPLETED("express_completed"),
    RESTORING("restoring"),
    RESUMED("resumed"),
    ERROR("error"),
    UNAVAILABLE("unavailable"),
    UNSUPPORTED("unsupported");

    public static final StringRepresentable.EnumCodec<ManagedCpuState> CODEC = StringRepresentable
            .fromEnum(ManagedCpuState::values);

    private final String name;

    ManagedCpuState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public Component displayName() {
        return Component.translatable("gui.ae2_crafting_scheduler.cpu_state." + name);
    }

    public boolean holdsPausedJob() {
        return this == PAUSE_REQUESTED || this == DRAINING_IN_FLIGHT_WORK || this == PAUSED
                || this == RUNNING_EXPRESS_JOB || this == EXPRESS_COMPLETED || this == RESTORING
                || this == ERROR;
    }

    public boolean isPreemptable() {
        return this == RUNNING;
    }
}
