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

@Mixin(ExecutingCraftingJob.class)
public interface ExecutingCraftingJobAccessor {
    @Accessor("link")
    CraftingLink acs$link();

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

    @Invoker("writeToNBT")
    CompoundTag acs$writeToNBT(HolderLookup.Provider registries);
}
