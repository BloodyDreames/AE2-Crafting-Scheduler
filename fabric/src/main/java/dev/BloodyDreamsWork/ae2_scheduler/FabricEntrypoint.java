package dev.BloodyDreamsWork.ae2_scheduler;

import net.fabricmc.api.ModInitializer;

import dev.BloodyDreamsWork.ae2_scheduler.net.ModNetwork;
import dev.BloodyDreamsWork.ae2_scheduler.platform.FabricConfig;
import dev.BloodyDreamsWork.ae2_scheduler.registry.ModRegistry;

public class FabricEntrypoint implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricConfig.bind();
        ModRegistry.register();
        ModNetwork.register();

        DevTestPlots.registerIfPresent();
    }

    /**
     * AE2 15.x has no {@code @TestPlotClass} annotation scan, so the game-test plots have to be
     * handed to AE2 explicitly. They are stripped from release jars, hence the reflective lookup.
     */
    private static final class DevTestPlots {
        static void registerIfPresent() {
            if (!Boolean.getBoolean("appeng.tests")) {
                return;
            }
            try {
                var plots = Class.forName("dev.BloodyDreamsWork.ae2_scheduler.testplots.SchedulerTestPlots");
                appeng.server.testplots.TestPlots.addPlotClass(plots);
            } catch (ClassNotFoundException e) {
                // Release jar: test plots are not shipped.
            }
        }
    }
}
