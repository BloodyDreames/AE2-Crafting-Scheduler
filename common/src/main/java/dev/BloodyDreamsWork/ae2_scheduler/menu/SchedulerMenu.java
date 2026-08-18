package dev.BloodyDreamsWork.ae2_scheduler.menu;

import java.util.ArrayList;
import java.util.HashSet;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.GenericStack;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.menu.AEBaseMenu;

import dev.BloodyDreamsWork.ae2_scheduler.SchedulerConfig;
import dev.BloodyDreamsWork.ae2_scheduler.net.ModNetwork;
import dev.BloodyDreamsWork.ae2_scheduler.net.SchedulerAction;
import dev.BloodyDreamsWork.ae2_scheduler.net.SchedulerStatus;
import dev.BloodyDreamsWork.ae2_scheduler.registry.ModRegistry;
import dev.BloodyDreamsWork.ae2_scheduler.scheduler.AbstractSchedulerBlockEntity;
import dev.BloodyDreamsWork.ae2_scheduler.scheduler.CpuKey;
import dev.BloodyDreamsWork.ae2_scheduler.scheduler.ManagedCpuState;

public class SchedulerMenu extends AEBaseMenu {
    private static final int REFRESH_INTERVAL_TICKS = 5;

    @Nullable
    private final AbstractSchedulerBlockEntity host;
    private final BlockPos hostPos;

    private SchedulerStatus status = SchedulerStatus.EMPTY;
    private int ticksUntilRefresh;

    public SchedulerMenu(int containerId, Inventory playerInventory, AbstractSchedulerBlockEntity host) {
        super(ModRegistry.schedulerMenuType(), containerId, playerInventory, host);
        this.host = host;
        this.hostPos = host.getBlockPos();
        host.markStatusDirty();
    }

    /**
     * Client-side constructor. Declared against {@link FriendlyByteBuf} on purpose: Minecraft 1.21.1
     * hands out a {@code RegistryFriendlyByteBuf}, which is a subclass, so the same signature works on
     * both Minecraft versions.
     */
    public SchedulerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        super(ModRegistry.schedulerMenuType(), containerId, playerInventory, null);
        this.host = null;
        this.hostPos = buf.readBlockPos();
    }

    public BlockPos getHostPos() {
        return hostPos;
    }

    public SchedulerStatus getStatus() {
        return status;
    }

    public void setStatus(SchedulerStatus status) {
        this.status = status;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!super.stillValid(player)) {
            return false;
        }
        if (host == null) {
            return true;
        }
        return !host.isRemoved() && player.distanceToSqr(hostPos.getX() + 0.5, hostPos.getY() + 0.5,
                hostPos.getZ() + 0.5) <= 64;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (host == null || !(getPlayer() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        boolean dirty = host.consumeStatusDirty();
        if (--ticksUntilRefresh > 0 && !dirty) {
            return;
        }
        ticksUntilRefresh = REFRESH_INTERVAL_TICKS;
        ModNetwork.sendStatusToPlayer(serverPlayer, buildStatus(host));
    }

    public void handleAction(SchedulerAction action) {
        if (host == null || host.isRemoved()) {
            return;
        }
        switch (action.type()) {
            case TOGGLE_CPU -> host.setManaged(action.cpu(), !host.manages(action.cpu()));
            case CYCLE_REDSTONE -> host.setRedstoneMode(host.getRedstoneMode().next());
            case CYCLE_REDSTONE_BACKWARDS -> host.setRedstoneMode(host.getRedstoneMode().previous());
            case CANCEL_EXPRESS -> host.cancelExpress(action.cpu());
        }
        host.markStatusDirty();
    }

    private static SchedulerStatus buildStatus(AbstractSchedulerBlockEntity host) {
        var grid = host.getGrid();
        var rows = new ArrayList<CpuStatus>();
        var seen = new HashSet<BlockPos>();

        if (grid != null) {
            int foreignIndex = 0;
            for (var cpu : grid.getCraftingService().getCpus()) {
                if (cpu instanceof CraftingCPUCluster cluster) {
                    var key = cluster.getBoundsMin();
                    seen.add(key);
                    rows.add(describe(host, cluster, key));
                } else {
                    rows.add(unsupported(cpu, foreignIndex++));
                }
            }
        }

        for (var key : host.getManagedCpus()) {
            if (!seen.contains(key)) {
                rows.add(new CpuStatus(key, "CPU @ " + key.toShortString(), 0, 0, true, true,
                        ManagedCpuState.UNAVAILABLE, "", 0, 0, 0, "", 0, 0, ""));
            }
        }

        rows.sort((a, b) -> a.pos().compareTo(b.pos()));

        return new SchedulerStatus(
                host.isOperational(),
                SchedulerConfig.isLoaded() && SchedulerConfig.allowAutomaticPreemption(),
                SchedulerConfig.isLoaded() ? SchedulerConfig.maxExpressComplexity() : 0,
                SchedulerConfig.isLoaded() ? SchedulerConfig.minimumJobComplexityForPreemption() : 0,
                host.getRedstoneMode(),
                rows);
    }

    private static CpuStatus describe(AbstractSchedulerBlockEntity host, CraftingCPUCluster cluster,
            BlockPos key) {
        var park = CpuKey.parkable(cluster);
        var session = host.getSession(key);
        boolean managed = host.manages(key);
        boolean supported = park != null;

        var name = cluster.getName() != null ? cluster.getName().getString() : "CPU @ " + key.toShortString();

        ManagedCpuState state;
        if (!supported) {
            state = ManagedCpuState.UNSUPPORTED;
        } else if (session != null) {
            state = session.state();
        } else {
            state = cluster.isBusy() ? ManagedCpuState.RUNNING : ManagedCpuState.IDLE;
        }

        var activeOutput = cluster.craftingLogic.getFinalJobOutput();
        var activeLabel = activeOutput == null ? "" : label(activeOutput);
        var activeProgress = cluster.craftingLogic.getElapsedTimeTracker().getProgress();
        long activeOperations = supported ? park.acs$getActiveComplexity() : 0;

        var pausedLabel = "";
        float pausedProgress = 0;
        long pausedOperations = 0;
        int inFlight = 0;
        if (supported && park.acs$isParked()) {
            var pausedOutput = park.acs$getParkedOutput();
            if (pausedOutput != null) {
                pausedLabel = label(new GenericStack(pausedOutput.what(), park.acs$getParkedRemainingAmount()));
            }
            pausedProgress = park.acs$getParkedProgress();
            pausedOperations = park.acs$getParkedComplexity();
            inFlight = park.acs$getParkedInFlightCount();
        }

        var error = session != null && session.errorReason() != null ? session.errorReason() : "";

        return new CpuStatus(key, name, cluster.getAvailableStorage(), cluster.getCoProcessors(), managed,
                supported, state, pausedLabel, pausedProgress, pausedOperations, inFlight, activeLabel,
                activeProgress, activeOperations, error);
    }

    private static CpuStatus unsupported(ICraftingCPU cpu, int index) {
        var name = cpu.getName() != null ? cpu.getName().getString()
                : Component.translatable("gui.ae2_crafting_scheduler.foreign_cpu").getString();
        return new CpuStatus(new BlockPos(0, -1 - index, 0), name, cpu.getAvailableStorage(),
                cpu.getCoProcessors(), false, false, ManagedCpuState.UNSUPPORTED, "", 0, 0, 0, "", 0, 0, "");
    }

    private static String label(GenericStack stack) {
        return stack.what().getDisplayName().getString() + " x" + stack.amount();
    }
}
