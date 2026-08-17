package dev.BloodyDreamsWork.ae2_scheduler.testplots;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

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
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.BlockDefinition;
import appeng.me.helpers.BaseActionSource;
import appeng.me.helpers.MachineSource;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.server.testplots.CraftingPatternHelper;
import appeng.server.testplots.TestPlot;
import appeng.server.testplots.TestPlotClass;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;
import appeng.util.Platform;

import dev.BloodyDreamsWork.ae2_scheduler.park.ParkableCpu;
import dev.BloodyDreamsWork.ae2_scheduler.registry.ModBlocks;
import dev.BloodyDreamsWork.ae2_scheduler.scheduler.CpuKey;
import dev.BloodyDreamsWork.ae2_scheduler.scheduler.SchedulerBlockEntity;

@TestPlotClass
public final class SchedulerTestPlots {
    private static final BlockPos ORIGIN = BlockPos.ZERO;

    private static final BlockPos SCHEDULER_POS = new BlockPos(1, 0, 0);
    private static final BlockPos CPU_A = new BlockPos(-1, 0, 0);
    private static final BlockPos CPU_B = new BlockPos(-1, 0, -2);
    private static final BlockPos DRIVE_POS = new BlockPos(1, 0, -4);
    private static final BlockPos MENU_HOST_POS = new BlockPos(1, 0, -3);

    private static final int STICK_TARGET = 400;
    private static final int PLANKS_FOR_STICKS = STICK_TARGET / 4 * 2;

    private static final int EXPRESS_TABLES = 8;
    private static final int PLANKS_FOR_TABLE = EXPRESS_TABLES * 4;

    private static final int ONE_K_STICK_TARGET = 560;
    private static final int ONE_K_PLANKS_FOR_STICKS = ONE_K_STICK_TARGET / 4 * 2;
    private static final long ONE_K_BYTES = 1024;

    private SchedulerTestPlots() {
    }

    private static void baseNetwork(PlotBuilder plot, boolean secondCpu, int extraPlanks) {
        baseNetwork(plot, secondCpu, extraPlanks, AEBlocks.CRAFTING_STORAGE_64K,
                PLANKS_FOR_STICKS + PLANKS_FOR_TABLE);
    }

    private static void baseNetwork(PlotBuilder plot, boolean secondCpu, int extraPlanks,
            BlockDefinition<?> cpuStorage, int basePlanks) {
        plot.creativeEnergyCell("0 -1 -1");
        plot.cable("0 0 [-4,0]");
        plot.block("0 1 0", AEBlocks.CONTROLLER);

        plot.block("1 0 0", ModBlocks.CRAFTING_SCHEDULER.get());

        plot.block(CPU_A, cpuStorage);
        if (secondCpu) {
            plot.block(CPU_B, cpuStorage);
        }

        plot.blockEntity("1 0 -2", AEBlocks.PATTERN_PROVIDER, provider -> {
            var level = (ServerLevel) provider.getLevel();
            var inv = provider.getLogic().getPatternInv();
            inv.addItems(CraftingPatternHelper.encodeCraftingPattern(level, new Object[] {
                    Items.OAK_PLANKS, null, null,
                    Items.OAK_PLANKS, null, null,
                    null, null, null
            }, false, false));
            inv.addItems(CraftingPatternHelper.encodeCraftingPattern(level, new Object[] {
                    Items.OAK_PLANKS, Items.OAK_PLANKS, null,
                    Items.OAK_PLANKS, Items.OAK_PLANKS, null,
                    null, null, null
            }, false, false));
        });
        plot.block("2 0 -2", AEBlocks.MOLECULAR_ASSEMBLER);
        plot.block("1 1 -2", AEBlocks.MOLECULAR_ASSEMBLER);

        var drive = plot.drive(DRIVE_POS);
        drive.addItemCell64k().add(Items.OAK_PLANKS, basePlanks + extraPlanks);
    }

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

