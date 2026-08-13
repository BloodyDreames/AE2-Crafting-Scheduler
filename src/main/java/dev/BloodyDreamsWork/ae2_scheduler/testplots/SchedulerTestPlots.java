package dev.BloodyDreamsWork.ae2_scheduler.testplots;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.core.definitions.AEBlocks;
import appeng.me.helpers.BaseActionSource;
import appeng.me.helpers.MachineSource;
import appeng.server.testplots.CraftingPatternHelper;
import appeng.server.testplots.TestPlot;
import appeng.server.testplots.TestPlotClass;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;

import dev.BloodyDreamsWork.ae2_scheduler.park.ParkableCpu;
import dev.BloodyDreamsWork.ae2_scheduler.registry.ModBlocks;
import dev.BloodyDreamsWork.ae2_scheduler.scheduler.CpuKey;
import dev.BloodyDreamsWork.ae2_scheduler.scheduler.SchedulerBlockEntity;

/**
 * Game tests for the pause/resume pipeline, built on AE2's own test-world framework.
 *
 * <p>
 * Run them with {@code gradlew runGameTestServer}. The thresholds these tests rely on are set in
 * {@code run-gametest/defaultconfigs/ae2_crafting_scheduler-server.toml}, which lowers
 * {@code minimumJobComplexityForPreemption} so a job small enough to finish inside a game test still
 * counts as "big".
 *
 * <p>
 * The recipes used throughout are vanilla: {@code 2 oak planks -> 4 sticks} for the long-running job
 * and {@code 4 oak planks -> 1 crafting table} for the express job. The item cell is stocked with
 * <em>exactly</em> the planks both jobs need, so "the network ends up with every stick, every crafting
 * table and zero planks" is a direct proof that nothing was duplicated and nothing was lost.
 */
@TestPlotClass
public final class SchedulerTestPlots {

    /** Grid origin. Every helper resolves the grid through this position, so it must carry a node. */
    private static final BlockPos ORIGIN = BlockPos.ZERO;

    private static final BlockPos SCHEDULER_POS = new BlockPos(1, 0, 0);
    private static final BlockPos CPU_A = new BlockPos(-1, 0, 0);
    private static final BlockPos CPU_B = new BlockPos(-1, 0, -2);
    private static final BlockPos DRIVE_POS = new BlockPos(1, 0, -4);

    private static final int STICK_TARGET = 400;
    private static final int PLANKS_FOR_STICKS = STICK_TARGET / 4 * 2;

    /**
     * The express job is eight crafting tables rather than one: eight operations still counts as an
     * express craft, but it keeps the CPU busy long enough for the tests to observe the paused state
     * across several ticks instead of a single one.
     */
    private static final int EXPRESS_TABLES = 8;
    private static final int PLANKS_FOR_TABLE = EXPRESS_TABLES * 4;

    private SchedulerTestPlots() {
    }

    // ------------------------------------------------------------------------------------------
    // Shared setup
    // ------------------------------------------------------------------------------------------

    /**
     * A minimal but complete autocrafting network: controller, one or two single-block Crafting CPUs, a
     * pattern provider with molecular assemblers, a drive, and the Scheduler.
     */
    private static void baseNetwork(PlotBuilder plot, boolean secondCpu, int extraPlanks) {
        // Layout follows AE2's own autocrafting test plots: one cable run along -Z with every machine
        // hanging off its side, so each device has an unambiguous adjacent cable to connect to.
        plot.creativeEnergyCell("0 -1 -1");
        plot.cable("0 0 [-4,0]");
        plot.block("0 1 0", AEBlocks.CONTROLLER);

        plot.block("1 0 0", ModBlocks.CRAFTING_SCHEDULER.get());

        plot.block(CPU_A, AEBlocks.CRAFTING_STORAGE_64K);
        if (secondCpu) {
            plot.block(CPU_B, AEBlocks.CRAFTING_STORAGE_64K);
        }

        plot.blockEntity("1 0 -2", AEBlocks.PATTERN_PROVIDER, provider -> {
            var level = (ServerLevel) provider.getLevel();
            var inv = provider.getLogic().getPatternInv();
            // 2 oak planks (stacked vertically) -> 4 sticks
            inv.addItems(CraftingPatternHelper.encodeCraftingPattern(level, new Object[] {
                    Items.OAK_PLANKS, null, null,
                    Items.OAK_PLANKS, null, null,
                    null, null, null
            }, false, false));
            // 4 oak planks -> 1 crafting table
            inv.addItems(CraftingPatternHelper.encodeCraftingPattern(level, new Object[] {
                    Items.OAK_PLANKS, Items.OAK_PLANKS, null,
                    Items.OAK_PLANKS, Items.OAK_PLANKS, null,
                    null, null, null
            }, false, false));
        });
        plot.block("2 0 -2", AEBlocks.MOLECULAR_ASSEMBLER);
        plot.block("1 1 -2", AEBlocks.MOLECULAR_ASSEMBLER);

        var drive = plot.drive(DRIVE_POS);
        drive.addItemCell64k().add(Items.OAK_PLANKS,
                PLANKS_FOR_STICKS + PLANKS_FOR_TABLE + extraPlanks);

    }

