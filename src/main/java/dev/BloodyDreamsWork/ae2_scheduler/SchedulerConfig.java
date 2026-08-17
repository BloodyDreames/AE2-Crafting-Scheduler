package dev.BloodyDreamsWork.ae2_scheduler;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side configuration.
 *
 * <p>
 * Everything here affects world state, so it lives in the SERVER config: on a dedicated server the
 * values come from the server, and in single player they come from the world's {@code serverconfig}
 * folder.
 */
public final class SchedulerConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue ENABLE_SCHEDULER;
    private static final ModConfigSpec.IntValue MAX_PAUSED_JOBS_PER_SCHEDULER;
    private static final ModConfigSpec.IntValue MAX_EXPRESS_COMPLEXITY;
    private static final ModConfigSpec.IntValue MINIMUM_JOB_COMPLEXITY_FOR_PREEMPTION;
    private static final ModConfigSpec.IntValue PAUSE_PROCESSING_TIMEOUT_TICKS;
    private static final ModConfigSpec.BooleanValue ALLOW_AUTOMATIC_PREEMPTION;
    private static final ModConfigSpec.DoubleValue ENERGY_USAGE_PER_TICK;
    private static final ModConfigSpec.IntValue EXPRESS_JOB_TIMEOUT_TICKS;
    private static final ModConfigSpec.IntValue ORPHANED_PARK_TIMEOUT_TICKS;
    private static final ModConfigSpec.IntValue RESUME_RETRY_TICKS;
    private static final ModConfigSpec.BooleanValue DEBUG_LOGGING;

    static {
        var builder = new ModConfigSpec.Builder();

        builder.comment("Core behaviour of the Crafting Scheduler.").push("scheduler");

        ENABLE_SCHEDULER = builder
                .comment(
                        "Master switch. When false, Crafting Schedulers stay inert and AE2 job submission is",
                        "left completely untouched.")
                .define("enableScheduler", true);

        ALLOW_AUTOMATIC_PREEMPTION = builder
                .comment(
                        "When true, a crafting request that AE2 could not place because every CPU is busy is",
                        "automatically offered to the Schedulers on that network. When false, Schedulers still",
                        "hold and resume jobs but never start a preemption on their own.")
                .define("allowAutomaticPreemption", true);

        MAX_PAUSED_JOBS_PER_SCHEDULER = builder
                .comment("How many jobs a single Scheduler may hold paused at the same time (one per CPU).")
                .defineInRange("maxPausedJobsPerScheduler", 8, 1, 64);

        builder.pop();

        builder.comment("Which jobs may jump the queue, and which jobs may be interrupted.").push("preemption");

        MAX_EXPRESS_COMPLEXITY = builder
                .comment(
                        "Upper bound on the estimated operation count of a job that is allowed to preempt.",
                        "'Operations' is the sum of ICraftingPlan.patternTimes(), i.e. how many pattern pushes",
                        "the whole crafting tree needs -- not the number of requested items.")
                .defineInRange("maxExpressComplexity", 128, 1, Integer.MAX_VALUE);

        MINIMUM_JOB_COMPLEXITY_FOR_PREEMPTION = builder
                .comment(
                        "An automatic machine request only pauses a running job when its estimated operation",
                        "count is at least this high. Prevents automation from repeatedly interrupting small",
                        "jobs. A player explicitly pressing Start may still use a managed CPU below this limit.")
                .defineInRange("minimumJobComplexityForPreemption", 1000, 1, Integer.MAX_VALUE);

        builder.pop();

        builder.comment("Timeouts. All values are in server ticks (20 ticks = 1 second).").push("timeouts");

        PAUSE_PROCESSING_TIMEOUT_TICKS = builder
                .comment(
                        "A paused job keeps accepting results of processing operations that were dispatched",
                        "before the pause. If those results have still not arrived after this many ticks the",
                        "pause is flagged in the GUI so the player can find the stalled machine.",
                        "This is a diagnostic only -- it never cancels or discards anything. 0 disables it.")
                .defineInRange("pauseProcessingTimeoutTicks", 1200, 0, Integer.MAX_VALUE);

        EXPRESS_JOB_TIMEOUT_TICKS = builder
                .comment(
                        "How long an express job may occupy a CPU that holds a paused job before it is",
                        "cancelled and the original job is resumed. This is the guarantee that a stuck express",
                        "craft can never hold the main job hostage forever. 0 disables the timeout.")
                .defineInRange("expressJobTimeoutTicks", 6000, 0, Integer.MAX_VALUE);

        ORPHANED_PARK_TIMEOUT_TICKS = builder
                .comment(
                        "A paused job whose owning Scheduler has not checked in for this long is resumed",
                        "automatically. This is the safety net that makes it impossible to lose a CPU to a",
                        "Scheduler that was removed, unpowered or cut off by a network split.")
                .defineInRange("orphanedParkTimeoutTicks", 1200, 100, Integer.MAX_VALUE);

        RESUME_RETRY_TICKS = builder
                .comment("How often a Scheduler retries a resume that could not be performed yet.")
                .defineInRange("resumeRetryTicks", 20, 1, 1200);

        builder.pop();

        builder.comment("Power and diagnostics.").push("misc");

        ENERGY_USAGE_PER_TICK = builder
                .comment("AE consumed per tick by an active Crafting Scheduler.")
                .defineInRange("energyUsagePerTick", 1.0, 0.0, 1000.0);

        DEBUG_LOGGING = builder
                .comment(
                        "Log every pause/resume decision and transition. Turn this on when hunting for a",
                        "duplication or a deadlock -- the log records every item movement decision the",
                        "Scheduler makes.")
                .define("debugLogging", false);

        builder.pop();

        SPEC = builder.build();
    }

    private SchedulerConfig() {
    }

    public static boolean enableScheduler() {
        return ENABLE_SCHEDULER.get();
    }

    public static boolean allowAutomaticPreemption() {
        return ALLOW_AUTOMATIC_PREEMPTION.get();
    }

    public static int maxPausedJobsPerScheduler() {
        return MAX_PAUSED_JOBS_PER_SCHEDULER.get();
    }

    public static long maxExpressComplexity() {
        return MAX_EXPRESS_COMPLEXITY.get();
    }

    public static long minimumJobComplexityForPreemption() {
        return MINIMUM_JOB_COMPLEXITY_FOR_PREEMPTION.get();
    }

    public static int pauseProcessingTimeoutTicks() {
        return PAUSE_PROCESSING_TIMEOUT_TICKS.get();
    }

    public static int expressJobTimeoutTicks() {
        return EXPRESS_JOB_TIMEOUT_TICKS.get();
    }

    public static int orphanedParkTimeoutTicks() {
        return ORPHANED_PARK_TIMEOUT_TICKS.get();
    }

    public static int resumeRetryTicks() {
        return RESUME_RETRY_TICKS.get();
    }

    public static double energyUsagePerTick() {
        return ENERGY_USAGE_PER_TICK.get();
    }

    public static boolean debugLogging() {
        return DEBUG_LOGGING.get();
    }

    /**
     * Config access from a Mixin can run before the config is bound (or on a client that has no server
     * config loaded), so every read goes through this guard.
     */
    public static boolean isLoaded() {
        return SPEC.isLoaded();
    }
}
