package dev.BloodyDreamsWork.ae2_scheduler.mixin;

import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import it.unimi.dsi.fastutil.longs.LongArrayList;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;

import dev.BloodyDreamsWork.ae2_scheduler.SchedulerConfig;
import dev.BloodyDreamsWork.ae2_scheduler.SchedulerLog;
import dev.BloodyDreamsWork.ae2_scheduler.park.ParkableCpu;
import dev.BloodyDreamsWork.ae2_scheduler.scheduler.PlanComplexity;

@Mixin(CraftingCpuLogic.class)
public abstract class CraftingCpuLogicMixin implements ParkableCpu {
    @Unique
    private static final String NBT_PARK = "acs_park";
    @Unique
    private static final String NBT_PARK_STATE = "state";
    @Unique
    private static final String NBT_PARK_OWNER = "owner";
    @Unique
    private static final String NBT_PARK_COMPLEXITY = "complexity";
    @Unique
    private static final String NBT_PARK_DURATION = "duration";
    @Unique
    private static final String NBT_ACTIVE_COMPLEXITY = "acs_active_complexity";

    @Shadow
    @Final
    protected CraftingCPUCluster cluster;

    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    @Final
    private ListCraftingInventory inventory;

    @Shadow
    private void postChange(AEKey what) {
        throw new AssertionError("mixin stub");
    }

    @Shadow
    public abstract void writeToNBT(CompoundTag data, HolderLookup.Provider registries);

    @Shadow
    public abstract void readFromNBT(CompoundTag data, HolderLookup.Provider registries);

    @Unique
    @Nullable
    private ExecutingCraftingJob acs$parkedJob;

    @Unique
    @Nullable
    private ListCraftingInventory acs$parkedInventory;

    @Unique
    @Nullable
    private UUID acs$parkOwner;

    @Unique
    private long acs$parkedComplexity;

    @Unique
    private long acs$activeComplexity;

    @Unique
    private long acs$parkedDuration;

    @Unique
    private long acs$ticksSinceHeartbeat;

    @Unique
    private boolean acs$rehydrating;

    @Unique
    private boolean acs$expressSubmitPermit;

    @Unique
    private final LongArrayList acs$offeredAmounts = new LongArrayList(4);

    @Override
    public boolean acs$isParked() {
        return acs$parkedJob != null;
    }

    @Override
    public boolean acs$park(UUID owner, long complexity) {
        if (job == null || acs$parkedJob != null) {
            return false;
        }
        if (acs$parkedInventory != null && !acs$parkedInventory.list.isEmpty()) {
            return false;
        }

        acs$parkedComplexity = complexity > 0 ? complexity : acs$activeComplexity;
        acs$moveActiveToPark();
        acs$parkOwner = owner;
        acs$parkedDuration = 0;
        acs$ticksSinceHeartbeat = 0;
        acs$activeComplexity = 0;

        cluster.updateOutput(null);
        cluster.markDirty();
        return true;
    }

    @Override
    public boolean acs$unpark() {
        if (acs$parkedJob == null || job != null) {
            return false;
        }

        var restored = acs$parkedJob;
        var accessor = (ExecutingCraftingJobAccessor) restored;

        this.job = restored;
        accessor.acs$setSuspended(false);

        if (acs$parkedInventory != null) {
            for (var entry : acs$parkedInventory.list) {
                inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            }
            acs$parkedInventory = null;
        }

        acs$activeComplexity = acs$parkedComplexity;
        acs$parkedJob = null;
        acs$parkOwner = null;
        acs$parkedComplexity = 0;
        acs$parkedDuration = 0;
        acs$ticksSinceHeartbeat = 0;

        var finalOutput = accessor.acs$finalOutput();
        if (finalOutput != null) {
            cluster.updateOutput(new GenericStack(finalOutput.what(), accessor.acs$remainingAmount()));
        }
        cluster.markDirty();
        return true;
    }

    @Override
    public ICraftingSubmitResult acs$submitExpress(IGrid grid, ICraftingPlan plan, IActionSource src,
            @Nullable ICraftingRequester requester) {
        if (acs$parkedJob == null) {
            return CraftingSubmitResult.CPU_BUSY;
        }

        acs$expressSubmitPermit = true;
        try {
            return ((CraftingCpuLogic) (Object) this).trySubmitJob(grid, plan, src, requester);
        } finally {
            acs$expressSubmitPermit = false;
        }
    }

