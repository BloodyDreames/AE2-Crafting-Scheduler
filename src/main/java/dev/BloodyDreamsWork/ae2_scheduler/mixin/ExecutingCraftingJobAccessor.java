package dev.BloodyDreamsWork.ae2_scheduler.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;

/**
 * Read/write access to the package-private state of an {@link ExecutingCraftingJob}.
 *
 * <p>
 * Nothing here changes AE2 behaviour; these are the exact fields AE2 itself reads in
 * {@code CraftingCpuLogic.insert}, and the parked-job routing has to run the same accounting.
 */
@Mixin(ExecutingCraftingJob.class)
public interface ExecutingCraftingJobAccessor {

    @Accessor("link")
    CraftingLink acs$link();

    /** The ledger of results this job still expects. This is what prevents duplication. */
    @Accessor("waitingFor")
    ListCraftingInventory acs$waitingFor();

    @Accessor("timeTracker")
    ElapsedTimeTracker acs$timeTracker();

    @Accessor("finalOutput")
    GenericStack acs$finalOutput();

    @Accessor("remainingAmount")
    long acs$remainingAmount();

    @Accessor("remainingAmount")
    void acs$setRemainingAmount(long remainingAmount);

    @Accessor("suspended")
    boolean acs$suspended();

    @Accessor("suspended")
    void acs$setSuspended(boolean suspended);

    /**
     * AE2's own serializer. Reused so a parked job is written in exactly the shape AE2's loader
     * expects, which is what lets the park be restored through {@code CraftingCpuLogic.readFromNBT}.
     */
    @Invoker("writeToNBT")
    CompoundTag acs$writeToNBT(HolderLookup.Provider registries);
}
