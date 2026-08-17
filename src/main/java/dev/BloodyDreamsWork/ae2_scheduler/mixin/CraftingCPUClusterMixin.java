package dev.BloodyDreamsWork.ae2_scheduler.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.me.cluster.implementations.CraftingCPUCluster;

import dev.BloodyDreamsWork.ae2_scheduler.park.ParkableCpu;

/** Keeps a parked CPU reserved while its normal active slot is temporarily empty. */
@Mixin(CraftingCPUCluster.class)
public abstract class CraftingCPUClusterMixin {

    @Inject(method = "isBusy", at = @At("RETURN"), cancellable = true)
    private void acs$reportParkReservation(CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }

        var cluster = (CraftingCPUCluster) (Object) this;
        var park = ParkableCpu.of(cluster.craftingLogic);
        if (park != null && park.acs$isParked()) {
            cir.setReturnValue(true);
        }
    }
}
