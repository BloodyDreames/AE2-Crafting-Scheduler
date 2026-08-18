package dev.BloodyDreamsWork.ae2_scheduler.platform;

import java.util.List;

import dev.BloodyDreamsWork.ae2_scheduler.SchedulerConfig;

/**
 * The single source of truth for the Scheduler's config layout: section, key, default, range and
 * comment. Each loader walks these entries and feeds them into its own config system, so all three
 * targets produce the same file with the same comments.
 */
public final class SchedulerConfigDefinition {
    public static final String SECTION_SCHEDULER = "scheduler";
    public static final String SECTION_PREEMPTION = "preemption";
    public static final String SECTION_TIMEOUTS = "timeouts";
    public static final String SECTION_MISC = "misc";

    public interface Entry {
        String section();

        String key();

        List<String> comment();
    }

    public record BoolEntry(String section, String key, boolean defaultValue, List<String> comment)
            implements Entry {
    }

    public record IntEntry(String section, String key, int defaultValue, int min, int max,
            List<String> comment) implements Entry {
    }

    public record DoubleEntry(String section, String key, double defaultValue, double min, double max,
            List<String> comment) implements Entry {
    }

    public static final BoolEntry ENABLE_SCHEDULER = new BoolEntry(SECTION_SCHEDULER, "enableScheduler",
            SchedulerConfig.DEF_ENABLE_SCHEDULER, List.of(
                    "Master switch. When false, Crafting Schedulers stay inert and AE2 job submission is",
                    "left completely untouched."));

    public static final BoolEntry ALLOW_AUTOMATIC_PREEMPTION = new BoolEntry(SECTION_SCHEDULER,
            "allowAutomaticPreemption", SchedulerConfig.DEF_ALLOW_AUTOMATIC_PREEMPTION, List.of(
                    "When true, a crafting request that AE2 could not place because every CPU is busy is",
                    "automatically offered to the Schedulers on that network. When false, Schedulers still",
                    "hold and resume jobs but never start a preemption on their own."));

    public static final IntEntry MAX_PAUSED_JOBS_PER_SCHEDULER = new IntEntry(SECTION_SCHEDULER,
            "maxPausedJobsPerScheduler", SchedulerConfig.DEF_MAX_PAUSED_JOBS_PER_SCHEDULER, 1, 64,
            List.of("How many jobs a single Scheduler may hold paused at the same time (one per CPU)."));

    public static final IntEntry MAX_EXPRESS_COMPLEXITY = new IntEntry(SECTION_PREEMPTION,
            "maxExpressComplexity", SchedulerConfig.DEF_MAX_EXPRESS_COMPLEXITY, 1, Integer.MAX_VALUE,
            List.of(
                    "Upper bound on the estimated operation count of a job that is allowed to preempt.",
                    "Operations is the sum of ICraftingPlan.patternTimes(), i.e. how many pattern pushes",
                    "the whole crafting tree needs -- not the number of requested items."));

    public static final IntEntry MINIMUM_JOB_COMPLEXITY_FOR_PREEMPTION = new IntEntry(SECTION_PREEMPTION,
            "minimumJobComplexityForPreemption", SchedulerConfig.DEF_MINIMUM_JOB_COMPLEXITY_FOR_PREEMPTION,
            1, Integer.MAX_VALUE, List.of(
                    "An automatic machine request only pauses a running job when its estimated operation",
                    "count is at least this high. Prevents automation from repeatedly interrupting small",
                    "jobs. A player explicitly pressing Start may still use a managed CPU below this limit."));

    public static final IntEntry PAUSE_PROCESSING_TIMEOUT_TICKS = new IntEntry(SECTION_TIMEOUTS,
            "pauseProcessingTimeoutTicks", SchedulerConfig.DEF_PAUSE_PROCESSING_TIMEOUT_TICKS, 0,
            Integer.MAX_VALUE, List.of(
                    "A paused job keeps accepting results of processing operations that were dispatched",
                    "before the pause. If those results have still not arrived after this many ticks the",
                    "pause is flagged in the GUI so the player can find the stalled machine.",
                    "This is a diagnostic only -- it never cancels or discards anything. 0 disables it."));

    public static final IntEntry EXPRESS_JOB_TIMEOUT_TICKS = new IntEntry(SECTION_TIMEOUTS,
            "expressJobTimeoutTicks", SchedulerConfig.DEF_EXPRESS_JOB_TIMEOUT_TICKS, 0, Integer.MAX_VALUE,
            List.of(
                    "How long an express job may occupy a CPU that holds a paused job before it is",
                    "cancelled and the original job is resumed. This is the guarantee that a stuck express",
                    "craft can never hold the main job hostage forever. 0 disables the timeout."));

    public static final IntEntry ORPHANED_PARK_TIMEOUT_TICKS = new IntEntry(SECTION_TIMEOUTS,
            "orphanedParkTimeoutTicks", SchedulerConfig.DEF_ORPHANED_PARK_TIMEOUT_TICKS, 100,
            Integer.MAX_VALUE, List.of(
                    "A paused job whose owning Scheduler has not checked in for this long is resumed",
                    "automatically. This is the safety net that makes it impossible to lose a CPU to a",
                    "Scheduler that was removed, unpowered or cut off by a network split."));

    public static final IntEntry RESUME_RETRY_TICKS = new IntEntry(SECTION_TIMEOUTS, "resumeRetryTicks",
            SchedulerConfig.DEF_RESUME_RETRY_TICKS, 1, 1200,
            List.of("How often a Scheduler retries a resume that could not be performed yet."));

    public static final DoubleEntry ENERGY_USAGE_PER_TICK = new DoubleEntry(SECTION_MISC,
            "energyUsagePerTick", SchedulerConfig.DEF_ENERGY_USAGE_PER_TICK, 0.0, 1000.0,
            List.of("AE consumed per tick by an active Crafting Scheduler."));

    public static final BoolEntry DEBUG_LOGGING = new BoolEntry(SECTION_MISC, "debugLogging",
            SchedulerConfig.DEF_DEBUG_LOGGING, List.of(
                    "Log every pause/resume decision and transition. Turn this on when hunting for a",
                    "duplication or a deadlock -- the log records every item movement decision the",
                    "Scheduler makes."));

    public static String sectionComment(String section) {
        switch (section) {
            case SECTION_SCHEDULER:
                return "Core behaviour of the Crafting Scheduler.";
            case SECTION_PREEMPTION:
                return "Which jobs may jump the queue, and which jobs may be interrupted.";
            case SECTION_TIMEOUTS:
                return "Timeouts. All values are in server ticks (20 ticks = 1 second).";
            case SECTION_MISC:
                return "Power and diagnostics.";
            default:
                return "";
        }
    }

    private SchedulerConfigDefinition() {
    }
}
