package dev.BloodyDreamsWork.ae2_scheduler.registry;

import java.util.Collection;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import appeng.api.networking.IGrid;

import dev.BloodyDreamsWork.ae2_scheduler.AE2CraftingScheduler;
import dev.BloodyDreamsWork.ae2_scheduler.block.SchedulerBlock;
import dev.BloodyDreamsWork.ae2_scheduler.menu.SchedulerMenu;
import dev.BloodyDreamsWork.ae2_scheduler.scheduler.SchedulerBlockEntity;

/**
 * Fabric implementation of the registry hooks the shared code calls.
 *
 * <p>
 * Fabric registers directly into the vanilla registries rather than through a deferred register, and
 * a menu that needs extra data on open uses Fabric API's {@code ExtendedScreenHandlerType} instead
 * of Forge's {@code IForgeMenuType}.
 */
public final class ModRegistry {
    private static final ResourceLocation ID = new ResourceLocation(AE2CraftingScheduler.MODID,
            "crafting_scheduler");

    public static final SchedulerBlock CRAFTING_SCHEDULER = new SchedulerBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(2.2f, 11f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops());

    public static final BlockItem CRAFTING_SCHEDULER_ITEM = new BlockItem(CRAFTING_SCHEDULER,
            new Item.Properties());

    public static final BlockEntityType<SchedulerBlockEntity> CRAFTING_SCHEDULER_BE = BlockEntityType.Builder
            .of(SchedulerBlockEntity::new, CRAFTING_SCHEDULER).build(null);

    public static final MenuType<SchedulerMenu> CRAFTING_SCHEDULER_MENU = new ExtendedScreenHandlerType<>(
            SchedulerMenu::new);

    private static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            new ResourceLocation(AE2CraftingScheduler.MODID, "main"));

    private ModRegistry() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.BLOCK, ID, CRAFTING_SCHEDULER);
        Registry.register(BuiltInRegistries.ITEM, ID, CRAFTING_SCHEDULER_ITEM);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ID, CRAFTING_SCHEDULER_BE);
        Registry.register(BuiltInRegistries.MENU, ID, CRAFTING_SCHEDULER_MENU);

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY.location(),
                FabricItemGroup.builder()
                        .title(Component.translatable("itemGroup.ae2_crafting_scheduler"))
                        .icon(() -> CRAFTING_SCHEDULER_ITEM.getDefaultInstance())
                        .build());
        ItemGroupEvents.modifyEntriesEvent(TAB_KEY)
                .register(entries -> entries.accept(CRAFTING_SCHEDULER_ITEM));
    }

    // --- hooks used by the shared code ---

    public static Block schedulerBlock() {
        return CRAFTING_SCHEDULER;
    }

    public static BlockEntityType<SchedulerBlockEntity> schedulerBlockEntityType() {
        return CRAFTING_SCHEDULER_BE;
    }

    public static MenuType<SchedulerMenu> schedulerMenuType() {
        return CRAFTING_SCHEDULER_MENU;
    }

    public static Collection<SchedulerBlockEntity> schedulers(IGrid grid) {
        return grid.getMachines(SchedulerBlockEntity.class);
    }

    /**
     * Fabric has no {@code openMenu(MenuProvider, Consumer<FriendlyByteBuf>)} extension, so the
     * block entity is wrapped in an {@link ExtendedScreenHandlerFactory} that writes the same extra
     * data the other two loaders send.
     */
    public static void openSchedulerMenu(ServerPlayer player, SchedulerBlockEntity scheduler) {
        player.openMenu(new ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayer serverPlayer, FriendlyByteBuf buf) {
                buf.writeBlockPos(scheduler.getBlockPos());
            }

            @Override
            public Component getDisplayName() {
                return scheduler.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
                return scheduler.createMenu(containerId, inventory, p);
            }
        });
    }
}
