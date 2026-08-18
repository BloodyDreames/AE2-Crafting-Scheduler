package dev.BloodyDreamsWork.ae2_scheduler.net;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;

import dev.BloodyDreamsWork.ae2_scheduler.menu.CpuStatus;
import dev.BloodyDreamsWork.ae2_scheduler.scheduler.SchedulerRedstoneMode;

/**
 * Server -&gt; client snapshot of one Scheduler. Loader-independent payload body; each loader wraps it
 * in whatever packet type it needs.
 */
public record SchedulerStatus(boolean operational,
        boolean preemption,
        long maxExpress,
        long minPreempt,
        SchedulerRedstoneMode redstoneMode,
        List<CpuStatus> cpus) {

    public static final SchedulerStatus EMPTY = new SchedulerStatus(false, false, 0, 0,
            SchedulerRedstoneMode.IGNORE, List.of());

    public static void write(FriendlyByteBuf buf, SchedulerStatus value) {
        buf.writeBoolean(value.operational);
        buf.writeBoolean(value.preemption);
        buf.writeVarLong(value.maxExpress);
        buf.writeVarLong(value.minPreempt);
        buf.writeEnum(value.redstoneMode);
        buf.writeVarInt(value.cpus.size());
        for (var cpu : value.cpus) {
            CpuStatus.write(buf, cpu);
        }
    }

    public static SchedulerStatus read(FriendlyByteBuf buf) {
        var operational = buf.readBoolean();
        var preemption = buf.readBoolean();
        var maxExpress = buf.readVarLong();
        var minPreempt = buf.readVarLong();
        var redstoneMode = buf.readEnum(SchedulerRedstoneMode.class);
        var count = buf.readVarInt();
        var cpus = new ArrayList<CpuStatus>(Math.min(count, 512));
        for (int i = 0; i < count; i++) {
            cpus.add(CpuStatus.read(buf));
        }
        return new SchedulerStatus(operational, preemption, maxExpress, minPreempt, redstoneMode, cpus);
    }
}
