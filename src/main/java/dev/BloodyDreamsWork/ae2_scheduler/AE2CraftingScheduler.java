package dev.BloodyDreamsWork.ae2_scheduler;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.api.AECapabilities;

import dev.BloodyDreamsWork.ae2_scheduler.registry.ModBlockEntities;
import dev.BloodyDreamsWork.ae2_scheduler.registry.ModBlocks;
import dev.BloodyDreamsWork.ae2_scheduler.registry.ModMenus;

@Mod(AE2CraftingScheduler.MODID)
public class AE2CraftingScheduler {
    public static final String MODID = "ae2_crafting_scheduler";

    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, MODID);

    @SuppressWarnings("unused")
    private static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae2_crafting_scheduler"))
                    .withTabsBefore(CreativeModeTabs.REDSTONE_BLOCKS)
                    .icon(() -> ModBlocks.CRAFTING_SCHEDULER_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(ModBlocks.CRAFTING_SCHEDULER_ITEM.get()))
                    .build());

    public AE2CraftingScheduler(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        TABS.register(modEventBus);

        modEventBus.addListener(AE2CraftingScheduler::registerCapabilities);

        modContainer.registerConfig(ModConfig.Type.SERVER, SchedulerConfig.SPEC);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.CRAFTING_SCHEDULER.get(), (blockEntity, context) -> blockEntity);
    }
}
