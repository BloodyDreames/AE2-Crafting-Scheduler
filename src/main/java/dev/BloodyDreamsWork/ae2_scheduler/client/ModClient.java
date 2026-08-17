package dev.BloodyDreamsWork.ae2_scheduler.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import appeng.init.client.InitScreens;

import dev.BloodyDreamsWork.ae2_scheduler.AE2CraftingScheduler;
import dev.BloodyDreamsWork.ae2_scheduler.registry.ModMenus;

@EventBusSubscriber(modid = AE2CraftingScheduler.MODID, value = Dist.CLIENT)
public final class ModClient {
    private ModClient() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        InitScreens.register(event, ModMenus.CRAFTING_SCHEDULER.get(), SchedulerScreen::new,
                "/screens/ae2_crafting_scheduler/scheduler.json");
    }
}
