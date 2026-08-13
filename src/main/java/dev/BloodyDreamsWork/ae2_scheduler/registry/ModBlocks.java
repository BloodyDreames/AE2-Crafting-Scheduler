package dev.BloodyDreamsWork.ae2_scheduler.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.BloodyDreamsWork.ae2_scheduler.AE2CraftingScheduler;
import dev.BloodyDreamsWork.ae2_scheduler.block.SchedulerBlock;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AE2CraftingScheduler.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AE2CraftingScheduler.MODID);

    /** Matches the feel of AE2's machine blocks without being a visual overhaul of anything. */
    public static final DeferredBlock<SchedulerBlock> CRAFTING_SCHEDULER = BLOCKS.register(
            "crafting_scheduler",
            () -> new SchedulerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(2.2f, 11f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    public static final DeferredItem<BlockItem> CRAFTING_SCHEDULER_ITEM = ITEMS.registerSimpleBlockItem(
            "crafting_scheduler", CRAFTING_SCHEDULER);

    private ModBlocks() {
    }
}
