package dev.BloodyDreamsWork.ae2_scheduler;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import dev.BloodyDreamsWork.ae2_scheduler.net.ModNetwork;
import dev.BloodyDreamsWork.ae2_scheduler.platform.ForgeConfig;
import dev.BloodyDreamsWork.ae2_scheduler.registry.ModRegistry;

@Mod(AE2CraftingScheduler.MODID)
public class ForgeEntrypoint {
    public ForgeEntrypoint() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModRegistry.register(modEventBus);
        ModNetwork.register();

        ForgeConfig.bind();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ForgeConfig.SPEC);

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
