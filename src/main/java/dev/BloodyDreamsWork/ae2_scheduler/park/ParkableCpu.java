package dev.BloodyDreamsWork.ae2_scheduler.park;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.GenericStack;

/**
 * The park slot that this addon adds to AE2's {@code CraftingCpuLogic}.
 *
 * <p>
 * A "parked" job is a fully intact {@code ExecutingCraftingJob} that has been moved out of the CPU's
 * active slot. The CPU therefore reports itself as free and accepts a new job, while the parked job
 * keeps its remaining pattern pushes, its intermediate items, its expectation ledger
 * ({@code waitingFor}) and its crafting link, and keeps accepting results of operations that were
 * dispatched to machines before the pause.
 *
 * <p>
 * Every {@code CraftingCpuLogic} instance implements this interface at runtime through
 * {@code CraftingCpuLogicMixin}. Use {@link ParkableCpu#of} to obtain it, which returns {@code null}
 * for any CPU implementation the Mixin did not reach -- those are reported as {@code Unsupported CPU}
 * instead of crashing.
 */
public interface ParkableCpu {

    /**
     * @return the park view of an AE2 crafting CPU cluster, or null if this CPU cannot be paused (a
     *         third-party CPU implementation, or one our Mixin did not apply to).
     */
    @Nullable
    static ParkableCpu of(@Nullable Object craftingCpuLogic) {
        return craftingCpuLogic instanceof ParkableCpu parkable ? parkable : null;
    }

    /** True if this CPU currently holds a paused job. */
    boolean acs$isParked();

    /**
     * Moves the currently running job into the park slot without cancelling it.
     *
     * <p>
     * This is atomic within one server tick: on return the CPU reports {@code isBusy() == false} and a
     * new job can be submitted to it immediately.
     *
     * @param owner      the Scheduler claiming this CPU; also acts as the exclusive lock.
     * @param complexity estimated total operations of the job being parked, used later for the GUI and
     *                   for victim ranking.
     * @return false if there was no job to park, or the CPU is already parked.
     */
    boolean acs$park(UUID owner, long complexity);

    /**
     * Moves the parked job back into the active slot. Requires the CPU to be free.
     *
     * @return false if there is nothing parked, or the CPU is busy with another job.
     */
    boolean acs$unpark();

    /**
     * Cancels the parked job the way AE2 cancels a running one: the link is cancelled and every held
     * intermediate item is returned to the network. Used when the job can never be resumed.
     */
    void acs$abandonPark();

    /**
     * Last-resort teardown used when the CPU multiblock is being destroyed and there will be no further
     * ticks to hand items back to the network: cancels the parked job and moves its items into the
     * CPU's normal inventory, which AE2's own {@code breakCluster} drops on the ground.
     */
    void acs$evacuateParkForDestruction();

    /** The Scheduler that owns the current park, or null. */
    @Nullable
    UUID acs$getParkOwner();

    /** Lets the owning Scheduler renew its claim; drives the orphaned-park watchdog. */
    void acs$heartbeatPark(UUID owner);

    /** Server ticks since the owning Scheduler last checked in. */
    long acs$getTicksSinceParkHeartbeat();

    /** Final output of the parked job, or null when nothing is parked. */
    @Nullable
    GenericStack acs$getParkedOutput();

    /** How much of the parked job's final output is still owed. */
    long acs$getParkedRemainingAmount();

    /** Progress of the parked job in [0, 1], as AE2 computes it for a running job. */
    float acs$getParkedProgress();

    /** Estimated total operations of the parked job, as recorded at park time. */
    long acs$getParkedComplexity();

    /**
     * Number of distinct item/fluid keys the parked job is still waiting for, i.e. operations that were
     * dispatched to machines before the pause and have not come back yet.
     */
    int acs$getParkedInFlightCount();

    /** Server ticks the current job has been parked for. */
    long acs$getParkedDuration();

    /** Records the estimated complexity of the job currently running on this CPU. */
    void acs$setActiveComplexity(long complexity);

    /** Estimated total operations of the job currently running on this CPU, or 0 if unknown. */
    long acs$getActiveComplexity();
}