    @TestPlot("scheduler_busy_1k_cpu_preempts")
    public static void busyOneKCpuPreempts(PlotBuilder plot) {
        var expectedPlanks = ONE_K_PLANKS_FOR_STICKS + PLANKS_FOR_TABLE;
        baseNetwork(plot, false, 0, AEBlocks.CRAFTING_STORAGE_1K, expectedPlanks);

        plot.test(helper -> {
            var bigJob = new CraftRequest(AEItemKey.of(Items.STICK), ONE_K_STICK_TARGET);
            var expressJob = new CraftRequest(AEItemKey.of(Items.CRAFTING_TABLE), EXPRESS_TABLES);

            helper.startSequence()
                    .thenExecute(() -> assertNetworkIsSane(helper, expectedPlanks))
                    .thenWaitUntil(() -> bigJob.poll(helper))
                    .thenExecute(() -> {
                        bigJob.require(helper, "the near-capacity background job");
                        var cluster = requireCluster(helper);
                        helper.check(cluster.getAvailableStorage() == ONE_K_BYTES,
                                "expected a 1024-byte CPU, found " + cluster.getAvailableStorage());
                        helper.check(bigJob.plannedBytes() <= ONE_K_BYTES,
                                "the background plan does not fit the 1K CPU: "
                                        + bigJob.plannedBytes() + " bytes");
                        helper.check(bigJob.plannedBytes() * 100 >= ONE_K_BYTES * 95,
                                "the regression plan should fill at least 95% of the 1K CPU, but uses only "
                                        + bigJob.plannedBytes() + " bytes");
                    })
                    .thenIdle(20)
                    .thenExecute(() -> helper.check(requireCluster(helper).isBusy(),
                            "the only 1K CPU must still be busy before the express request"))
                    .thenWaitUntil(() -> expressJob.poll(helper))
                    .thenExecute(() -> {
                        expressJob.require(helper, "the small express job");
                        helper.check(expressJob.plannedBytes() < bigJob.plannedBytes(),
                                "the express job is not smaller than the background job");
                        helper.check(expressJob.plannedBytes() <= ONE_K_BYTES,
                                "the express plan does not fit the 1K CPU");
                        helper.check(expressJob.parkedOnSubmit(),
                                "the near-capacity job was not moved into the CPU park slot on submit");

                        var park = requirePark(helper);
                        helper.check(park.acs$getParkedOutput() != null
                                        && AEItemKey.of(Items.STICK).equals(park.acs$getParkedOutput().what()),
                                "the park slot does not hold the original stick job");
                        helper.check(park.acs$getParkedRemainingAmount() > 0,
                                "the original job was already empty when it was parked");
                    })
                    .thenWaitUntil(() -> {
                        var storage = helper.getGrid(ORIGIN).getStorageService().getInventory();
                        var sticks = storage.extract(AEItemKey.of(Items.STICK), Long.MAX_VALUE,
                                appeng.api.config.Actionable.SIMULATE, new BaseActionSource());
                        var tables = storage.extract(AEItemKey.of(Items.CRAFTING_TABLE), Long.MAX_VALUE,
                                appeng.api.config.Actionable.SIMULATE, new BaseActionSource());
                        helper.check(sticks == ONE_K_STICK_TARGET && tables == EXPRESS_TABLES,
                                "both jobs must finish after 1K preemption; found " + sticks
                                        + " sticks and " + tables + " tables");
                    })
                    .thenExecute(() -> helper.check(!requirePark(helper).acs$isParked(),
                            "the original job remained stranded in the 1K CPU park slot"))
                    .thenSucceed();
        }).maxTicks(1600).setupTicks(80);
    }

    @TestPlot("scheduler_confirm_menu_keeps_busy_cpu")
    public static void confirmMenuKeepsBusyCpu(PlotBuilder plot) {
        var expectedPlanks = ONE_K_PLANKS_FOR_STICKS + PLANKS_FOR_TABLE;
        baseNetwork(plot, false, 0, AEBlocks.CRAFTING_STORAGE_1K, expectedPlanks);
        plot.block(MENU_HOST_POS, AEBlocks.ME_CHEST);

        plot.test(helper -> {
            var bigJob = new CraftRequest(AEItemKey.of(Items.STICK), ONE_K_STICK_TARGET);
            var expressPlan = new CraftRequest(AEItemKey.of(Items.CRAFTING_TABLE), EXPRESS_TABLES);

            helper.startSequence()
                    .thenExecute(() -> assertNetworkIsSane(helper, expectedPlanks))
                    .thenWaitUntil(() -> bigJob.poll(helper))
                    .thenExecute(() -> bigJob.require(helper, "the near-capacity background job"))
                    .thenIdle(20)
                    .thenWaitUntil(() -> expressPlan.pollPlan(helper))
                    .thenExecute(() -> {
                        var cluster = requireCluster(helper);
                        helper.check(cluster.isBusy(), "the only 1K CPU must be busy for this regression");

                        var host = helper.getBlockEntity(MENU_HOST_POS);
                        helper.check(host instanceof MEChestBlockEntity,
                                "the confirmation-menu host did not form");

                        var player = Platform.getFakePlayer(helper.getLevel(), null);
                        var menu = new CraftConfirmMenu(1, player.getInventory(), (MEChestBlockEntity) host);
                        var plan = expressPlan.plan();
                        menu.setPlan(CraftingPlanSummary.fromJob(helper.getGrid(ORIGIN),
                                new BaseActionSource(), plan));
                        setPrivateField(menu, "result", plan);

                        requirePark(helper).acs$setActiveComplexity(1);

                        helper.check(invokeCpuMatches(menu, cluster),
                                "CraftConfirmMenu removed the preemptible busy CPU after the plan arrived; "
                                        + "the Start button would be disabled");
                    })
                    .thenSucceed();
        }).maxTicks(600).setupTicks(80);
    }

