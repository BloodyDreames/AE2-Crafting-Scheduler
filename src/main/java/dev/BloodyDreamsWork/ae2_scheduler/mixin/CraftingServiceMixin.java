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

/**
 * Offers a crafting request that AE2 could not place to the Schedulers on that network.
 *
 * <p>
 * {@code CraftingService.submitJob} is the single funnel every job goes through -- the terminal,
 * requesting machines, and AE2's own test world all call it -- which makes it the one place where a
 * "no free CPU" answer can be turned into a preemption without touching any other code path.
 *
 * <p>
 * The injection runs at RETURN and only replaces the result when AE2 already failed <em>and</em> a
 * Scheduler successfully made room. Because the pause and the express submit both complete inside this
 * call, the caller gets a normal synchronous result with a real crafting link, which is what requesting
 * machines need.
 */
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
            // A failure here must never break plain AE2 autocrafting; fall back to AE2's own answer.
            SchedulerLog.error("Preemption attempt failed; falling back to AE2's result", e);
        }
    }
}
