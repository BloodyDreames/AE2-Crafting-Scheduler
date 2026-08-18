package dev.BloodyDreamsWork.ae2_scheduler.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import dev.BloodyDreamsWork.ae2_scheduler.SchedulerLog;
import dev.BloodyDreamsWork.ae2_scheduler.park.ParkableCpu;

@Mixin(CraftingBlockEntity.class)
public abstract class CraftingBlockEntityMixin {
    @Shadow
    public abstract CraftingCPUCluster getCluster();

    @Inject(method = "breakCluster", at = @At("HEAD"))
    private void acs$restoreParkBeforeBreak(CallbackInfo ci) {
        var cluster = getCluster();
        if (cluster == null) {
            return;
        }
        var parkable = ParkableCpu.of(cluster.craftingLogic);
        if (parkable == null || !parkable.acs$isParked()) {
            return;
        }

        SchedulerLog.debug("Crafting CPU holding a paused job is being broken; restoring it first");

        if (parkable.acs$hasActiveJob()) {
            cluster.cancelJob();
        }

        if (!parkable.acs$unpark()) {
            SchedulerLog.warn("Could not restore a paused job while breaking a CPU; dropping its items");
            parkable.acs$evacuateParkForDestruction();
        }
    }
}
