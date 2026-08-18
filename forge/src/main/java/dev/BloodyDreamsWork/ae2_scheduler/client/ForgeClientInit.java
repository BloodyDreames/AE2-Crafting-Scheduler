package dev.BloodyDreamsWork.ae2_scheduler.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import appeng.init.client.InitScreens;

import dev.BloodyDreamsWork.ae2_scheduler.AE2CraftingScheduler;
import dev.BloodyDreamsWork.ae2_scheduler.registry.ModRegistry;

/**
 * AE2 15.x registers screens through {@code MenuScreens} directly, so {@code InitScreens.register}
 * takes no event here and has to be called during client setup rather than from
 * {@code RegisterMenuScreensEvent}.
 */
@Mod.EventBusSubscriber(modid = AE2CraftingScheduler.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForgeClientInit {
    private ForgeClientInit() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> InitScreens.register(ModRegistry.schedulerMenuType(),
                SchedulerScreen::new, "/screens/ae2_crafting_scheduler/scheduler.json"));
    }
}
