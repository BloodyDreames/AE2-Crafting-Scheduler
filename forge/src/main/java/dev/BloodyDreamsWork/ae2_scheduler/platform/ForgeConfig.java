package dev.BloodyDreamsWork.ae2_scheduler.platform;

import net.minecraftforge.common.ForgeConfigSpec;

import dev.BloodyDreamsWork.ae2_scheduler.SchedulerConfig;

/**
 * Binds {@link SchedulerConfig} to Forge's config system.
 *
 * <p>
 * Forge 1.20.1 calls the class {@code ForgeConfigSpec} where NeoForge calls it
 * {@code ModConfigSpec}; the builder API is otherwise the same, so the key layout comes from the
 * shared {@link SchedulerConfigDefinition} and the generated TOML matches the other two targets.
 */
public final class ForgeConfig implements SchedulerConfigValues {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue ENABLE_SCHEDULER;
    private static final ForgeConfigSpec.BooleanValue ALLOW_AUTOMATIC_PREEMPTION;
    private static final ForgeConfigSpec.IntValue MAX_PAUSED_JOBS_PER_SCHEDULER;
    private static final ForgeConfigSpec.IntValue MAX_EXPRESS_COMPLEXITY;
    private static final ForgeConfigSpec.IntValue MINIMUM_JOB_COMPLEXITY_FOR_PREEMPTION;
    private static final ForgeConfigSpec.IntValue PAUSE_PROCESSING_TIMEOUT_TICKS;
    private static final ForgeConfigSpec.IntValue EXPRESS_JOB_TIMEOUT_TICKS;
    private static final ForgeConfigSpec.IntValue ORPHANED_PARK_TIMEOUT_TICKS;
    private static final ForgeConfigSpec.IntValue RESUME_RETRY_TICKS;
    private static final ForgeConfigSpec.DoubleValue ENERGY_USAGE_PER_TICK;
    private static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING;

    static {
        var builder = new ForgeConfigSpec.Builder();

        push(builder, SchedulerConfigDefinition.SECTION_SCHEDULER);
        ENABLE_SCHEDULER = bool(builder, SchedulerConfigDefinition.ENABLE_SCHEDULER);
        ALLOW_AUTOMATIC_PREEMPTION = bool(builder, SchedulerConfigDefinition.ALLOW_AUTOMATIC_PREEMPTION);
        MAX_PAUSED_JOBS_PER_SCHEDULER = integer(builder,
                SchedulerConfigDefinition.MAX_PAUSED_JOBS_PER_SCHEDULER);
        builder.pop();

        push(builder, SchedulerConfigDefinition.SECTION_PREEMPTION);
        MAX_EXPRESS_COMPLEXITY = integer(builder, SchedulerConfigDefinition.MAX_EXPRESS_COMPLEXITY);
        MINIMUM_JOB_COMPLEXITY_FOR_PREEMPTION = integer(builder,
                SchedulerConfigDefinition.MINIMUM_JOB_COMPLEXITY_FOR_PREEMPTION);
        builder.pop();

        push(builder, SchedulerConfigDefinition.SECTION_TIMEOUTS);
        PAUSE_PROCESSING_TIMEOUT_TICKS = integer(builder,
                SchedulerConfigDefinition.PAUSE_PROCESSING_TIMEOUT_TICKS);
        EXPRESS_JOB_TIMEOUT_TICKS = integer(builder, SchedulerConfigDefinition.EXPRESS_JOB_TIMEOUT_TICKS);
        ORPHANED_PARK_TIMEOUT_TICKS = integer(builder,
                SchedulerConfigDefinition.ORPHANED_PARK_TIMEOUT_TICKS);
        RESUME_RETRY_TICKS = integer(builder, SchedulerConfigDefinition.RESUME_RETRY_TICKS);
        builder.pop();

        push(builder, SchedulerConfigDefinition.SECTION_MISC);
        ENERGY_USAGE_PER_TICK = decimal(builder, SchedulerConfigDefinition.ENERGY_USAGE_PER_TICK);
        DEBUG_LOGGING = bool(builder, SchedulerConfigDefinition.DEBUG_LOGGING);
        builder.pop();

        SPEC = builder.build();
    }

    private static void push(ForgeConfigSpec.Builder builder, String section) {
        builder.comment(SchedulerConfigDefinition.sectionComment(section)).push(section);
    }

    private static ForgeConfigSpec.BooleanValue bool(ForgeConfigSpec.Builder builder,
            SchedulerConfigDefinition.BoolEntry entry) {
        return builder.comment(entry.comment().toArray(new String[0]))
                .define(entry.key(), entry.defaultValue());
    }

    private static ForgeConfigSpec.IntValue integer(ForgeConfigSpec.Builder builder,
            SchedulerConfigDefinition.IntEntry entry) {
        return builder.comment(entry.comment().toArray(new String[0]))
                .defineInRange(entry.key(), entry.defaultValue(), entry.min(), entry.max());
    }

    private static ForgeConfigSpec.DoubleValue decimal(ForgeConfigSpec.Builder builder,
            SchedulerConfigDefinition.DoubleEntry entry) {
        return builder.comment(entry.comment().toArray(new String[0]))
                .defineInRange(entry.key(), entry.defaultValue(), entry.min(), entry.max());
    }

    public static void bind() {
        SchedulerConfig.bind(new ForgeConfig());
    }

    private ForgeConfig() {
    }

    @Override
    public boolean isLoaded() {
        return SPEC.isLoaded();
    }

    @Override
    public boolean enableScheduler() {
        return ENABLE_SCHEDULER.get();
    }

    @Override
    public boolean allowAutomaticPreemption() {
        return ALLOW_AUTOMATIC_PREEMPTION.get();
    }

    @Override
    public int maxPausedJobsPerScheduler() {
        return MAX_PAUSED_JOBS_PER_SCHEDULER.get();
    }

    @Override
    public long maxExpressComplexity() {
        return MAX_EXPRESS_COMPLEXITY.get();
    }

    @Override
    public long minimumJobComplexityForPreemption() {
        return MINIMUM_JOB_COMPLEXITY_FOR_PREEMPTION.get();
    }

    @Override
    public int pauseProcessingTimeoutTicks() {
        return PAUSE_PROCESSING_TIMEOUT_TICKS.get();
    }

    @Override
    public int expressJobTimeoutTicks() {
        return EXPRESS_JOB_TIMEOUT_TICKS.get();
    }

    @Override
    public int orphanedParkTimeoutTicks() {
        return ORPHANED_PARK_TIMEOUT_TICKS.get();
    }

    @Override
    public int resumeRetryTicks() {
        return RESUME_RETRY_TICKS.get();
    }

    @Override
    public double energyUsagePerTick() {
        return ENERGY_USAGE_PER_TICK.get();
    }

    @Override
    public boolean debugLogging() {
        return DEBUG_LOGGING.get();
    }
}
