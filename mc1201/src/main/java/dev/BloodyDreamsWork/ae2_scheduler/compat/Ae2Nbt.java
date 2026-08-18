package dev.BloodyDreamsWork.ae2_scheduler.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;

import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.inv.ListCraftingInventory;

import dev.BloodyDreamsWork.ae2_scheduler.mixin.ExecutingCraftingJobAccessor;

/**
 * Minecraft 1.20.1 / AE2 15.x flavour of the AE2 serialization calls the park implementation needs.
 *
 * <p>
 * On this branch none of the AE2 NBT entry points take a registry lookup, so {@code ctx} is always
 * {@code null} and is ignored.
 */
public final class Ae2Nbt {
    private Ae2Nbt() {
    }

    public static ListTag writeInventory(ListCraftingInventory inventory, Object ctx) {
        return inventory.writeToNBT();
    }

    public static CompoundTag writeJob(ExecutingCraftingJobAccessor job, Object ctx) {
        return job.acs$writeToNBT();
    }

    public static void writeLogic(CraftingCpuLogic logic, CompoundTag data, Object ctx) {
        logic.writeToNBT(data);
    }

    public static void readLogic(CraftingCpuLogic logic, CompoundTag data, Object ctx) {
        logic.readFromNBT(data);
    }

    /** Opaque serialization context for a level: nothing is needed on this version. */
    public static Object contextFor(Level level) {
        return null;
    }

    /**
     * AE2 15.x has no per-job suspend flag (it was added in AE2 19.x), so there is nothing to clear
     * when a parked job is resumed.
     */
    public static void clearSuspended(ExecutingCraftingJobAccessor job) {
    }
}