    /**
     * Opts every CPU on the network in, the way a player would tick them in the GUI. CPUs are keyed by
     * their world position, so this has to run against the built plot rather than with the plot's own
     * relative coordinates.
     */
    private static void manageAllCpus(PlotTestHelper helper) {
        var grid = helper.getGrid(ORIGIN);
        for (var scheduler : grid.getMachines(SchedulerBlockEntity.class)) {
            for (var cpu : grid.getCraftingService().getCpus()) {
                var key = CpuKey.of(cpu);
                if (key != null) {
                    scheduler.setManaged(key, true);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // 1. Basic: pause a big job, run a small one, resume, and account for every item.
    // ------------------------------------------------------------------------------------------

    @TestPlot("scheduler_pause_resume")
    public static void pauseResume(PlotBuilder plot) {
        baseNetwork(plot, false, 0);

        plot.test(helper -> {
            var bigJob = new CraftRequest(AEItemKey.of(Items.STICK), STICK_TARGET);
            var expressJob = new CraftRequest(AEItemKey.of(Items.CRAFTING_TABLE), EXPRESS_TABLES);

            helper.startSequence()
                    .thenExecute(() -> assertNetworkIsSane(helper, PLANKS_FOR_STICKS + PLANKS_FOR_TABLE))
                    .thenWaitUntil(() -> bigJob.poll(helper))
                    .thenExecute(() -> bigJob.require(helper, "the background job"))
                    // Let the job actually get going so there is real state to preserve: pushed
                    // patterns, intermediate items, and results still in flight from the assemblers.
                    .thenIdle(20)
                    .thenExecute(() -> {
                        var park = requirePark(helper);
                        helper.check(!park.acs$isParked(), "nothing should be paused yet");
                        helper.check(park.acs$getActiveComplexity() > 0,
                                "the running job's complexity was not recorded");
                        helper.check(activeProgress(helper) > 0, "the big job made no progress");
                    })
                    // The pause and the express submit both happen inside the submit call, so the flag
                    // captured there is the authoritative answer to "was the big job paused for it?".
                    .thenWaitUntil(() -> expressJob.poll(helper))
                    .thenExecute(() -> {
                        expressJob.require(helper, "the express job");
                        helper.check(expressJob.parkedOnSubmit(),
                                "the big job was not paused for the express job");
                    })
                    // Everything from here on is the real acceptance criterion: both jobs finish, and
                    // the item count in the world adds up exactly.
                    .thenWaitUntil(() -> {
                        helper.assertNetworkContains(ORIGIN, Items.CRAFTING_TABLE);
                        helper.assertNetworkContains(ORIGIN, Items.STICK);
                        var storage = helper.getGrid(ORIGIN).getStorageService().getInventory();
                        var sticks = storage.extract(AEItemKey.of(Items.STICK), Long.MAX_VALUE,
                                appeng.api.config.Actionable.SIMULATE, new BaseActionSource());
                        helper.check(sticks == STICK_TARGET,
                                "expected exactly " + STICK_TARGET + " sticks, found " + sticks);
                    })
                    .thenExecute(() -> {
                        var park = requirePark(helper);
                        helper.check(!park.acs$isParked(), "a job is still stuck in the park slot");
                        // Every plank that went in came back out as a stick or a crafting table.
                        helper.assertNetworkContainsNot(ORIGIN, Items.OAK_PLANKS);
                        var storage = helper.getGrid(ORIGIN).getStorageService().getInventory();
                        var tables = storage.extract(AEItemKey.of(Items.CRAFTING_TABLE), Long.MAX_VALUE,
                                appeng.api.config.Actionable.SIMULATE, new BaseActionSource());
                        helper.check(tables == EXPRESS_TABLES,
                                "expected exactly " + EXPRESS_TABLES + " crafting tables, found " + tables);
                    })
                    .thenSucceed();
        }).maxTicks(1200).setupTicks(80);
    }

    // ------------------------------------------------------------------------------------------
    // 2. A paused job survives being written to and read back from NBT.
    // ------------------------------------------------------------------------------------------

    /**
     * This is the server-restart path. A game test cannot restart the server, but it can exercise the
     * exact code that a restart runs: the CPU's own {@code writeToNBT} / {@code readFromNBT}, which is
     * where the park state lives.
     */
    @TestPlot("scheduler_park_survives_nbt")
    public static void parkSurvivesNbt(PlotBuilder plot) {
        baseNetwork(plot, false, 0);

        plot.test(helper -> {
            var bigJob = new CraftRequest(AEItemKey.of(Items.STICK), STICK_TARGET);
            // The round trip happens on the submit tick itself, while the job is definitely parked.
            var expressJob = new CraftRequest(AEItemKey.of(Items.CRAFTING_TABLE), EXPRESS_TABLES)
                    .onSubmitted(() -> roundTripThroughNbt(helper));

            helper.startSequence()
                    .thenExecute(() -> assertNetworkIsSane(helper, PLANKS_FOR_STICKS + PLANKS_FOR_TABLE))
                    .thenWaitUntil(() -> bigJob.poll(helper))
                    .thenExecute(() -> bigJob.require(helper, "the background job"))
                    .thenIdle(20)
                    .thenWaitUntil(() -> expressJob.poll(helper))
                    .thenExecute(() -> expressJob.require(helper, "the express job"))
                    // And it still resumes and completes correctly afterwards.
                    .thenWaitUntil(() -> {
                        var storage = helper.getGrid(ORIGIN).getStorageService().getInventory();
                        var sticks = storage.extract(AEItemKey.of(Items.STICK), Long.MAX_VALUE,
                                appeng.api.config.Actionable.SIMULATE, new BaseActionSource());
                        helper.check(sticks == STICK_TARGET,
                                "expected " + STICK_TARGET + " sticks after the reload, found " + sticks);
                    })
                    .thenSucceed();
        }).maxTicks(1200).setupTicks(80);
    }

    // ------------------------------------------------------------------------------------------
    // 3. Removing the Scheduler must resume, not strand, what it was holding.
    // ------------------------------------------------------------------------------------------

    @TestPlot("scheduler_removal_resumes_job")
    public static void removalResumesJob(PlotBuilder plot) {
        baseNetwork(plot, false, 0);

        plot.test(helper -> {
            var bigJob = new CraftRequest(AEItemKey.of(Items.STICK), STICK_TARGET);
            // Break the Scheduler on the submit tick, while it is definitely holding the big job.
            var expressJob = new CraftRequest(AEItemKey.of(Items.CRAFTING_TABLE), EXPRESS_TABLES)
                    .onSubmitted(() -> {
                        helper.check(requirePark(helper).acs$isParked(), "the big job was not paused");
                        helper.getLevel().removeBlock(helper.absolutePos(SCHEDULER_POS), false);
                    });

            helper.startSequence()
                    .thenExecute(() -> assertNetworkIsSane(helper, PLANKS_FOR_STICKS + PLANKS_FOR_TABLE))
                    .thenWaitUntil(() -> bigJob.poll(helper))
                    .thenExecute(() -> bigJob.require(helper, "the background job"))
                    .thenIdle(20)
                    .thenWaitUntil(() -> expressJob.poll(helper))
                    .thenExecute(() -> expressJob.require(helper, "the express job"))
                    .thenWaitUntil(() -> helper.check(!requirePark(helper).acs$isParked(),
                            "breaking the Scheduler left a job stranded in the park slot"))
                    .thenWaitUntil(() -> {
                        var storage = helper.getGrid(ORIGIN).getStorageService().getInventory();
                        var sticks = storage.extract(AEItemKey.of(Items.STICK), Long.MAX_VALUE,
                                appeng.api.config.Actionable.SIMULATE, new BaseActionSource());
                        helper.check(sticks == STICK_TARGET,
                                "the big job did not finish after the Scheduler was removed, found "
                                        + sticks + " sticks");
                    })
                    .thenSucceed();
        }).maxTicks(1200).setupTicks(80);
    }

    // ------------------------------------------------------------------------------------------
    // 4. With several busy CPUs, the Scheduler must pick one deterministically and pause only it.
    // ------------------------------------------------------------------------------------------

    @TestPlot("scheduler_picks_one_cpu")
    public static void picksOneCpu(PlotBuilder plot) {
        // The two background jobs add up to STICK_TARGET, so the stock planks are exactly right.
        baseNetwork(plot, true, 0);

        plot.test(helper -> {
            // Two background jobs, one per CPU, so nothing is free when the express request arrives.
            var firstJob = new CraftRequest(AEItemKey.of(Items.STICK), STICK_TARGET / 2);
            var secondJob = new CraftRequest(AEItemKey.of(Items.STICK), STICK_TARGET / 2);
            var expressJob = new CraftRequest(AEItemKey.of(Items.CRAFTING_TABLE), EXPRESS_TABLES)
                    .onSubmitted(() -> {
                        int parked = 0;
                        for (var cpu : helper.getGrid(ORIGIN).getCraftingService().getCpus()) {
                            var park = CpuKey.parkable(cpu);
                            if (park != null && park.acs$isParked()) {
                                parked++;
                            }
                        }
                        helper.check(parked == 1,
                                "expected exactly one CPU to be paused, found " + parked);
                    });

            helper.startSequence()
                    .thenExecute(() -> assertNetworkIsSane(helper, PLANKS_FOR_STICKS + PLANKS_FOR_TABLE))
                    .thenWaitUntil(() -> firstJob.poll(helper))
                    .thenExecute(() -> firstJob.require(helper, "the first background job"))
                    .thenWaitUntil(() -> secondJob.poll(helper))
                    .thenExecute(() -> secondJob.require(helper, "the second background job"))
                    .thenIdle(20)
                    .thenWaitUntil(() -> expressJob.poll(helper))
                    .thenExecute(() -> expressJob.require(helper, "the express job"))
                    .thenWaitUntil(() -> {
                        var storage = helper.getGrid(ORIGIN).getStorageService().getInventory();
                        var sticks = storage.extract(AEItemKey.of(Items.STICK), Long.MAX_VALUE,
                                appeng.api.config.Actionable.SIMULATE, new BaseActionSource());
                        helper.check(sticks == STICK_TARGET,
                                "both background jobs should still complete in full, found " + sticks);
                    })
                    .thenExecute(() -> {
                        for (var cpu : helper.getGrid(ORIGIN).getCraftingService().getCpus()) {
                            var park = CpuKey.parkable(cpu);
                            helper.check(park == null || !park.acs$isParked(),
                                    "a job is still stuck in a park slot");
                        }
                    })
                    .thenSucceed();
        }).maxTicks(1600).setupTicks(80);
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    /** Tells apart "block missing", "not a grid host", "node not created" and "not connected". */
    private static String describeHost(PlotTestHelper helper, BlockPos relative) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(relative);
        var block = level.getBlockState(pos).getBlock();
        var host = appeng.api.networking.GridHelper.getNodeHost(level, pos);
        if (host == null) {
            return block + "(no grid-host capability)";
        }
        var node = host.getGridNode(null);
        if (node == null) {
            return block + "(host, but no node)";
        }
        return block + "(node, connections=" + node.getConnections().size() + ", active=" + node.isActive()
                + ", grid=" + System.identityHashCode(node.getGrid()) + ")";
    }

    /**
     * Writes the CPU out and reads it straight back, which is exactly what a server restart does to a
     * paused job, and checks that nothing about the park changed.
     */
    private static void roundTripThroughNbt(PlotTestHelper helper) {
        var cluster = requireCluster(helper);
        var park = requirePark(helper);
        helper.check(park.acs$isParked(), "the big job was not paused");

        var remainingBefore = park.acs$getParkedRemainingAmount();
        var ownerBefore = park.acs$getParkOwner();
        var complexityBefore = park.acs$getParkedComplexity();

        var registries = helper.getLevel().registryAccess();
        var tag = new CompoundTag();
        cluster.craftingLogic.writeToNBT(tag, registries);
        helper.check(tag.contains("acs_park"), "the paused job was not written to the CPU's NBT");

        cluster.craftingLogic.readFromNBT(tag, registries);

        var reloaded = requirePark(helper);
        helper.check(reloaded.acs$isParked(), "the paused job did not survive a save/load");
        helper.check(reloaded.acs$getParkedRemainingAmount() == remainingBefore,
                "the paused job's remaining amount changed across save/load");
        helper.check(Objects.equals(reloaded.acs$getParkOwner(), ownerBefore),
                "the paused job lost its owning Scheduler across save/load");
        helper.check(reloaded.acs$getParkedComplexity() == complexityBefore,
                "the paused job lost its recorded size across save/load");
    }

    /**
     * Fails loudly and specifically if the plot did not come up the way the tests assume. Without this
     * every downstream failure just says "job rejected", which says nothing about why.
     */
    private static void assertNetworkIsSane(PlotTestHelper helper, int expectedPlanks) {
        var grid = helper.getGrid(ORIGIN);

        var cpus = grid.getCraftingService().getCpus();
        helper.check(!cpus.isEmpty(), "no Crafting CPU formed on the test network");
        for (var cpu : cpus) {
            helper.check(CpuKey.parkable(cpu) != null,
                    "the Crafting CPU is not parkable -- the mixin did not apply");
        }

        var craftables = grid.getCraftingService().getCraftables(k -> true);
        helper.check(craftables.contains(AEItemKey.of(Items.STICK)),
                "no pattern produces sticks; craftables are " + craftables);

        var stickPatterns = grid.getCraftingService().getCraftingFor(AEItemKey.of(Items.STICK));
        var described = new StringBuilder();
        for (var pattern : stickPatterns) {
            described.append("[inputs=").append(pattern.getInputs().length).append(" outputs=");
            for (var out : pattern.getOutputs()) {
                described.append(out.what().getId()).append('x').append(out.amount()).append(' ');
            }
            described.append("providers=");
            int providers = 0;
            for (var ignored : ((appeng.me.service.CraftingService) grid.getCraftingService())
                    .getProviders(pattern)) {
                providers++;
            }
            described.append(providers).append("] ");
        }
        helper.check(!stickPatterns.isEmpty() && described.indexOf("providers=0") < 0,
                "the stick pattern is not usable: " + described);
        helper.check(craftables.contains(AEItemKey.of(Items.CRAFTING_TABLE)),
                "no pattern produces crafting tables; craftables are " + craftables);

        var storage = grid.getStorageService().getInventory();
        long planks = storage.extract(AEItemKey.of(Items.OAK_PLANKS), Long.MAX_VALUE,
                appeng.api.config.Actionable.SIMULATE, new BaseActionSource());
        if (planks != expectedPlanks) {
            // Usually means the drive did not get a channel, so say so instead of just the count.
            var contents = new appeng.api.stacks.KeyCounter();
            storage.getAvailableStacks(contents);
            var owners = new StringBuilder();
            for (var node : grid.getNodes()) {
                var owner = node.getOwner();
                owners.append(owner.getClass().getSimpleName());
                if (owner instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
                    owners.append('@').append(helper.relativePos(be.getBlockPos()).toShortString());
                }
                owners.append(node.isActive() ? " " : "(inactive) ");
            }
            var level = helper.getLevel();
            helper.check(false, "expected " + expectedPlanks + " oak planks in the network, found "
                    + planks + "; network holds " + contents.size() + " kinds of item; nodes: " + owners
                    + " | scheduler " + describeHost(helper, SCHEDULER_POS)
                    + " | drive " + describeHost(helper, DRIVE_POS)
                    + " | cable end " + describeHost(helper, new BlockPos(0, 0, -4))
                    + " | cpu " + describeHost(helper, CPU_A)
                    + " | level " + level.dimension().location());
        }

        manageAllCpus(helper);

        boolean foundScheduler = false;
        for (var scheduler : grid.getMachines(SchedulerBlockEntity.class)) {
            foundScheduler = true;
            helper.check(scheduler.isOperational(), "the Scheduler is not operational");
            helper.check(scheduler.getManagedCpus().size() == cpus.size(),
                    "the Scheduler manages " + scheduler.getManagedCpus().size() + " of " + cpus.size()
                            + " CPUs");
        }
        helper.check(foundScheduler,
                "the Scheduler is not on the grid: " + describeHost(helper, SCHEDULER_POS)
                        + "; cable next to it: " + describeHost(helper, new BlockPos(0, 0, 0)));
    }

    /**
     * A crafting request driven one tick at a time.
     *
     * <p>
     * The plan is computed on AE2's calculation thread, which needs the server thread to keep ticking,
     * so it must never be waited on synchronously. {@link #poll} is written for
     * {@code GameTestSequence#thenWaitUntil}: it throws to be retried until the plan is ready, and on
     * the tick it finally submits it records both the result <em>and</em> whether the CPU was holding a
     * paused job at that exact moment. That capture is what makes the pause assertion deterministic:
     * the preemption happens inside the submit call, and a one-operation express job can be finished
     * again before the next tick runs.
     */
    private static final class CraftRequest {
        private final AEKey what;
        private final long amount;

        @Nullable
        private Future<ICraftingPlan> future;
        @Nullable
        private ICraftingPlan plan;
        @Nullable
        private ICraftingSubmitResult result;
        private boolean parkedOnSubmit;
        @Nullable
        private Runnable onSubmitted;

        CraftRequest(AEKey what, long amount) {
            this.what = what;
            this.amount = amount;
        }

        /**
         * Runs on the very tick the job is submitted, right after the submit returns. Tests that need
         * to observe or disturb the paused state use this rather than a following step, because a
         * short express job can be finished again before the next tick.
         */
        CraftRequest onSubmitted(Runnable action) {
            this.onSubmitted = action;
            return this;
        }

        void poll(PlotTestHelper helper) {
            if (result != null) {
                return;
            }
            var grid = helper.getGrid(ORIGIN);

            if (future == null) {
                // Same source AE2's own TestCraftingJob uses for planning: a machine source anchored on
                // the grid pivot. A plain BaseActionSource has no identity and the simulated extraction
                // finds nothing, which shows up as "the requested item itself is missing".
                var planningSource = new MachineSource(grid::getPivot);
                ICraftingSimulationRequester simRequester = () -> planningSource;
                future = grid.getCraftingService().beginCraftingCalculation(
                        grid.getPivot().getLevel(), simRequester, what, amount,
                        CalculationStrategy.REPORT_MISSING_ITEMS);
            }
            if (plan == null) {
                try {
                    plan = future.get(0, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    // Not ready yet; thenWaitUntil will call us again next tick.
                    throw new GameTestAssertException("crafting calculation for " + what + " is still running");
                } catch (InterruptedException | ExecutionException e) {
                    throw new GameTestAssertException("crafting calculation for " + what + " failed: " + e);
                }
            }
            // Outside the block above on purpose: `plan` is cached, so this has to be re-checked on
            // every retry, or a simulated plan would silently be submitted on the second pass.
            if (plan.simulation()) {
                var missing = new StringBuilder();
                for (var entry : plan.missingItems()) {
                    missing.append(entry.getKey().getId()).append(" x").append(entry.getLongValue())
                            .append("; ");
                }
                // Throw the plan away and recompute next tick: early in a plot's life the network can
                // still be settling, and a plan computed then would be cached forever.
                plan = null;
                future = null;
                throw new GameTestAssertException(
                        "crafting plan for " + what + " is incomplete, missing " + missing);
            }

            result = grid.getCraftingService().submitJob(plan, null, null, true, new BaseActionSource());
            var park = CpuKey.parkable(firstCluster(grid));
            parkedOnSubmit = park != null && park.acs$isParked();
            if (onSubmitted != null) {
                onSubmitted.run();
            }
        }

        ICraftingSubmitResult require(PlotTestHelper helper, String what) {
            helper.check(result != null, what + " was never submitted");
            helper.check(result.successful(), what + " was rejected: " + result.errorCode());
            return result;
        }

        boolean parkedOnSubmit() {
            return parkedOnSubmit;
        }
    }

    private static appeng.me.cluster.implementations.CraftingCPUCluster requireCluster(
            PlotTestHelper helper) {
        var cluster = firstCluster(helper.getGrid(ORIGIN));
        if (cluster == null) {
            throw new GameTestAssertException("No Crafting CPU on the test network");
        }
        return cluster;
    }

    private static ParkableCpu requirePark(PlotTestHelper helper) {
        var park = CpuKey.parkable(requireCluster(helper));
        if (park == null) {
            throw new GameTestAssertException(
                    "The Crafting CPU is not parkable -- the mixin did not apply");
        }
        return park;
    }

    private static float activeProgress(PlotTestHelper helper) {
        return requireCluster(helper).craftingLogic.getElapsedTimeTracker().getProgress();
    }

    @Nullable
    private static appeng.me.cluster.implementations.CraftingCPUCluster firstCluster(IGrid grid) {
        for (var cpu : grid.getCraftingService().getCpus()) {
            if (cpu instanceof appeng.me.cluster.implementations.CraftingCPUCluster cluster) {
                return cluster;
            }
        }
        return null;
    }
}
