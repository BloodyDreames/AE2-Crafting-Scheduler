package dev.BloodyDreamsWork.ae2_scheduler;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

/**
 * Debug logging for the pause/resume pipeline.
 *
 * <p>
 * Every state transition and every item-routing decision goes through here. It is off by default and
 * enabled with {@code debugLogging} in the server config, because it is the primary tool for tracking
 * down a duplication or a deadlock: the log is meant to be readable end to end, e.g.
 *
 * <pre>
 * [Scheduler] Express request detected: ae2:me_drive x1 (12 operations)
 * [Scheduler] Selected CPU: Main CPU at [12, 64, -30]
 * [Scheduler] Pausing job: minecraft:glass x100000 (progress 46.1%, 3 in-flight)
 * [Scheduler] Job safely paused, 128 intermediate stacks held
 * </pre>
 */
public final class SchedulerLog {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "[Scheduler] ";

    private SchedulerLog() {
    }

    public static boolean enabled() {
        return SchedulerConfig.isLoaded() && SchedulerConfig.debugLogging();
    }

    public static void debug(String format, Object... args) {
        if (enabled()) {
            LOGGER.info(PREFIX + format, args);
        }
    }

    /** Always logged: something went wrong that a player or pack dev needs to know about. */
    public static void warn(String format, Object... args) {
        LOGGER.warn(PREFIX + format, args);
    }

    public static void error(String format, Object... args) {
        LOGGER.error(PREFIX + format, args);
    }
}
