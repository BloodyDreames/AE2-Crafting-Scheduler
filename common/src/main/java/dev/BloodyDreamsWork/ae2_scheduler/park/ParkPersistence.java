package dev.BloodyDreamsWork.ae2_scheduler.park;

import net.minecraft.nbt.CompoundTag;

/**
 * Bridge between the shared park implementation and the per-Minecraft-version mixin that hooks
 * {@code CraftingCpuLogic.writeToNBT}/{@code readFromNBT}.
 *
 * <p>
 * Those two AE2 methods gained a {@code HolderLookup.Provider} parameter in Minecraft 1.21.1, so
 * their injection descriptors differ per version and the injectors have to live in a version
 * specific mixin. The park state itself is shared and reached through this interface.
 *
 * <p>
 * {@code ctx} carries the {@code HolderLookup.Provider} on 1.21.1 and is {@code null} on 1.20.1; it
 * is only ever passed straight back into {@code Ae2Nbt}.
 */
public interface ParkPersistence {
    void acs$writeParkData(CompoundTag data, Object ctx);

    void acs$readParkData(CompoundTag data, Object ctx);
}
