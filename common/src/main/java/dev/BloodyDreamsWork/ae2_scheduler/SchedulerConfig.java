package dev.BloodyDreamsWork.ae2_scheduler;

import dev.BloodyDreamsWork.ae2_scheduler.platform.SchedulerConfigValues;

/**
 * Loader-independent view of the Scheduler configuration.
 *
 * <p>
 * Every loader binds a {@link SchedulerConfigValues} implementation during mod construction. The
 * option names, defaults, ranges and semantics are identical on all three targets, so a config file
 * can be copied between them.
 */
public final class SchedulerConfig {
    public static final boolean DEF_ENABLE_SCHEDULER = true;
    public static final boolean DEF_ALLOW_AUTOMATIC_PREEMPTION = true;
    public static final int DEF_MAX_PAUSED_JOBS_PER_SCHEDULER = 8;
    public static final int DEF_MAX_EXPRESS_COMPLEXITY = 128;
    public static final int DEF_MINIMUM_JOB_COMPLEXITY_FOR_PREEMPTION = 1000;
    public static final int DEF_PAUSE_PROCESSING_TIMEOUT_TICKS = 1200;
    public static final int DEF_EXPRESS_JOB_TIMEOUT_TICKS = 6000;
    public static final int DEF_ORPHANED_PARK_TIMEOUT_TICKS = 1200;
    public static final int DEF_RESUME_RETRY_TICKS = 20;
    public static final double DEF_ENERGY_USAGE_PER_TICK = 1.0;
    public static final boolean DEF_DEBUG_LOGGING = false;

    private static SchedulerConfigValues values;

    private SchedulerConfig() {
    }

    public static void bind(SchedulerConfigValues impl) {
        values = impl;
    }

    public static boolean isLoaded() {
        return values != null && values.isLoaded();
    }

    public static boolean enableScheduler() {
        return isLoaded() ? values.enableScheduler() : DEF_ENABLE_SCHEDULER;
    }

    public static boolean allowAutomaticPreemption() {
        return isLoaded() ? values.allowAutomaticPreemption() : DEF_ALLOW_AUTOMATIC_PREEMPTION;
    }

    public static int maxPausedJobsPerScheduler() {
        return isLoaded() ? values.maxPausedJobsPerScheduler() : DEF_MAX_PAUSED_JOBS_PER_SCHEDULER;
    }

    public static long maxExpressComplexity() {
        return isLoaded() ? values.maxExpressComplexity() : DEF_MAX_EXPRESS_COMPLEXITY;
    }

    public static long minimumJobComplexityForPreemption() {
        return isLoaded() ? values.minimumJobComplexityForPreemption()
                : DEF_MINIMUM_JOB_COMPLEXITY_FOR_PREEMPTION;
    }

    public static int pauseProcessingTimeoutTicks() {
        return isLoaded() ? values.pauseProcessingTimeoutTicks() : DEF_PAUSE_PROCESSING_TIMEOUT_TICKS;
    }

    public static int expressJobTimeoutTicks() {
        return isLoaded() ? values.expressJobTimeoutTicks() : DEF_EXPRESS_JOB_TIMEOUT_TICKS;
    }

    public static int orphanedParkTimeoutTicks() {
        return isLoaded() ? values.orphanedParkTimeoutTicks() : DEF_ORPHANED_PARK_TIMEOUT_TICKS;
    }

    public static int resumeRetryTicks() {
        return isLoaded() ? values.resumeRetryTicks() : DEF_RESUME_RETRY_TICKS;
    }

    public static double energyUsagePerTick() {
        return isLoaded() ? values.energyUsagePerTick() : DEF_ENERGY_USAGE_PER_TICK;
    }

    public static boolean debugLogging() {
        return isLoaded() && values.debugLogging();
    }
}
