package dev.BloodyDreamsWork.ae2_scheduler.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.loader.api.FabricLoader;

import dev.BloodyDreamsWork.ae2_scheduler.SchedulerConfig;
import dev.BloodyDreamsWork.ae2_scheduler.SchedulerLog;

/**
 * Binds {@link SchedulerConfig} on Fabric.
 *
 * <p>
 * Fabric has no configuration system of its own, so this reads and writes the same commented TOML
 * layout that NeoForge's {@code ModConfigSpec} and Forge's {@code ForgeConfigSpec} produce from
 * {@link SchedulerConfigDefinition}. That keeps a config file portable between all three targets.
 * Only the small subset of TOML those files use is handled: section headers, and {@code key = value}
 * with a boolean, integer or floating point value.
 */
public final class FabricConfig implements SchedulerConfigValues {
    private static final String FILE_NAME = "ae2_crafting_scheduler-server.toml";

    private static final List<SchedulerConfigDefinition.Entry> ENTRIES = List.of(
            SchedulerConfigDefinition.ENABLE_SCHEDULER,
            SchedulerConfigDefinition.ALLOW_AUTOMATIC_PREEMPTION,
            SchedulerConfigDefinition.MAX_PAUSED_JOBS_PER_SCHEDULER,
            SchedulerConfigDefinition.MAX_EXPRESS_COMPLEXITY,
            SchedulerConfigDefinition.MINIMUM_JOB_COMPLEXITY_FOR_PREEMPTION,
            SchedulerConfigDefinition.PAUSE_PROCESSING_TIMEOUT_TICKS,
            SchedulerConfigDefinition.EXPRESS_JOB_TIMEOUT_TICKS,
            SchedulerConfigDefinition.ORPHANED_PARK_TIMEOUT_TICKS,
            SchedulerConfigDefinition.RESUME_RETRY_TICKS,
            SchedulerConfigDefinition.ENERGY_USAGE_PER_TICK,
            SchedulerConfigDefinition.DEBUG_LOGGING);

    private static final List<String> SECTION_ORDER = List.of(
            SchedulerConfigDefinition.SECTION_SCHEDULER,
            SchedulerConfigDefinition.SECTION_PREEMPTION,
            SchedulerConfigDefinition.SECTION_TIMEOUTS,
            SchedulerConfigDefinition.SECTION_MISC);

    private final Map<String, Object> values = new HashMap<>();
    private boolean loaded;

    private FabricConfig() {
    }

