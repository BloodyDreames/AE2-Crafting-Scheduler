package dev.BloodyDreamsWork.ae2_scheduler.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.me.service.CraftingService;

import dev.BloodyDreamsWork.ae2_scheduler.SchedulerLog;
import dev.BloodyDreamsWork.ae2_scheduler.scheduler.PreemptionManager;

@Mixin(CraftingService.class)
public abstract class CraftingServiceMixin {
    @Shadow
    @Final
    private IGrid grid;

    @Inject(method = "submitJob", at = @At("RETURN"), cancellable = true)
    private void acs$offerToSchedulers(ICraftingPlan job, ICraftingRequester requestingMachine,
            ICraftingCPU target, boolean prioritizePower, IActionSource src,
            CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        try {
            var replacement = PreemptionManager.offer(grid, job, requestingMachine, target, src,
                    cir.getReturnValue());
            if (replacement != null) {
                cir.setReturnValue(replacement);
            }
        } catch (RuntimeException e) {
            SchedulerLog.error("Preemption attempt failed; falling back to AE2's result", e);
        }
    }
}
