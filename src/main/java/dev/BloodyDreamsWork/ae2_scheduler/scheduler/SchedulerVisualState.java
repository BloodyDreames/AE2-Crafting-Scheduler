package dev.BloodyDreamsWork.ae2_scheduler.scheduler;

import net.minecraft.util.StringRepresentable;

public enum SchedulerVisualState implements StringRepresentable {
    OFFLINE("offline"),
    ACTIVE("active"),
    PAUSING("pausing"),
    PAUSED("paused"),
    EXPRESS_JOB("express_job"),
    ERROR("error");

    private final String name;

    SchedulerVisualState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
