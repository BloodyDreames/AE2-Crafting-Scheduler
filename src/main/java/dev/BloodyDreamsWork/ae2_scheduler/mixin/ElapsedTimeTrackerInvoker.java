package dev.BloodyDreamsWork.ae2_scheduler.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.api.stacks.AEKeyType;
import appeng.crafting.execution.ElapsedTimeTracker;

/**
 * Lets the parked-job routing book completed work exactly like AE2 does, so a job's progress bar does
 * not freeze or jump when it is paused and resumed.
 */
@Mixin(ElapsedTimeTracker.class)
public interface ElapsedTimeTrackerInvoker {

    @Invoker("decrementItems")
    void acs$decrementItems(long itemDiff, AEKeyType keyType);
}
