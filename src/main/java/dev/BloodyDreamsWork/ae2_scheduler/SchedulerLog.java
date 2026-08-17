package dev.BloodyDreamsWork.ae2_scheduler;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

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

    public static void warn(String format, Object... args) {
        LOGGER.warn(PREFIX + format, args);
    }

    public static void error(String format, Object... args) {
        LOGGER.error(PREFIX + format, args);
    }
}
