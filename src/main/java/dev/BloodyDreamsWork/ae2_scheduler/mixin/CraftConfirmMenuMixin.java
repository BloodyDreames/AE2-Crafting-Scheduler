package dev.BloodyDreamsWork.ae2_scheduler.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.chat.Component;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.menu.me.crafting.CraftConfirmMenu;

import dev.BloodyDreamsWork.ae2_scheduler.scheduler.PreemptionManager;

/**
 * Bridges AE2's crafting confirmation screen to the Scheduler's preemption path.
 *
 * <p>
 * AE2's normal CPU predicate is {@code capacity >= plan bytes && !cpu.isBusy()}. During the first
 * menu update the plan has not been installed yet, so a busy CPU is briefly visible; one update later
 * the predicate removes it and disables Start. The submit hook in {@code CraftingServiceMixin} can
 * therefore never run through the normal player workflow.
 *
 * <p>
 * Preserve AE2's answer whenever it already accepts the CPU. When it rejects a busy CPU, add it back
 * only if an operational Scheduler has positively checked that this exact plan and CPU can be
 * preempted. The actual submit path repeats every check, so a state change between rendering and the
 * click remains safe.
 */
@Mixin(CraftConfirmMenu.class)
public abstract class CraftConfirmMenuMixin {

    @Shadow
    private ICraftingPlan result;

    @Shadow
    private ICraftingCPU selectedCpu;

    @Shadow
    public Component cpuName;

    @Unique
    private boolean acs$showingPreemptionHint;

    @Shadow
    private IGrid getGrid() {
        throw new AssertionError("mixin stub");
    }

    @Inject(method = "cpuMatches", at = @At("RETURN"), cancellable = true)
    private void acs$keepPreemptibleCpuSelectable(ICraftingCPU cpu, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue()) || result == null) {
            return;
        }

        var grid = getGrid();
        if (PreemptionManager.canPreempt(grid, result, cpu,
                ((CraftConfirmMenu) (Object) this).getActionSource())) {
            cir.setReturnValue(true);
        }
    }

    /** Reuses AE2's existing synchronized CPU label to explain why a busy CPU is selectable. */
    @Inject(method = "broadcastChanges", at = @At("TAIL"))
    private void acs$describeAutomaticPreemption(CallbackInfo ci) {
        if (result == null) {
            if (acs$showingPreemptionHint) {
                cpuName = null;
                acs$showingPreemptionHint = false;
            }
            return;
        }
        if (selectedCpu != null) {
            // onCPUSelectionChanged has already installed the selected CPU's real name.
            acs$showingPreemptionHint = false;
            return;
        }

        var menu = (CraftConfirmMenu) (Object) this;
        var grid = getGrid();
        var src = menu.getActionSource();
        boolean ordinaryCpuAvailable = false;
        boolean preemptibleCpuAvailable = false;

        for (var cpu : grid.getCraftingService().getCpus()) {
            if (!cpu.isBusy() && cpu.getAvailableStorage() >= result.bytes()) {
                ordinaryCpuAvailable = true;
                break;
            }
            if (PreemptionManager.canPreempt(grid, result, cpu, src)) {
                preemptibleCpuAvailable = true;
            }
        }

        if (!ordinaryCpuAvailable && preemptibleCpuAvailable) {
            cpuName = Component.translatable("gui.ae2_crafting_scheduler.preemption_available");
            acs$showingPreemptionHint = true;
        } else if (acs$showingPreemptionHint) {
            cpuName = null;
            acs$showingPreemptionHint = false;
        }
    }
}
