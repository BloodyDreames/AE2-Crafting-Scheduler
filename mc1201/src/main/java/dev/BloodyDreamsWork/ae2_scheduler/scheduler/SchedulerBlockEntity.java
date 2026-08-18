package dev.BloodyDreamsWork.ae2_scheduler.scheduler;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Minecraft 1.20.1 binding for the Scheduler block entity. All behaviour lives in
 * {@link AbstractSchedulerBlockEntity}; only the NBT hook signatures are version specific.
 *
 * <p>
 * 1.20.1 has no {@code loadAdditional}, so {@code load} is used instead.
 */
public class SchedulerBlockEntity extends AbstractSchedulerBlockEntity {
    public SchedulerBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeSchedulerNbt(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        readSchedulerNbt(tag);
    }
}
