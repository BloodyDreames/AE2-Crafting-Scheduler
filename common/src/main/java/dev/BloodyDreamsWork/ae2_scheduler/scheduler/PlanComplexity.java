package dev.BloodyDreamsWork.ae2_scheduler.scheduler;

import appeng.api.networking.crafting.ICraftingPlan;

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

    public static long estimateRemaining(long totalOperations, float progress) {
        if (totalOperations <= 0) {
            return 0;
        }
        var remaining = (long) Math.ceil(totalOperations * (1.0 - Math.min(1.0f, Math.max(0.0f, progress))));
        return Math.max(0, remaining);
    }
}