    public static void bind() {
        var config = new FabricConfig();
        config.load(FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME));
        SchedulerConfig.bind(config);
    }

    private void load(Path path) {
        var parsed = new HashMap<String, String>();
        if (Files.exists(path)) {
            try {
                parsed.putAll(parse(Files.readAllLines(path, StandardCharsets.UTF_8)));
            } catch (IOException e) {
                SchedulerLog.error("Could not read {}; falling back to defaults", path, e);
            }
        }

        boolean complete = true;
        for (var entry : ENTRIES) {
            var raw = parsed.get(key(entry));
            var value = raw == null ? null : coerce(entry, raw);
            if (value == null) {
                complete = false;
                value = defaultOf(entry);
            }
            values.put(key(entry), value);
        }
        loaded = true;

        if (!complete) {
            write(path);
        }
    }

    private void write(Path path) {
        var text = new StringBuilder();
        for (var section : SECTION_ORDER) {
            text.append('#').append(SchedulerConfigDefinition.sectionComment(section)).append('\n');
            text.append('[').append(section).append("]\n");
            for (var entry : ENTRIES) {
                if (!entry.section().equals(section)) {
                    continue;
                }
                for (var line : entry.comment()) {
                    text.append("\t#").append(line).append('\n');
                }
                if (entry instanceof SchedulerConfigDefinition.IntEntry range) {
                    // Matches what the Forge and NeoForge config builders emit for an unbounded
                    // maximum, so all three targets produce the same file.
                    if (range.max() == Integer.MAX_VALUE) {
                        text.append("\t#Range: > ").append(range.min()).append('\n');
                    } else {
                        text.append("\t#Range: ").append(range.min()).append(" ~ ")
                                .append(range.max()).append('\n');
                    }
                } else if (entry instanceof SchedulerConfigDefinition.DoubleEntry range) {
                    text.append("\t#Range: ").append(range.min()).append(" ~ ").append(range.max())
                            .append('\n');
                }
                text.append('\t').append(entry.key()).append(" = ").append(values.get(key(entry)))
                        .append('\n');
            }
            text.append('\n');
        }

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, text.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SchedulerLog.error("Could not write {}", path, e);
        }
    }

    /** Returns a {@code section.key -> raw value} map for the TOML subset these files use. */
    private static Map<String, String> parse(List<String> lines) {
        var result = new LinkedHashMap<String, String>();
        var section = "";
        for (var raw : lines) {
            var line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                continue;
            }
            var split = line.indexOf('=');
            if (split < 0) {
                continue;
            }
            result.put(section + '.' + line.substring(0, split).trim(),
                    line.substring(split + 1).trim());
        }
        return result;
    }

    private static String key(SchedulerConfigDefinition.Entry entry) {
        return entry.section() + '.' + entry.key();
    }

    private static Object defaultOf(SchedulerConfigDefinition.Entry entry) {
        if (entry instanceof SchedulerConfigDefinition.BoolEntry bool) {
            return bool.defaultValue();
        }
        if (entry instanceof SchedulerConfigDefinition.IntEntry number) {
            return number.defaultValue();
        }
        return ((SchedulerConfigDefinition.DoubleEntry) entry).defaultValue();
    }

    /** Parses and range-clamps a raw value, or returns null if it cannot be understood. */
    private static Object coerce(SchedulerConfigDefinition.Entry entry, String raw) {
        try {
            if (entry instanceof SchedulerConfigDefinition.BoolEntry) {
                if ("true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)) {
                    return Boolean.parseBoolean(raw);
                }
                return null;
            }
            if (entry instanceof SchedulerConfigDefinition.IntEntry number) {
                return Math.max(number.min(), Math.min(number.max(), Integer.parseInt(raw)));
            }
            var number = (SchedulerConfigDefinition.DoubleEntry) entry;
            return Math.max(number.min(), Math.min(number.max(), Double.parseDouble(raw)));
        } catch (NumberFormatException e) {
            SchedulerLog.warn("Ignoring unreadable config value {} = {}", key(entry), raw);
            return null;
        }
    }

    private boolean bool(SchedulerConfigDefinition.BoolEntry entry) {
        return (Boolean) values.getOrDefault(key(entry), entry.defaultValue());
    }

    private int integer(SchedulerConfigDefinition.IntEntry entry) {
        return (Integer) values.getOrDefault(key(entry), entry.defaultValue());
    }

    private double decimal(SchedulerConfigDefinition.DoubleEntry entry) {
        return (Double) values.getOrDefault(key(entry), entry.defaultValue());
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    @Override
    public boolean enableScheduler() {
        return bool(SchedulerConfigDefinition.ENABLE_SCHEDULER);
    }

    @Override
    public boolean allowAutomaticPreemption() {
        return bool(SchedulerConfigDefinition.ALLOW_AUTOMATIC_PREEMPTION);
    }

    @Override
    public int maxPausedJobsPerScheduler() {
        return integer(SchedulerConfigDefinition.MAX_PAUSED_JOBS_PER_SCHEDULER);
    }

    @Override
    public long maxExpressComplexity() {
        return integer(SchedulerConfigDefinition.MAX_EXPRESS_COMPLEXITY);
    }

    @Override
    public long minimumJobComplexityForPreemption() {
        return integer(SchedulerConfigDefinition.MINIMUM_JOB_COMPLEXITY_FOR_PREEMPTION);
    }

    @Override
    public int pauseProcessingTimeoutTicks() {
        return integer(SchedulerConfigDefinition.PAUSE_PROCESSING_TIMEOUT_TICKS);
    }

    @Override
    public int expressJobTimeoutTicks() {
        return integer(SchedulerConfigDefinition.EXPRESS_JOB_TIMEOUT_TICKS);
    }

    @Override
    public int orphanedParkTimeoutTicks() {
        return integer(SchedulerConfigDefinition.ORPHANED_PARK_TIMEOUT_TICKS);
    }

    @Override
    public int resumeRetryTicks() {
        return integer(SchedulerConfigDefinition.RESUME_RETRY_TICKS);
    }

    @Override
    public double energyUsagePerTick() {
        return decimal(SchedulerConfigDefinition.ENERGY_USAGE_PER_TICK);
    }

    @Override
    public boolean debugLogging() {
        return bool(SchedulerConfigDefinition.DEBUG_LOGGING);
    }
}