    @TestPlot("scheduler_park_reserves_cpu")
    public static void parkReservesCpu(PlotBuilder plot) {
        baseNetwork(plot, false, 0);

        plot.test(helper -> {
            var bigJob = new CraftRequest(AEItemKey.of(Items.STICK), STICK_TARGET);
            var expressJob = new CraftRequest(AEItemKey.of(Items.CRAFTING_TABLE), EXPRESS_TABLES);
            var intruderResult = new AtomicReference<ICraftingSubmitResult>();

            expressJob.onSubmitted(() -> {
                var grid = helper.getGrid(ORIGIN);
                var cluster = requireCluster(helper);
                var park = requirePark(helper);
                helper.check(park.acs$isParked(), "the background job was not parked");
                helper.check(park.acs$hasActiveJob(), "the express job never entered the active slot");

                cluster.cancelJob();
                helper.check(!park.acs$hasActiveJob(), "cancelling express did not empty the active slot");
                helper.check(cluster.isBusy(),
                        "a CPU with a parked job must advertise its reservation as busy");

                intruderResult.set(grid.getCraftingService().submitJob(expressJob.plan(), null, cluster,
                        true, new BaseActionSource()));
            });

            helper.startSequence()
                    .thenExecute(() -> assertNetworkIsSane(helper, PLANKS_FOR_STICKS + PLANKS_FOR_TABLE))
                    .thenWaitUntil(() -> bigJob.poll(helper))
                    .thenExecute(() -> bigJob.require(helper, "the background job"))
                    .thenIdle(20)
                    .thenWaitUntil(() -> expressJob.poll(helper))
                    .thenExecute(() -> {
                        expressJob.require(helper, "the express job");
                        var rejected = intruderResult.get();
                        helper.check(rejected != null && !rejected.successful(),
                                "an unrelated submit stole the CPU's reserved active slot");
                    })
                    .thenWaitUntil(() -> helper.check(!requirePark(helper).acs$isParked(),
                            "the background job was not restored after the reserved slot became free"))
                    .thenWaitUntil(() -> {
                        var storage = helper.getGrid(ORIGIN).getStorageService().getInventory();
                        var sticks = storage.extract(AEItemKey.of(Items.STICK), Long.MAX_VALUE,
                                appeng.api.config.Actionable.SIMULATE, new BaseActionSource());
                        helper.check(sticks == STICK_TARGET,
                                "the restored background job did not finish; found " + sticks + " sticks");
                    })
                    .thenSucceed();
        }).maxTicks(1400).setupTicks(80);
    }

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
                    .thenIdle(20)
                    .thenExecute(() -> {
                        var park = requirePark(helper);
                        helper.check(!park.acs$isParked(), "nothing should be paused yet");
                        helper.check(park.acs$getActiveComplexity() > 0,
                                "the running job's complexity was not recorded");
                        helper.check(activeProgress(helper) > 0, "the big job made no progress");
                    })
                    .thenWaitUntil(() -> expressJob.poll(helper))
                    .thenExecute(() -> {
                        expressJob.require(helper, "the express job");
                        helper.check(expressJob.parkedOnSubmit(),
                                "the big job was not paused for the express job");
                    })
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