    @Override
    public boolean acs$hasActiveJob() {
        return job != null;
    }

    @Override
    public void acs$abandonPark() {
        if (acs$parkedJob == null) {
            return;
        }
        acs$finishParkedJob(false);
    }

    @Override
    public void acs$evacuateParkForDestruction() {
        acs$finishParkedJob(false);
        if (acs$parkedInventory != null) {
            for (var entry : acs$parkedInventory.list) {
                inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            }
            acs$parkedInventory = null;
        }
    }

    @Override
    @Nullable
    public UUID acs$getParkOwner() {
        return acs$parkOwner;
    }

    @Override
    public void acs$heartbeatPark(UUID owner) {
        if (owner.equals(acs$parkOwner)) {
            acs$ticksSinceHeartbeat = 0;
        }
    }

    @Override
    public long acs$getTicksSinceParkHeartbeat() {
        return acs$ticksSinceHeartbeat;
    }

    @Override
    @Nullable
    public GenericStack acs$getParkedOutput() {
        return acs$parkedJob == null ? null : ((ExecutingCraftingJobAccessor) acs$parkedJob).acs$finalOutput();
    }

    @Override
    public long acs$getParkedRemainingAmount() {
        return acs$parkedJob == null ? 0 : ((ExecutingCraftingJobAccessor) acs$parkedJob).acs$remainingAmount();
    }

    @Override
    public float acs$getParkedProgress() {
        if (acs$parkedJob == null) {
            return 0;
        }
        return ((ExecutingCraftingJobAccessor) acs$parkedJob).acs$timeTracker().getProgress();
    }

    @Override
    public long acs$getParkedComplexity() {
        return acs$parkedComplexity;
    }

    @Override
    public int acs$getParkedInFlightCount() {
        if (acs$parkedJob == null) {
            return 0;
        }
        return ((ExecutingCraftingJobAccessor) acs$parkedJob).acs$waitingFor().list.size();
    }

    @Override
    public long acs$getParkedDuration() {
        return acs$parkedDuration;
    }

    @Override
    public void acs$setActiveComplexity(long complexity) {
        this.acs$activeComplexity = complexity;
    }

    @Override
    public long acs$getActiveComplexity() {
        return acs$activeComplexity;
    }

    @Unique
    private void acs$moveActiveToPark() {
        var parked = new ListCraftingInventory(this::acs$onParkedInventoryChange);
        for (var entry : inventory.list) {
            parked.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
        }
        inventory.clear();

        acs$parkedInventory = parked;
        acs$parkedJob = this.job;
        this.job = null;
    }

    @Unique
    private void acs$onParkedInventoryChange(AEKey what) {
        postChange(what);
    }

    @Unique
    private long acs$insertIntoParkedJob(AEKey what, long amount, Actionable type) {
        var parked = acs$parkedJob;
        if (parked == null || what == null || amount <= 0) {
            return 0;
        }
        var accessor = (ExecutingCraftingJobAccessor) parked;
        var waitingFor = accessor.acs$waitingFor();

        long expected = waitingFor.extract(what, amount, Actionable.SIMULATE);
        if (expected <= 0) {
            return 0;
        }
        if (amount > expected) {
            amount = expected;
        }

        if (type == Actionable.MODULATE) {
            ((ElapsedTimeTrackerInvoker) accessor.acs$timeTracker()).acs$decrementItems(amount, what.getType());
            waitingFor.extract(what, amount, Actionable.MODULATE);
            cluster.markDirty();
        }

        long inserted = amount;
        var finalOutput = accessor.acs$finalOutput();

        if (finalOutput != null && what.matches(finalOutput)) {
            inserted = accessor.acs$link().insert(what, amount, type);

            if (type == Actionable.MODULATE) {
                postChange(what);
                long remaining = Math.max(0, accessor.acs$remainingAmount() - amount);
                accessor.acs$setRemainingAmount(remaining);

                if (remaining <= 0) {
                    SchedulerLog.debug("Paused job {} completed while paused; nothing to resume",
                            finalOutput.what().getDisplayName().getString());
                    acs$finishParkedJob(true);
                }
            }
        } else if (type == Actionable.MODULATE) {
            if (acs$parkedInventory == null) {
                acs$parkedInventory = new ListCraftingInventory(this::acs$onParkedInventoryChange);
            }
            acs$parkedInventory.insert(what, amount, Actionable.MODULATE);
        }

        if (type == Actionable.MODULATE && inserted > 0) {
            SchedulerLog.debug("Accepted in-flight result for paused job: {} x{}",
                    what.getDisplayName().getString(), inserted);
        }
        return inserted;
    }

