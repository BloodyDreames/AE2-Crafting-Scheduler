package dev.BloodyDreamsWork.ae2_scheduler.scheduler;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import dev.BloodyDreamsWork.ae2_scheduler.park.ParkableCpu;

public final class CpuKey {
    private CpuKey() {
    }

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
