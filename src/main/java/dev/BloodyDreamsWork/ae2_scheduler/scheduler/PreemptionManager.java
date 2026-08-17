package dev.BloodyDreamsWork.ae2_scheduler.scheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import dev.BloodyDreamsWork.ae2_scheduler.SchedulerConfig;
import dev.BloodyDreamsWork.ae2_scheduler.SchedulerLog;

public final class PreemptionManager {
    private static final Set<CraftingSubmitErrorCode> PREEMPTABLE_FAILURES = EnumSet.of(
            CraftingSubmitErrorCode.NO_CPU_FOUND,
            CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND,
            CraftingSubmitErrorCode.CPU_BUSY);

    private PreemptionManager() {
    }

    @Nullable
    public static ICraftingSubmitResult offer(IGrid grid, ICraftingPlan plan,
            @Nullable ICraftingRequester requester, @Nullable ICraftingCPU target, IActionSource src,
            ICraftingSubmitResult originalResult) {
        if (!SchedulerConfig.isLoaded() || !SchedulerConfig.enableScheduler()
                || !SchedulerConfig.allowAutomaticPreemption()) {
            return null;
        }
        if (originalResult == null || originalResult.successful()
                || !PREEMPTABLE_FAILURES.contains(originalResult.errorCode())) {
            return null;
        }
        if (plan.simulation()) {
            return null;
        }

        var complexity = PlanComplexity.of(plan);
        if (complexity.operations() > SchedulerConfig.maxExpressComplexity()) {
            SchedulerLog.debug("Not an express craft: {} needs {} operations, limit is {}",
                    plan.finalOutput().what().getDisplayName().getString(), complexity.operations(),
                    SchedulerConfig.maxExpressComplexity());
            return null;
        }

        var schedulers = new ArrayList<>(grid.getMachines(SchedulerBlockEntity.class));
        if (schedulers.isEmpty()) {
            return null;
        }
        schedulers.sort(Comparator.comparing(SchedulerBlockEntity::getBlockPos));

        SchedulerLog.debug("Express request detected: {} x{} ({} operations, {} steps, {} bytes)",
                plan.finalOutput().what().getId(), plan.finalOutput().amount(), complexity.operations(),
                complexity.steps(), complexity.bytes());

        var pinned = target instanceof CraftingCPUCluster cluster ? cluster : null;
        boolean playerInitiated = src.player().isPresent();

        for (var scheduler : schedulers) {
            if (!scheduler.isOperational()) {
                continue;
            }

            CraftingCPUCluster victim;
            if (pinned != null) {
                victim = scheduler.manages(pinned.getBoundsMin()) ? pinned : null;
                if (victim != null && !isViableVictim(scheduler, victim, plan, playerInitiated)) {
                    victim = null;
                }
            } else {
                victim = scheduler.selectVictim(grid, plan, complexity, playerInitiated);
            }

            if (victim == null) {
                continue;
            }

            SchedulerLog.debug("Selected CPU: {} at {}",
                    victim.getName() != null ? victim.getName().getString() : "unnamed", victim.getBoundsMin());

            var result = scheduler.beginExpress(grid, victim, plan, requester, src, complexity);
            if (result != null) {
                return result;
            }
        }

        SchedulerLog.debug("No Scheduler could make room for this request; leaving AE2's answer ({}) alone",
                originalResult.errorCode());
        return null;
    }

    public static boolean canPreempt(IGrid grid, ICraftingPlan plan, ICraftingCPU target, IActionSource src) {
        if (grid == null || plan == null || target == null || src == null) {
            return false;
        }
        if (!SchedulerConfig.isLoaded() || !SchedulerConfig.enableScheduler()
                || !SchedulerConfig.allowAutomaticPreemption() || plan.simulation()) {
            return false;
        }
        if (PlanComplexity.operations(plan) > SchedulerConfig.maxExpressComplexity()) {
            return false;
        }
        if (!(target instanceof CraftingCPUCluster cluster)) {
            return false;
        }

        boolean playerInitiated = src.player().isPresent();
        for (var scheduler : grid.getMachines(SchedulerBlockEntity.class)) {
            if (scheduler.isOperational() && scheduler.canPreempt(cluster, plan, playerInitiated)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isViableVictim(SchedulerBlockEntity scheduler, CraftingCPUCluster cluster,
            ICraftingPlan plan, boolean playerInitiated) {
        return scheduler.canPreempt(cluster, plan, playerInitiated);
    }
}