    @TestPlot("scheduler_park_survives_nbt")
    public static void parkSurvivesNbt(PlotBuilder plot) {
        baseNetwork(plot, false, 0);

        plot.test(helper -> {
            var bigJob = new CraftRequest(AEItemKey.of(Items.STICK), STICK_TARGET);
            var expressJob = new CraftRequest(AEItemKey.of(Items.CRAFTING_TABLE), EXPRESS_TABLES)
                    .onSubmitted(() -> roundTripThroughNbt(helper));

            helper.startSequence()
                    .thenExecute(() -> assertNetworkIsSane(helper, PLANKS_FOR_STICKS + PLANKS_FOR_TABLE))
                    .thenWaitUntil(() -> bigJob.poll(helper))
                    .thenExecute(() -> bigJob.require(helper, "the background job"))
                    .thenIdle(20)
                    .thenWaitUntil(() -> expressJob.poll(helper))
                    .thenExecute(() -> expressJob.require(helper, "the express job"))
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

    @TestPlot("scheduler_removal_resumes_job")
    public static void removalResumesJob(PlotBuilder plot) {
        baseNetwork(plot, false, 0);

        plot.test(helper -> {
            var bigJob = new CraftRequest(AEItemKey.of(Items.STICK), STICK_TARGET);
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

    @TestPlot("scheduler_picks_one_cpu")
    public static void picksOneCpu(PlotBuilder plot) {
        baseNetwork(plot, true, 0);

        plot.test(helper -> {
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
        helper.check(tag.contains("job"),
                "the active express job was not written to the CPU's normal NBT slot");
        helper.check(tag.contains("acs_park"), "the paused job was not written to the CPU's NBT");
        helper.check(tag.getCompound("acs_park").getCompound("state").contains("job"),
                "the original job was not written inside the dedicated park slot");

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

    private static boolean invokeCpuMatches(CraftConfirmMenu menu,
            appeng.api.networking.crafting.ICraftingCPU cpu) {
        try {
            var method = CraftConfirmMenu.class.getDeclaredMethod("cpuMatches",
                    appeng.api.networking.crafting.ICraftingCPU.class);
            method.setAccessible(true);
            return (boolean) method.invoke(menu, cpu);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("could not invoke CraftConfirmMenu.cpuMatches: " + e);
        }
    }

    private static void setPrivateField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("could not set " + name + " on "
                    + target.getClass().getSimpleName() + ": " + e);
        }
    }

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

        CraftRequest onSubmitted(Runnable action) {
            this.onSubmitted = action;
            return this;
        }

        void poll(PlotTestHelper helper) {
            if (result != null) {
                return;
            }
            pollPlan(helper);
            var grid = helper.getGrid(ORIGIN);

            result = grid.getCraftingService().submitJob(plan, null, null, true, new BaseActionSource());
            var park = CpuKey.parkable(firstCluster(grid));
            parkedOnSubmit = park != null && park.acs$isParked();
            if (onSubmitted != null) {
                onSubmitted.run();
            }
        }

        void pollPlan(PlotTestHelper helper) {
            if (plan != null) {
                return;
            }
            var grid = helper.getGrid(ORIGIN);

            if (future == null) {
                var planningSource = new MachineSource(grid::getPivot);
                ICraftingSimulationRequester simRequester = () -> planningSource;
                future = grid.getCraftingService().beginCraftingCalculation(
                        grid.getPivot().getLevel(), simRequester, what, amount,
                        CalculationStrategy.REPORT_MISSING_ITEMS);
            }
            try {
                plan = future.get(0, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                throw new GameTestAssertException("crafting calculation for " + what + " is still running");
            } catch (InterruptedException | ExecutionException e) {
                throw new GameTestAssertException("crafting calculation for " + what + " failed: " + e);
            }
            if (plan.simulation()) {
                var missing = new StringBuilder();
                for (var entry : plan.missingItems()) {
                    missing.append(entry.getKey().getId()).append(" x").append(entry.getLongValue())
                            .append("; ");
                }
                plan = null;
                future = null;
                throw new GameTestAssertException(
                        "crafting plan for " + what + " is incomplete, missing " + missing);
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

        long plannedBytes() {
            if (plan == null) {
                throw new IllegalStateException("crafting plan is not ready");
            }
            return plan.bytes();
        }

        ICraftingPlan plan() {
            if (plan == null) {
                throw new IllegalStateException("crafting plan is not ready");
            }
            return plan;
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
