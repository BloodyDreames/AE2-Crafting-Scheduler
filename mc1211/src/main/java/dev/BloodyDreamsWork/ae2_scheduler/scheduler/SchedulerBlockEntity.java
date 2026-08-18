package dev.BloodyDreamsWork.ae2_scheduler.scheduler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Minecraft 1.21.1 binding for the Scheduler block entity. All behaviour lives in
 * {@link AbstractSchedulerBlockEntity}; only the NBT hook signatures are version specific.
 */
public class SchedulerBlockEntity extends AbstractSchedulerBlockEntity {
    public SchedulerBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeSchedulerNbt(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readSchedulerNbt(tag);
    }
}