    @Unique
    private void acs$finishParkedJob(boolean success) {
        var parked = acs$parkedJob;
        if (parked == null) {
            return;
        }
        var accessor = (ExecutingCraftingJobAccessor) parked;

        if (success) {
            accessor.acs$link().markDone();
        } else {
            accessor.acs$link().cancel();
        }
        accessor.acs$waitingFor().clear();

        acs$parkedJob = null;
        acs$parkOwner = null;
        acs$parkedComplexity = 0;
        acs$parkedDuration = 0;
        acs$ticksSinceHeartbeat = 0;
        cluster.markDirty();
    }

    @Unique
    private void acs$drainOrphanedParkedInventory() {
        if (acs$parkedJob != null || acs$parkedInventory == null) {
            return;
        }
        if (acs$parkedInventory.list.isEmpty()) {
            acs$parkedInventory = null;
            return;
        }

        var grid = cluster.getGrid();
        if (grid == null) {
            return;
        }
        var storage = grid.getStorageService().getInventory();

        for (var entry : acs$parkedInventory.list) {
            postChange(entry.getKey());
            long inserted = storage.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE,
                    cluster.getSrc());
            entry.setValue(entry.getLongValue() - inserted);
        }
        acs$parkedInventory.list.removeZeros();
        cluster.markDirty();
    }

    @Inject(method = "insert", at = @At("HEAD"))
    private void acs$captureInsertAmount(AEKey what, long amount, Actionable type,
            CallbackInfoReturnable<Long> cir) {
        if (acs$parkedJob != null) {
            acs$offeredAmounts.add(amount);
        }
    }

    @Inject(method = "insert", at = @At("RETURN"), cancellable = true)
    private void acs$insertIntoPark(AEKey what, long amount, Actionable type,
            CallbackInfoReturnable<Long> cir) {
        if (acs$parkedJob == null) {
            return;
        }
        long offered = acs$offeredAmounts.isEmpty() ? amount
                : acs$offeredAmounts.removeLong(acs$offeredAmounts.size() - 1);

        long alreadyInserted = cir.getReturnValue();
        long remaining = offered - alreadyInserted;
        if (remaining <= 0) {
            return;
        }

        long extra = acs$insertIntoParkedJob(what, remaining, type);
        if (extra > 0) {
            cir.setReturnValue(alreadyInserted + extra);
        }
    }

    @Inject(method = "trySubmitJob", at = @At("RETURN"))
    private void acs$recordJobComplexity(IGrid grid, ICraftingPlan plan, IActionSource src,
            ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        var result = cir.getReturnValue();
        if (result != null && result.successful() && job != null) {
            acs$activeComplexity = PlanComplexity.operations(plan);
        }
    }

    @Inject(method = "trySubmitJob", at = @At("HEAD"), cancellable = true)
    private void acs$rejectSubmitIntoReservedCpu(IGrid grid, ICraftingPlan plan, IActionSource src,
            ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        if (acs$parkedJob != null && !acs$expressSubmitPermit) {
            cir.setReturnValue(CraftingSubmitResult.CPU_BUSY);
        }
    }

    @Inject(method = "tickCraftingLogic", at = @At("HEAD"))
    private void acs$tickPark(IEnergyService energyService, CraftingService craftingService, CallbackInfo ci) {
        if (!acs$offeredAmounts.isEmpty()) {
            acs$offeredAmounts.clear();
        }

        if (!cluster.isActive()) {
            return;
        }

        if (acs$parkedJob != null) {
            acs$parkedDuration++;
            acs$ticksSinceHeartbeat++;

            if (((ExecutingCraftingJobAccessor) acs$parkedJob).acs$link().isCanceled()) {
                SchedulerLog.debug("Paused job's crafting link was cancelled; releasing park");
                acs$finishParkedJob(false);
            } else {
                acs$watchdog();
            }
        }

        acs$drainOrphanedParkedInventory();
    }

    @Unique
    private void acs$watchdog() {
        if (!SchedulerConfig.isLoaded()) {
            return;
        }
        long timeout = SchedulerConfig.orphanedParkTimeoutTicks();
        if (acs$ticksSinceHeartbeat <= timeout) {
            return;
        }

        if (job == null) {
            SchedulerLog.warn("Paused job on the CPU at {} has been orphaned for {} ticks; resuming it",
                    cluster.getBoundsMin(), acs$ticksSinceHeartbeat);
            acs$unpark();
        } else if (acs$ticksSinceHeartbeat > timeout * 4) {
            SchedulerLog.warn(
                    "Paused job on the CPU at {} is still blocked by another job {} ticks after its "
                            + "Scheduler disappeared; cancelling that job to free the CPU",
                    cluster.getBoundsMin(), acs$ticksSinceHeartbeat);
            ((CraftingCpuLogic) (Object) this).cancel();
        }
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void acs$writePark(CompoundTag data, HolderLookup.Provider registries, CallbackInfo ci) {
        if (acs$activeComplexity > 0) {
            data.putLong(NBT_ACTIVE_COMPLEXITY, acs$activeComplexity);
        }
        if (acs$parkedJob == null && (acs$parkedInventory == null || acs$parkedInventory.list.isEmpty())) {
            return;
        }

        var state = new CompoundTag();
        if (acs$parkedInventory != null) {
            state.put("inventory", acs$parkedInventory.writeToNBT(registries));
        }
        if (acs$parkedJob != null) {
            state.put("job", ((ExecutingCraftingJobAccessor) acs$parkedJob).acs$writeToNBT(registries));
        }

        var park = new CompoundTag();
        park.put(NBT_PARK_STATE, state);
        if (acs$parkOwner != null) {
            park.putUUID(NBT_PARK_OWNER, acs$parkOwner);
        }
        park.putLong(NBT_PARK_COMPLEXITY, acs$parkedComplexity);
        park.putLong(NBT_PARK_DURATION, acs$parkedDuration);
        data.put(NBT_PARK, park);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void acs$readPark(CompoundTag data, HolderLookup.Provider registries, CallbackInfo ci) {
        if (acs$rehydrating) {
            return;
        }

        acs$parkedJob = null;
        acs$parkedInventory = null;
        acs$parkOwner = null;
        acs$parkedComplexity = 0;
        acs$parkedDuration = 0;
        acs$ticksSinceHeartbeat = 0;
        acs$activeComplexity = data.getLong(NBT_ACTIVE_COMPLEXITY);

        if (!data.contains(NBT_PARK, Tag.TAG_COMPOUND)) {
            return;
        }
        var park = data.getCompound(NBT_PARK);

        acs$rehydrating = true;
        try {
            var activeState = new CompoundTag();
            writeToNBT(activeState, registries);

            readFromNBT(park.getCompound(NBT_PARK_STATE), registries);
            acs$moveActiveToPark();

            readFromNBT(activeState, registries);
        } catch (RuntimeException e) {
            SchedulerLog.error("Failed to restore a paused crafting job; its items are kept held", e);
        } finally {
            acs$rehydrating = false;
        }

        if (park.hasUUID(NBT_PARK_OWNER)) {
            acs$parkOwner = park.getUUID(NBT_PARK_OWNER);
        }
        acs$parkedComplexity = park.getLong(NBT_PARK_COMPLEXITY);
        acs$parkedDuration = park.getLong(NBT_PARK_DURATION);
        acs$activeComplexity = data.getLong(NBT_ACTIVE_COMPLEXITY);

        if (acs$parkedJob != null) {
            SchedulerLog.debug("Restored paused job from disk: {} x{} remaining",
                    acs$getParkedOutput() != null ? acs$getParkedOutput().what().getDisplayName().getString()
                            : "?",
                    acs$getParkedRemainingAmount());
        }
    }

    @Inject(method = "getAllWaitingFor", at = @At("TAIL"))
    private void acs$parkedWaitingFor(Set<AEKey> waitingFor, CallbackInfo ci) {
        if (acs$parkedJob != null) {
            for (var entry : ((ExecutingCraftingJobAccessor) acs$parkedJob).acs$waitingFor().list) {
                waitingFor.add(entry.getKey());
            }
        }
    }

    @Inject(method = "getWaitingFor", at = @At("RETURN"), cancellable = true)
    private void acs$parkedWaitingForAmount(AEKey template, CallbackInfoReturnable<Long> cir) {
        if (acs$parkedJob != null) {
            long parked = ((ExecutingCraftingJobAccessor) acs$parkedJob).acs$waitingFor()
                    .extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
            if (parked > 0) {
                cir.setReturnValue(cir.getReturnValue() + parked);
            }
        }
    }
}
