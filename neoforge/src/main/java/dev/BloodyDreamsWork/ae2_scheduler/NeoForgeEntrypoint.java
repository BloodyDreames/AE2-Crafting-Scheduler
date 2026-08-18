package dev.BloodyDreamsWork.ae2_scheduler;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import appeng.api.AECapabilities;

import dev.BloodyDreamsWork.ae2_scheduler.platform.NeoForgeConfig;
import dev.BloodyDreamsWork.ae2_scheduler.registry.ModRegistry;

@Mod(AE2CraftingScheduler.MODID)
public class NeoForgeEntrypoint {
    public NeoForgeEntrypoint(IEventBus modEventBus, ModContainer modContainer) {
        ModRegistry.register(modEventBus);

        modEventBus.addListener(NeoForgeEntrypoint::registerCapabilities);

        NeoForgeConfig.bind();
        modContainer.registerConfig(ModConfig.Type.SERVER, NeoForgeConfig.SPEC);
    }

    /**
     * AE2 19.x resolves in-world grid node hosts purely through this capability, so a block entity
     * that implements {@code IInWorldGridNodeHost} still has to register for it. AE2 15.x checks
     * {@code instanceof} first, which is why the 1.20.1 modules do not need an equivalent.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModRegistry.schedulerBlockEntityType(), (blockEntity, context) -> blockEntity);
    }
}
