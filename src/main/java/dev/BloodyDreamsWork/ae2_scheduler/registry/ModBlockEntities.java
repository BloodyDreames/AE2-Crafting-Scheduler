package dev.BloodyDreamsWork.ae2_scheduler.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.BloodyDreamsWork.ae2_scheduler.AE2CraftingScheduler;
import dev.BloodyDreamsWork.ae2_scheduler.scheduler.SchedulerBlockEntity;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
            .create(Registries.BLOCK_ENTITY_TYPE, AE2CraftingScheduler.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SchedulerBlockEntity>> CRAFTING_SCHEDULER = BLOCK_ENTITIES
            .register("crafting_scheduler",
                    () -> BlockEntityType.Builder
                            .of(SchedulerBlockEntity::new, ModBlocks.CRAFTING_SCHEDULER.get())
                            .build(null));

    private ModBlockEntities() {
    }
}
