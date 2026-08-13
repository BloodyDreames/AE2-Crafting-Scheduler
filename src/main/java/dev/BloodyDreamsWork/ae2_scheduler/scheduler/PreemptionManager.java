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

/**
 * Decides whether a crafting request AE2 could not place should jump the queue.
 *
 * <p>
 * This runs at the tail of {@code CraftingService.submitJob}, i.e. only after AE2's own scheduling has
 * already failed. When no Scheduler is present, none manages a suitable CPU, or preemption is off, the
 * original AE2 result is returned untouched and AE2 behaves exactly as it does without this mod.
 */
public final class PreemptionManager {

    /**
     * The failures that mean "there was simply no free CPU". Anything else (an incomplete plan, a
     * missing ingredient, an offline CPU) is a real problem that pausing another job would not fix.
     */
    private static final Set<CraftingSubmitErrorCode> PREEMPTABLE_FAILURES = EnumSet.of(
            CraftingSubmitErrorCode.NO_CPU_FOUND,
            CraftingSubmitErrorCode.NO_SUITABLE_CPU_FOUND,
            CraftingSubmitErrorCode.CPU_BUSY);

    private PreemptionManager() {
    }

    /**
     * @param originalResult what AE2 decided on its own.
     * @return a replacement result when a preemption happened, or null to keep AE2's result.
     */
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
        // Deterministic order: with two Schedulers on one network the same request must always be
        // handled by the same one.
        schedulers.sort(Comparator.comparing(SchedulerBlockEntity::getBlockPos));

        SchedulerLog.debug("Express request detected: {} x{} ({} operations, {} steps, {} bytes)",
                plan.finalOutput().what().getId(), plan.finalOutput().amount(), complexity.operations(),
                complexity.steps(), complexity.bytes());

        // The player may have pinned the request to one specific CPU in the terminal. Respect that:
        // pausing a different CPU would put the job somewhere they did not ask for.
        var pinned = target instanceof CraftingCPUCluster cluster ? cluster : null;

        for (var scheduler : schedulers) {
            if (!scheduler.isOperational()) {
                continue;
            }

            CraftingCPUCluster victim;
            if (pinned != null) {
                victim = scheduler.manages(pinned.getBoundsMin()) ? pinned : null;
                if (victim != null && !isViableVictim(scheduler, victim, plan)) {
                    victim = null;
                }
            } else {
                victim = scheduler.selectVictim(grid, plan, complexity);
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

    /** The subset of {@link SchedulerBlockEntity#selectVictim} checks that apply to a pinned CPU. */
    private static boolean isViableVictim(SchedulerBlockEntity scheduler, CraftingCPUCluster cluster,
            ICraftingPlan plan) {
        var park = CpuKey.parkable(cluster);
        if (park == null || park.acs$isParked() || park.acs$getParkOwner() != null) {
            return false;
        }
        if (scheduler.getSession(cluster.getBoundsMin()) != null) {
            return false;
        }
        if (!cluster.isActive() || !cluster.isBusy()) {
            return false;
        }
        if (cluster.getAvailableStorage() < plan.bytes()) {
            return false;
        }
        return park.acs$getActiveComplexity() >= SchedulerConfig.minimumJobComplexityForPreemption();
    }
}
