package dev.BloodyDreamsWork.ae2_scheduler.platform;

/** Implemented once per loader on top of that loader's configuration system. */
public interface SchedulerConfigValues {
    boolean isLoaded();

    boolean enableScheduler();

    boolean allowAutomaticPreemption();

    int maxPausedJobsPerScheduler();

    long maxExpressComplexity();

    long minimumJobComplexityForPreemption();

    int pauseProcessingTimeoutTicks();

    int expressJobTimeoutTicks();

    int orphanedParkTimeoutTicks();

    int resumeRetryTicks();

    double energyUsagePerTick();

    boolean debugLogging();
}
