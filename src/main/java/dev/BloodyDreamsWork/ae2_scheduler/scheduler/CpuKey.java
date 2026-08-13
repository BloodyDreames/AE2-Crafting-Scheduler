package dev.BloodyDreamsWork.ae2_scheduler.scheduler;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import dev.BloodyDreamsWork.ae2_scheduler.park.ParkableCpu;

/**
 * Identifying and resolving Crafting CPUs.
 *
 * <p>
 * {@link CraftingCPUCluster} is a runtime object that AE2 throws away and rebuilds whenever any unit
 * block of the multiblock changes, so it cannot be stored. CPUs are keyed by
 * {@link CraftingCPUCluster#getBoundsMin()} instead: it is deterministic, survives cluster rebuilds and
 * server restarts, and is unaffected by renaming the CPU.
 *
 * <p>
 * If a player rebuilds the multiblock into a different shape its bounds change and the CPU shows up as
 * a new, unselected one. That is intentional -- a differently shaped multiblock is a different CPU as
 * far as the player is concerned.
 */
public final class CpuKey {

    private CpuKey() {
    }

    /** @return the stable key of a CPU, or null if it is not an AE2 CPU cluster. */
    @Nullable
    public static BlockPos of(@Nullable ICraftingCPU cpu) {
        return cpu instanceof CraftingCPUCluster cluster ? cluster.getBoundsMin() : null;
    }

    @Nullable
    public static CraftingCPUCluster resolve(@Nullable IGrid grid, BlockPos key) {
        if (grid == null) {
            return null;
        }
        for (var cpu : grid.getCraftingService().getCpus()) {
            if (cpu instanceof CraftingCPUCluster cluster && cluster.getBoundsMin().equals(key)) {
                return cluster;
            }
        }
        return null;
    }

    /**
     * @return the park slot of a CPU, or null when this CPU cannot be paused -- either a third-party
     *         {@link ICraftingCPU} implementation, or one our mixin did not apply to. Callers report
     *         those as {@code Unsupported CPU} rather than failing.
     */
    @Nullable
    public static ParkableCpu parkable(@Nullable ICraftingCPU cpu) {
        return cpu instanceof CraftingCPUCluster cluster ? ParkableCpu.of(cluster.craftingLogic) : null;
    }

    @Nullable
    public static ParkableCpu parkable(@Nullable CraftingCPUCluster cluster) {
        return cluster == null ? null : ParkableCpu.of(cluster.craftingLogic);
    }

    public static boolean isSupported(@Nullable ICraftingCPU cpu) {
        return parkable(cpu) != null;
    }
}
