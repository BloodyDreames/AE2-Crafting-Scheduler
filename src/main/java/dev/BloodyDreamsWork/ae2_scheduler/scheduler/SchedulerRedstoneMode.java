package dev.BloodyDreamsWork.ae2_scheduler.scheduler;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * Redstone control for the Scheduler, so a player can take it out of the loop from automation without
 * breaking it.
 *
 * <p>
 * Switching the Scheduler off never abandons a paused job: it resumes everything it holds first (see
 * {@code SchedulerBlockEntity#releaseAll}).
 */
public enum SchedulerRedstoneMode implements StringRepresentable {
    IGNORE("ignore"),
    ACTIVE_WITH_SIGNAL("active_with_signal"),
    ACTIVE_WITHOUT_SIGNAL("active_without_signal");

    public static final StringRepresentable.EnumCodec<SchedulerRedstoneMode> CODEC = StringRepresentable
            .fromEnum(SchedulerRedstoneMode::values);

    private final String name;

    SchedulerRedstoneMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public boolean allows(boolean hasSignal) {
        return switch (this) {
            case IGNORE -> true;
            case ACTIVE_WITH_SIGNAL -> hasSignal;
            case ACTIVE_WITHOUT_SIGNAL -> !hasSignal;
        };
    }

    public SchedulerRedstoneMode next() {
        var values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public Component displayName() {
        return Component.translatable("gui.ae2_crafting_scheduler.redstone." + name);
    }
}
