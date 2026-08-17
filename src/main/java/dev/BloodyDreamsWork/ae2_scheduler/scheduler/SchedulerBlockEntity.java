package dev.BloodyDreamsWork.ae2_scheduler.scheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import dev.BloodyDreamsWork.ae2_scheduler.SchedulerConfig;
import dev.BloodyDreamsWork.ae2_scheduler.SchedulerLog;
import dev.BloodyDreamsWork.ae2_scheduler.menu.SchedulerMenu;
import dev.BloodyDreamsWork.ae2_scheduler.park.ParkableCpu;
import dev.BloodyDreamsWork.ae2_scheduler.registry.ModBlockEntities;
import dev.BloodyDreamsWork.ae2_scheduler.registry.ModBlocks;

public class SchedulerBlockEntity extends BlockEntity implements IInWorldGridNodeHost, MenuProvider {
    private static final String NBT_ID = "schedulerId";
    private static final String NBT_MANAGED = "managedCpus";
    private static final String NBT_REDSTONE = "redstoneMode";
    private static final String NBT_NODE = "proxy";

    private static final IGridNodeListener<SchedulerBlockEntity> NODE_LISTENER = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(SchedulerBlockEntity owner, IGridNode node) {
            owner.setChanged();
        }

        @Override
        public void onStateChanged(SchedulerBlockEntity owner, IGridNode node, State state) {
            owner.markStatusDirty();
        }

