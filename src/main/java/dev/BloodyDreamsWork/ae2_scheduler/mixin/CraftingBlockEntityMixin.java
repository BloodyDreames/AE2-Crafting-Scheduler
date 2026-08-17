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

/**
 * Makes a paused job survive the destruction of the CPU multiblock that holds it.
 *
 * <p>
 * AE2's {@code breakCluster} cancels the running job and drops the CPU's inventory on the ground. It
 * knows nothing about the park slot, so a paused job's items would silently disappear. Restoring the
 * paused job before AE2 runs means AE2's own cancel-and-drop path covers both jobs, and no separate
 * item-dropping logic is needed here.
 */
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

        // An express job may be occupying the slot the paused job needs. Cancelling it returns its own
        // items to the network the way AE2 always does when a CPU is destroyed.
        if (parkable.acs$hasActiveJob()) {
            cluster.cancelJob();
        }

        if (!parkable.acs$unpark()) {
            // Should not happen, but never leave items stranded in the park slot: hand them to the
            // inventory AE2 is about to drop.
            SchedulerLog.warn("Could not restore a paused job while breaking a CPU; dropping its items");
            parkable.acs$evacuateParkForDestruction();
        }
    }
}
