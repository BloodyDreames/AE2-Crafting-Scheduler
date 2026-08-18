package dev.BloodyDreamsWork.ae2_scheduler.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;

import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.inv.ListCraftingInventory;

import dev.BloodyDreamsWork.ae2_scheduler.mixin.ExecutingCraftingJobAccessor;

/**
 * Minecraft 1.21.1 / AE2 19.x flavour of the AE2 serialization calls the park implementation needs.
 *
 * <p>
 * On this branch every AE2 NBT entry point takes a {@link HolderLookup.Provider}, which arrives here
 * as the opaque {@code ctx} the shared code carries around.
 */
public final class Ae2Nbt {
    private Ae2Nbt() {
    }

    private static HolderLookup.Provider registries(Object ctx) {
        return (HolderLookup.Provider) ctx;
    }

    public static ListTag writeInventory(ListCraftingInventory inventory, Object ctx) {
        return inventory.writeToNBT(registries(ctx));
    }

    public static CompoundTag writeJob(ExecutingCraftingJobAccessor job, Object ctx) {
        return job.acs$writeToNBT(registries(ctx));
    }

    public static void writeLogic(CraftingCpuLogic logic, CompoundTag data, Object ctx) {
        logic.writeToNBT(data, registries(ctx));
    }

    public static void readLogic(CraftingCpuLogic logic, CompoundTag data, Object ctx) {
        logic.readFromNBT(data, registries(ctx));
    }

    /** Opaque serialization context for a level: the registry lookup on this version. */
    public static Object contextFor(Level level) {
        return level.registryAccess();
    }

    /**
     * AE2 19.x can suspend a running job on its own. A job we resume must never come back suspended,
     * so the flag is cleared explicitly. AE2 15.x has no such flag and its counterpart is a no-op.
     */
    public static void clearSuspended(ExecutingCraftingJobAccessor job) {
        job.acs$setSuspended(false);
    }
}