        @Override
        public void onGridChanged(SchedulerBlockEntity owner, IGridNode node) {
            owner.markStatusDirty();
        }
    };

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .setInWorldNode(true)
            .setExposedOnSides(EnumSet.allOf(Direction.class))
            .setVisualRepresentation(ModBlocks.CRAFTING_SCHEDULER.get())
            .setTagName(NBT_NODE)
            .setIdlePowerUsage(1.0);

    private UUID schedulerId = UUID.randomUUID();

    private final Set<BlockPos> managedCpus = new LinkedHashSet<>();

    private SchedulerRedstoneMode redstoneMode = SchedulerRedstoneMode.IGNORE;

    private final Map<BlockPos, PreemptionSession> sessions = new HashMap<>();

    private SchedulerVisualState visualState = SchedulerVisualState.OFFLINE;
    private boolean powerUsageApplied;
    private boolean statusDirty = true;

    private static final int RECONNECT_SCAN_INTERVAL = 20;
    private int ticksUntilReconnectScan = 1;

    public SchedulerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRAFTING_SCHEDULER.get(), pos, state);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        GridHelper.onFirstTick(this, SchedulerBlockEntity::onFirstTick);
    }

    private void onFirstTick() {
        mainNode.create(level, worldPosition);
    }

    private void reconnectIfIsolated() {
        var node = mainNode.getNode();
        if (node == null || !node.getConnections().isEmpty()) {
            return;
        }
        if (--ticksUntilReconnectScan > 0) {
            return;
        }
        ticksUntilReconnectScan = RECONNECT_SCAN_INTERVAL;
        mainNode.setExposedOnSides(EnumSet.noneOf(Direction.class));
        mainNode.setExposedOnSides(EnumSet.allOf(Direction.class));
    }

    @Override
    public void setRemoved() {
        mainNode.destroy();
        super.setRemoved();
    }

    public void onBlockDestroyed() {
        releaseAll("scheduler removed");
    }

    @Override
    public void onChunkUnloaded() {
        mainNode.destroy();
        super.onChunkUnloaded();
    }

    @Nullable
    @Override
    public IGridNode getGridNode(Direction dir) {
        return mainNode.getNode();
    }

    public IManagedGridNode getMainNode() {
        return mainNode;
    }

    @Nullable
    public IGrid getGrid() {
        return mainNode.getGrid();
    }

    public UUID getSchedulerId() {
        return schedulerId;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID(NBT_ID, schedulerId);
        tag.putString(NBT_REDSTONE, redstoneMode.getSerializedName());

        var packed = new long[managedCpus.size()];
        int i = 0;
        for (var cpu : managedCpus) {
            packed[i++] = cpu.asLong();
        }
        tag.putLongArray(NBT_MANAGED, packed);

        mainNode.saveToNBT(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID(NBT_ID)) {
            schedulerId = tag.getUUID(NBT_ID);
        }
        redstoneMode = SchedulerRedstoneMode.CODEC.byName(tag.getString(NBT_REDSTONE),
                SchedulerRedstoneMode.IGNORE);

        managedCpus.clear();
        for (var packed : tag.getLongArray(NBT_MANAGED)) {
            managedCpus.add(BlockPos.of(packed));
        }

        mainNode.loadFromNBT(tag);
    }

    public Set<BlockPos> getManagedCpus() {
        return managedCpus;
    }

    public boolean manages(BlockPos cpu) {
        return managedCpus.contains(cpu);
    }

    public void setManaged(BlockPos cpu, boolean managed) {
        if (managed) {
            if (managedCpus.add(cpu)) {
                SchedulerLog.debug("CPU {} is now managed", cpu);
                markStatusDirty();
                setChanged();
            }
        } else if (managedCpus.remove(cpu)) {
            SchedulerLog.debug("CPU {} is no longer managed", cpu);
            releaseSession(cpu, "cpu unmanaged");
            markStatusDirty();
            setChanged();
        }
    }

    public SchedulerRedstoneMode getRedstoneMode() {
        return redstoneMode;
    }

    public void setRedstoneMode(SchedulerRedstoneMode mode) {
        this.redstoneMode = mode;
        markStatusDirty();
        setChanged();
    }

    public SchedulerVisualState getVisualState() {
        return visualState;
    }

    public boolean isOperational() {
        if (level == null || level.isClientSide()) {
            return false;
        }
        if (!SchedulerConfig.isLoaded() || !SchedulerConfig.enableScheduler()) {
            return false;
        }
        if (!mainNode.isActive()) {
            return false;
        }
        return redstoneMode.allows(level.hasNeighborSignal(worldPosition));
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        if (!powerUsageApplied && SchedulerConfig.isLoaded()) {
            mainNode.setIdlePowerUsage(SchedulerConfig.energyUsagePerTick());
            powerUsageApplied = true;
        }

        reconnectIfIsolated();

        var grid = getGrid();
        if (!isOperational() || grid == null) {
            if (!sessions.isEmpty()) {
                releaseAll("scheduler is not operational");
            }
            updateVisualState(SchedulerVisualState.OFFLINE);
            return;
        }

        adoptOwnedParks(grid);

        var it = sessions.values().iterator();
        var finished = new ArrayList<BlockPos>();
        while (it.hasNext()) {
            var session = it.next();
            if (!advance(grid, session)) {
                finished.add(session.cpu());
                it.remove();
            }
        }
        if (!finished.isEmpty()) {
            markStatusDirty();
        }

        updateVisualState(computeVisualState());
    }

    private void adoptOwnedParks(IGrid grid) {
        for (var cpu : grid.getCraftingService().getCpus()) {
            if (!(cpu instanceof CraftingCPUCluster cluster)) {
                continue;
            }
            var key = cluster.getBoundsMin();
            if (sessions.containsKey(key)) {
                continue;
            }
            var park = CpuKey.parkable(cluster);
            if (park == null || !park.acs$isParked() || !schedulerId.equals(park.acs$getParkOwner())) {
                continue;
            }

            var session = new PreemptionSession(key);
            session.state = park.acs$hasActiveJob() ? ManagedCpuState.RUNNING_EXPRESS_JOB
                    : ManagedCpuState.EXPRESS_COMPLETED;
            session.pausedComplexity = park.acs$getParkedComplexity();
            sessions.put(key, session);
            managedCpus.add(key);
            markStatusDirty();

            SchedulerLog.debug("Re-adopted paused job on CPU {} after load, state {}", key, session.state);
        }
    }

    private boolean advance(IGrid grid, PreemptionSession session) {
        var cluster = CpuKey.resolve(grid, session.cpu());
        if (cluster == null) {
            session.state = ManagedCpuState.UNAVAILABLE;
            return true;
        }

        var park = CpuKey.parkable(cluster);
        if (park == null) {
            session.state = ManagedCpuState.UNSUPPORTED;
            return true;
        }

        park.acs$heartbeatPark(schedulerId);

        if (!park.acs$isParked()) {
            SchedulerLog.debug("Paused job on CPU {} is gone; releasing session", session.cpu());
            return false;
        }

        session.stateTicks++;
        session.pausedComplexity = park.acs$getParkedComplexity();
        session.inFlight = park.acs$getParkedInFlightCount();

        switch (session.state) {
            case PAUSE_REQUESTED, RUNNING_EXPRESS_JOB -> {
                if (park.acs$hasActiveJob()) {
                    session.state = ManagedCpuState.RUNNING_EXPRESS_JOB;
                    session.expressTicks++;

                    var timeout = SchedulerConfig.expressJobTimeoutTicks();
                    if (timeout > 0 && session.expressTicks > timeout) {
                        SchedulerLog.warn(
                                "Express job on CPU {} did not finish within {} ticks; cancelling it and "
                                        + "resuming the original job",
                                session.cpu(), timeout);
                        cluster.cancelJob();
                        session.setState(ManagedCpuState.EXPRESS_COMPLETED);
                    }
                } else {
                    SchedulerLog.debug("Express job completed on CPU {}", session.cpu());
                    session.setState(ManagedCpuState.EXPRESS_COMPLETED);
                }
            }
            case EXPRESS_COMPLETED -> session
                    .setState(session.inFlight > 0 ? ManagedCpuState.DRAINING_IN_FLIGHT_WORK
                            : ManagedCpuState.PAUSED);
            case DRAINING_IN_FLIGHT_WORK, PAUSED -> {
                if (park.acs$hasActiveJob()) {
                    session.setState(ManagedCpuState.RUNNING_EXPRESS_JOB);
                } else {
                    session.setState(ManagedCpuState.RESTORING);
                }
            }
            case RESTORING -> {
                if (!cluster.isActive()) {
                    session.fail("CPU is offline");
                } else if (park.acs$hasActiveJob()) {
                    session.fail("CPU became busy again");
                } else if (park.acs$unpark()) {
                    SchedulerLog.debug("Resume successful on CPU {}", session.cpu());
                    session.setState(ManagedCpuState.RESUMED);
                } else {
                    session.fail("CPU refused to accept the paused job");
                }
            }
            case RESUMED -> {
                return false;
            }
            case ERROR -> {
                if (--session.retryCooldown <= 0) {
                    session.retryCooldown = SchedulerConfig.resumeRetryTicks();
                    session.setState(ManagedCpuState.RESTORING);
                }
            }
            default -> session.setState(ManagedCpuState.RESTORING);
        }
        return true;
    }

    private SchedulerVisualState computeVisualState() {
        var result = SchedulerVisualState.ACTIVE;
        for (var session : sessions.values()) {
            switch (session.state) {
                case ERROR, UNAVAILABLE, UNSUPPORTED -> {
                    return SchedulerVisualState.ERROR;
                }
                case RUNNING_EXPRESS_JOB -> result = SchedulerVisualState.EXPRESS_JOB;
                case PAUSE_REQUESTED, DRAINING_IN_FLIGHT_WORK, RESTORING -> {
                    if (result != SchedulerVisualState.EXPRESS_JOB) {
                        result = SchedulerVisualState.PAUSING;
                    }
                }
                case PAUSED -> {
                    if (result == SchedulerVisualState.ACTIVE) {
                        result = SchedulerVisualState.PAUSED;
                    }
                }
                default -> {
                }
            }
        }
        return result;
    }

    private void updateVisualState(SchedulerVisualState state) {
        if (visualState == state || level == null) {
            return;
        }
        visualState = state;
        markStatusDirty();

        var current = getBlockState();
        if (current.hasProperty(dev.BloodyDreamsWork.ae2_scheduler.block.SchedulerBlock.STATE)) {
            level.setBlock(worldPosition,
                    current.setValue(dev.BloodyDreamsWork.ae2_scheduler.block.SchedulerBlock.STATE, state),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    @Nullable
    public CraftingCPUCluster selectVictim(IGrid grid, ICraftingPlan plan, PlanComplexity complexity,
            boolean playerInitiated) {
        if (!hasPreemptionCapacity()) {
            return null;
        }

        var candidates = new ArrayList<CraftingCPUCluster>();

        for (var cpu : grid.getCraftingService().getCpus()) {
            if (!(cpu instanceof CraftingCPUCluster cluster)) {
                continue;
            }
            var key = cluster.getBoundsMin();
            var reason = rejectVictim(cluster, key, plan, playerInitiated);
            if (reason != null) {
                SchedulerLog.debug("CPU {} is not a preemption candidate: {}", key, reason);
                continue;
            }
            candidates.add(cluster);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        candidates.sort(Comparator
                .comparingLong((CraftingCPUCluster c) -> -remainingOperations(c))
                .thenComparingInt(CraftingCPUCluster::getCoProcessors)
                .thenComparingLong(CraftingCPUCluster::getAvailableStorage)
                .thenComparing(CraftingCPUCluster::getBoundsMin));

        return candidates.get(0);
    }

    public boolean canPreempt(CraftingCPUCluster cluster, ICraftingPlan plan, boolean playerInitiated) {
        return hasPreemptionCapacity()
                && rejectVictim(cluster, cluster.getBoundsMin(), plan, playerInitiated) == null;
    }

    private boolean hasPreemptionCapacity() {
        return sessions.size() < SchedulerConfig.maxPausedJobsPerScheduler();
    }

    @Nullable
    private String rejectVictim(CraftingCPUCluster cluster, BlockPos key, ICraftingPlan plan,
            boolean playerInitiated) {
        if (!managedCpus.contains(key)) {
            return "not managed by this Scheduler";
        }
        if (sessions.containsKey(key)) {
            return "already holds a preemption of ours";
        }
        var park = CpuKey.parkable(cluster);
        if (park == null) {
            return "unsupported CPU implementation";
        }
        if (park.acs$isParked() || park.acs$getParkOwner() != null) {
            return "already holds a paused job";
        }
        if (!cluster.isActive()) {
            return "offline";
        }
        if (!cluster.isBusy()) {
            return "idle, so there is nothing to pause";
        }
        if (cluster.getAvailableStorage() < plan.bytes()) {
            return "too small for the express job (" + cluster.getAvailableStorage() + " < " + plan.bytes()
                    + " bytes)";
        }
        var complexity = park.acs$getActiveComplexity();
        if (!playerInitiated && complexity < SchedulerConfig.minimumJobComplexityForPreemption()) {
            return "its job is only " + complexity + " operations, below the "
                    + SchedulerConfig.minimumJobComplexityForPreemption() + " needed to interrupt it";
        }
        return null;
    }

    private static long remainingOperations(CraftingCPUCluster cluster) {
        var park = CpuKey.parkable(cluster);
        if (park == null) {
            return 0;
        }
        var progress = cluster.craftingLogic.getElapsedTimeTracker().getProgress();
        return PlanComplexity.estimateRemaining(park.acs$getActiveComplexity(), progress);
    }

    @Nullable
    public ICraftingSubmitResult beginExpress(IGrid grid, CraftingCPUCluster cluster, ICraftingPlan plan,
            @Nullable ICraftingRequester requester, IActionSource src, PlanComplexity complexity) {
        var park = CpuKey.parkable(cluster);
        if (park == null) {
            return null;
        }
        if (sessions.size() >= SchedulerConfig.maxPausedJobsPerScheduler()) {
            SchedulerLog.debug("Refusing preemption: already holding {} paused jobs", sessions.size());
            return null;
        }

        var key = cluster.getBoundsMin();
        var pausedOutput = cluster.craftingLogic.getFinalJobOutput();
        long pausedComplexity = park.acs$getActiveComplexity();

        SchedulerLog.debug("Pausing job: {} ({} estimated operations) on CPU {}",
                pausedOutput != null ? describe(pausedOutput) : "unknown", pausedComplexity, key);

        if (!park.acs$park(schedulerId, pausedComplexity)) {
            SchedulerLog.debug("CPU {} could not be paused", key);
            return null;
        }

        SchedulerLog.debug("Job safely paused, {} operations still in flight", park.acs$getParkedInFlightCount());
        SchedulerLog.debug("Starting express job: {}", describe(plan.finalOutput()));

        var result = park.acs$submitExpress(grid, plan, src, requester);
        if (result == null || !result.successful()) {
            SchedulerLog.debug("Express job was rejected ({}); resuming the original job immediately",
                    result == null ? "null" : result.errorCode());
            park.acs$unpark();
            return null;
        }

        var session = new PreemptionSession(key);
        session.state = ManagedCpuState.PAUSE_REQUESTED;
        session.pausedComplexity = pausedComplexity;
        session.expressComplexity = complexity.operations();
        sessions.put(key, session);
        managedCpus.add(key);
        markStatusDirty();
        setChanged();
        return result;
    }

    public void releaseAll(String reason) {
        if (sessions.isEmpty()) {
            return;
        }
        for (var cpu : List.copyOf(sessions.keySet())) {
            releaseSession(cpu, reason);
        }
        markStatusDirty();
    }

    public void releaseSession(BlockPos cpu, String reason) {
        var session = sessions.remove(cpu);
        if (session == null) {
            return;
        }
        var grid = getGrid();
        var cluster = CpuKey.resolve(grid, cpu);
        var park = cluster == null ? null : CpuKey.parkable(cluster);
        if (park == null || !park.acs$isParked()) {
            return;
        }

        SchedulerLog.debug("Restoring original crafting state on CPU {} ({})", cpu, reason);
        if (park.acs$hasActiveJob()) {
            cluster.cancelJob();
        }
        if (!park.acs$unpark()) {
            SchedulerLog.warn("Could not resume the paused job on CPU {} right now; the CPU will resume it "
                    + "itself once it is free", cpu);
        }
    }

    public void cancelExpress(BlockPos cpu) {
        releaseSession(cpu, "cancelled by player");
        markStatusDirty();
    }

    @Nullable
    public PreemptionSession getSession(BlockPos cpu) {
        return sessions.get(cpu);
    }

    public int getPausedJobCount() {
        int count = 0;
        for (var session : sessions.values()) {
            if (session.state.holdsPausedJob()) {
                count++;
            }
        }
        return count;
    }

    public void markStatusDirty() {
        statusDirty = true;
    }

    public boolean consumeStatusDirty() {
        var dirty = statusDirty;
        statusDirty = false;
        return dirty;
    }

    private static String describe(appeng.api.stacks.GenericStack stack) {
        return stack.what().getDisplayName().getString() + " x" + stack.amount();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.ae2_crafting_scheduler.crafting_scheduler");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SchedulerMenu(containerId, playerInventory, this);
    }

    public static final class PreemptionSession {
        private final BlockPos cpu;
        ManagedCpuState state = ManagedCpuState.PAUSE_REQUESTED;
        long pausedComplexity;
        long expressComplexity;
        int expressTicks;
        int stateTicks;
        int inFlight;
        int retryCooldown;
        @Nullable
        String errorReason;

        PreemptionSession(BlockPos cpu) {
            this.cpu = cpu;
        }

        public BlockPos cpu() {
            return cpu;
        }

        public ManagedCpuState state() {
            return state;
        }

        public long pausedComplexity() {
            return pausedComplexity;
        }

        public long expressComplexity() {
            return expressComplexity;
        }

        public int inFlight() {
            return inFlight;
        }

        @Nullable
        public String errorReason() {
            return errorReason;
        }

        void setState(ManagedCpuState next) {
            if (state != next) {
                state = next;
                stateTicks = 0;
                if (next != ManagedCpuState.ERROR) {
                    errorReason = null;
                }
            }
        }

        void fail(String reason) {
            if (state != ManagedCpuState.ERROR || !reason.equals(errorReason)) {
                SchedulerLog.warn("Cannot resume the paused job on CPU {}: {}. State is preserved and the "
                        + "resume will be retried.", cpu, reason);
            }
            setState(ManagedCpuState.ERROR);
            errorReason = reason;
            retryCooldown = SchedulerConfig.resumeRetryTicks();
        }
    }
}
