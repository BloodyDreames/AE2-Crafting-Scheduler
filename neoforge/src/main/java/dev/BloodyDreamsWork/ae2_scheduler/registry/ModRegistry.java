package dev.BloodyDreamsWork.ae2_scheduler.registry;

import java.util.Collection;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.api.networking.IGrid;

import dev.BloodyDreamsWork.ae2_scheduler.AE2CraftingScheduler;
import dev.BloodyDreamsWork.ae2_scheduler.block.SchedulerBlock;
import dev.BloodyDreamsWork.ae2_scheduler.menu.SchedulerMenu;
import dev.BloodyDreamsWork.ae2_scheduler.scheduler.SchedulerBlockEntity;

/**
 * NeoForge implementation of the registry hooks the shared code calls. Every loader module provides
 * a class with this exact name and signature set.
 */
public final class ModRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister
            .createBlocks(AE2CraftingScheduler.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister
            .createItems(AE2CraftingScheduler.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
            .create(Registries.BLOCK_ENTITY_TYPE, AE2CraftingScheduler.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister
            .create(Registries.MENU, AE2CraftingScheduler.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, AE2CraftingScheduler.MODID);

    public static final DeferredBlock<SchedulerBlock> CRAFTING_SCHEDULER = BLOCKS.register(
            "crafting_scheduler",
            () -> new SchedulerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(2.2f, 11f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    public static final DeferredItem<BlockItem> CRAFTING_SCHEDULER_ITEM = ITEMS.registerSimpleBlockItem(
            "crafting_scheduler", CRAFTING_SCHEDULER);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SchedulerBlockEntity>> CRAFTING_SCHEDULER_BE = BLOCK_ENTITIES
            .register("crafting_scheduler",
                    () -> BlockEntityType.Builder
                            .of(SchedulerBlockEntity::new, CRAFTING_SCHEDULER.get())
                            .build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<SchedulerMenu>> CRAFTING_SCHEDULER_MENU = MENUS
            .register("crafting_scheduler", () -> IMenuTypeExtension.create(SchedulerMenu::new));

    @SuppressWarnings("unused")
    private static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae2_crafting_scheduler"))
                    .withTabsBefore(CreativeModeTabs.REDSTONE_BLOCKS)
                    .icon(() -> CRAFTING_SCHEDULER_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(CRAFTING_SCHEDULER_ITEM.get()))
                    .build());

    private ModRegistry() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        TABS.register(modEventBus);
    }

    // --- hooks used by the shared code ---

    public static Block schedulerBlock() {
        return CRAFTING_SCHEDULER.get();
    }

    public static BlockEntityType<SchedulerBlockEntity> schedulerBlockEntityType() {
        return CRAFTING_SCHEDULER_BE.get();
    }

    public static MenuType<SchedulerMenu> schedulerMenuType() {
        return CRAFTING_SCHEDULER_MENU.get();
    }

    public static Collection<SchedulerBlockEntity> schedulers(IGrid grid) {
        return grid.getMachines(SchedulerBlockEntity.class);
    }

    public static void openSchedulerMenu(ServerPlayer player, SchedulerBlockEntity scheduler) {
        player.openMenu(scheduler, buf -> buf.writeBlockPos(scheduler.getBlockPos()));
    }
}
