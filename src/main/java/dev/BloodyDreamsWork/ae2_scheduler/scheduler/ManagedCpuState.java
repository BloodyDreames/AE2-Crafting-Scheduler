package dev.BloodyDreamsWork.ae2_scheduler.scheduler;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * The state of one managed Crafting CPU, as an explicit state machine rather than a set of booleans.
 *
 * <pre>
 * IDLE ────────► RUNNING ────────► PAUSE_REQUESTED
 *   ▲               ▲                    │
 *   │               │                    ▼
 *   │               │      DRAINING_IN_FLIGHT_WORK ──► PAUSED
 *   │               │                                    │
 *   │               │                                    ▼
 *   │               │                        RUNNING_EXPRESS_JOB
 *   │               │                                    │
 *   │               │                                    ▼
 *   │               │                          EXPRESS_COMPLETED
 *   │               │                                    │
 *   │               │                                    ▼
 *   │               └──── RESUMED ◄──── RESTORING ◄──────┘
 *   │                                        │
 *   └────────────────────────────────────────┴──────────► ERROR
 * </pre>
 *
 * {@link #ERROR} is never terminal and never discards state: the paused job stays on its CPU and the
 * Scheduler keeps retrying the resume.
 */
public enum ManagedCpuState implements StringRepresentable {
    /** Managed, online, no job. */
    IDLE("idle"),
    /** Running a job of its own; nothing paused. */
    RUNNING("running"),
    /** A preemption has been decided this tick and the job is about to be moved to the park slot. */
    PAUSE_REQUESTED("pause_requested"),
    /**
     * The job is paused and its state is safe, but operations dispatched to machines before the pause
     * have not all come back yet. The paused job keeps accepting them.
     */
    DRAINING_IN_FLIGHT_WORK("draining_in_flight_work"),
    /** Paused and fully settled. */
    PAUSED("paused"),
    /** An express job is using the CPU while the original job waits. */
    RUNNING_EXPRESS_JOB("running_express_job"),
    /** The express job has ended (completed, cancelled or timed out); the resume is next. */
    EXPRESS_COMPLETED("express_completed"),
    /** Actively moving the paused job back into the CPU. */
    RESTORING("restoring"),
    /** One-tick confirmation that the original job is running again. */
    RESUMED("resumed"),
    /** The resume cannot happen right now. State is preserved and the resume is retried. */
    ERROR("error"),
    /** Selected, but this CPU is not currently present in the network. */
    UNAVAILABLE("unavailable"),
    /** Present, but not an AE2 CPU this addon can pause safely. */
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

    /** True while this CPU holds a paused job that the Scheduler intends to resume. */
    public boolean holdsPausedJob() {
        return this == PAUSE_REQUESTED || this == DRAINING_IN_FLIGHT_WORK || this == PAUSED
                || this == RUNNING_EXPRESS_JOB || this == EXPRESS_COMPLETED || this == RESTORING
                || this == ERROR;
    }

    public boolean isPreemptable() {
        return this == RUNNING;
    }
}
