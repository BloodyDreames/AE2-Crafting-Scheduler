package dev.BloodyDreamsWork.ae2_scheduler.scheduler;

import net.minecraft.util.StringRepresentable;

/**
 * What the Scheduler block itself looks like. Each state uses the status-light colour from the block's
 * visual design so it can be read at a glance.
 */
public enum SchedulerVisualState implements StringRepresentable {
    /** No grid, no power, or switched off by redstone. */
    OFFLINE("offline"),
    /** Online and watching its CPUs. */
    ACTIVE("active"),
    /** A pause is in progress: state captured, in-flight work still settling. */
    PAUSING("pausing"),
    /** At least one original job is fully parked and waiting to resume. */
    PAUSED("paused"),
    /** An express job is running on a CPU whose original job is held. */
    EXPRESS_JOB("express_job"),
    /** At least one paused job cannot be resumed right now. */
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
