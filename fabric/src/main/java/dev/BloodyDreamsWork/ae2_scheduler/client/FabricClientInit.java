package dev.BloodyDreamsWork.ae2_scheduler.client;

import net.fabricmc.api.ClientModInitializer;

import appeng.init.client.InitScreens;

import dev.BloodyDreamsWork.ae2_scheduler.net.ClientNetworkAccess;
import dev.BloodyDreamsWork.ae2_scheduler.registry.ModRegistry;

/**
 * AE2 15.x registers screens through {@code MenuScreens} directly, so {@code InitScreens.register}
 * takes no event argument here.
 */
public class FabricClientInit implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientNetworkAccess.register();
        InitScreens.register(ModRegistry.schedulerMenuType(), SchedulerScreen::new,
                "/screens/ae2_crafting_scheduler/scheduler.json");
    }
}
