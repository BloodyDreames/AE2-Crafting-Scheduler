package dev.BloodyDreamsWork.ae2_scheduler.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.BloodyDreamsWork.ae2_scheduler.AE2CraftingScheduler;
import dev.BloodyDreamsWork.ae2_scheduler.menu.SchedulerMenu;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister
            .create(Registries.MENU, AE2CraftingScheduler.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<SchedulerMenu>> CRAFTING_SCHEDULER = MENUS
            .register("crafting_scheduler", () -> IMenuTypeExtension.create(SchedulerMenu::new));

    private ModMenus() {
    }
}
