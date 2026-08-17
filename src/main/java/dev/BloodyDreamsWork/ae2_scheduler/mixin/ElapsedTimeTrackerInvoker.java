package dev.BloodyDreamsWork.ae2_scheduler.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.api.stacks.AEKeyType;
import appeng.crafting.execution.ElapsedTimeTracker;

@Mixin(ElapsedTimeTracker.class)
public interface ElapsedTimeTrackerInvoker {
    @Invoker("decrementItems")
    void acs$decrementItems(long itemDiff, AEKeyType keyType);
}
