package dev.BloodyDreamsWork.ae2_scheduler.park;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;

public interface ParkableCpu {
    @Nullable
    static ParkableCpu of(@Nullable Object craftingCpuLogic) {
        return craftingCpuLogic instanceof ParkableCpu parkable ? parkable : null;
    }

    boolean acs$isParked();

    boolean acs$park(UUID owner, long complexity);

    boolean acs$unpark();

    ICraftingSubmitResult acs$submitExpress(IGrid grid, ICraftingPlan plan, IActionSource src,
            @Nullable ICraftingRequester requester);

    boolean acs$hasActiveJob();

    void acs$abandonPark();

    void acs$evacuateParkForDestruction();

    @Nullable
    UUID acs$getParkOwner();

    void acs$heartbeatPark(UUID owner);

    long acs$getTicksSinceParkHeartbeat();

    @Nullable
    GenericStack acs$getParkedOutput();

    long acs$getParkedRemainingAmount();

    float acs$getParkedProgress();

    long acs$getParkedComplexity();

    int acs$getParkedInFlightCount();

    long acs$getParkedDuration();

    void acs$setActiveComplexity(long complexity);

    long acs$getActiveComplexity();
}
