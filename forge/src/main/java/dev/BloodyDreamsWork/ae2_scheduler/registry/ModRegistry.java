package dev.BloodyDreamsWork.ae2_scheduler.registry;

import java.util.Collection;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import appeng.api.networking.IGrid;

import dev.BloodyDreamsWork.ae2_scheduler.AE2CraftingScheduler;
import dev.BloodyDreamsWork.ae2_scheduler.block.SchedulerBlock;
import dev.BloodyDreamsWork.ae2_scheduler.menu.SchedulerMenu;
import dev.BloodyDreamsWork.ae2_scheduler.scheduler.SchedulerBlockEntity;

/**
 * Forge 1.20.1 implementation of the registry hooks the shared code calls.
 *
 * <p>
 * Forge uses {@code RegistryObject} and {@code ForgeRegistries} where NeoForge uses
 * {@code DeferredHolder} and {@code Registries}, and menus are created through
 * {@code IForgeMenuType} rather than {@code IMenuTypeExtension}.
 */
public final class ModRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister
            .create(ForgeRegistries.BLOCKS, AE2CraftingScheduler.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister
            .create(ForgeRegistries.ITEMS, AE2CraftingScheduler.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
            .create(ForgeRegistries.BLOCK_ENTITY_TYPES, AE2CraftingScheduler.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister
            .create(ForgeRegistries.MENU_TYPES, AE2CraftingScheduler.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, AE2CraftingScheduler.MODID);

    public static final RegistryObject<SchedulerBlock> CRAFTING_SCHEDULER = BLOCKS.register(
            "crafting_scheduler",
            () -> new SchedulerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(2.2f, 11f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<BlockItem> CRAFTING_SCHEDULER_ITEM = ITEMS.register(
            "crafting_scheduler",
            () -> new BlockItem(CRAFTING_SCHEDULER.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<SchedulerBlockEntity>> CRAFTING_SCHEDULER_BE = BLOCK_ENTITIES
            .register("crafting_scheduler",
                    () -> BlockEntityType.Builder
                            .of(SchedulerBlockEntity::new, CRAFTING_SCHEDULER.get())
                            .build(null));

    public static final RegistryObject<MenuType<SchedulerMenu>> CRAFTING_SCHEDULER_MENU = MENUS
            .register("crafting_scheduler", () -> IForgeMenuType.create(SchedulerMenu::new));

    @SuppressWarnings("unused")
    private static final RegistryObject<CreativeModeTab> TAB = TABS.register("main",
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
        NetworkHooks.openScreen(player, scheduler, buf -> buf.writeBlockPos(scheduler.getBlockPos()));
    }
}
