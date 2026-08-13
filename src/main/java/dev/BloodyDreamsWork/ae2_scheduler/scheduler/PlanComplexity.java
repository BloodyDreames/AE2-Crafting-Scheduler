package dev.BloodyDreamsWork.ae2_scheduler.scheduler;

import appeng.api.networking.crafting.ICraftingPlan;

/**
 * How "big" a crafting plan is.
 *
 * <p>
 * Deliberately not measured in requested items: one ME Controller is a single item with a crafting
 * tree of thousands of operations, and 100 000 Glass is a huge item count with a trivial tree.
 *
 * <p>
 * {@link #operations()} -- the sum of {@link ICraftingPlan#patternTimes()} -- is the number of pattern
 * pushes the whole tree needs. It counts sub-crafts and processing operations alike and is exactly the
 * work AE2's CPU has to perform, so it is the metric the preemption thresholds are expressed in.
 *
 * @param operations    total pattern pushes across the whole crafting tree
 * @param steps         number of distinct patterns involved (the "width" of the tree)
 * @param bytes         CPU storage the plan needs
 * @param multiplePaths whether the planner had a choice of recipes anywhere
 */
public record PlanComplexity(long operations, int steps, long bytes, boolean multiplePaths) {

    public static final PlanComplexity UNKNOWN = new PlanComplexity(0, 0, 0, false);

    public static PlanComplexity of(ICraftingPlan plan) {
        return new PlanComplexity(operations(plan), plan.patternTimes().size(), plan.bytes(),
                plan.multiplePaths());
    }

    public static long operations(ICraftingPlan plan) {
        long total = 0;
        for (var times : plan.patternTimes().values()) {
            total += times;
        }
        return total;
    }

    /**
     * Work still to do on a job that is already running. The plan is gone by then (AE2 discards it at
     * submit time), so this combines the operation count recorded at submit with AE2's own progress
     * tracker.
     */
    public static long estimateRemaining(long totalOperations, float progress) {
        if (totalOperations <= 0) {
            return 0;
        }
        var remaining = (long) Math.ceil(totalOperations * (1.0 - Math.min(1.0f, Math.max(0.0f, progress))));
        return Math.max(0, remaining);
    }
}
