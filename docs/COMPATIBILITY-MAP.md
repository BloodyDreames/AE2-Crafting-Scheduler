# Compatibility map (Phase 1 audit)

Legend: **MC** = depends on a Minecraft API that changed between 1.20.1 and 1.21.1,
**Loader** = depends on NeoForge/Forge/Fabric APIs, **AE2** = depends on AE2 internals/API,
**Free** = loader- and version-independent.

| Source file | MC | Loader | AE2 | Notes |
|---|---|---|---|---|
| `AE2CraftingScheduler` | - | **yes** | `AECapabilities` | `@Mod`, `DeferredRegister`, capability registration |
| `SchedulerConfig` | - | **yes** | - | `ModConfigSpec` (NeoForge only) |
| `SchedulerLog` | - | - | - | Free |
| `block/SchedulerBlock` | **yes** | - | - | `MapCodec codec()`, `useItemOn`/`ItemInteractionResult`, `protected` overrides, `isPathfindable` arity |
| `client/CpuTableRenderer` | - | - | yes | `AbstractTableRenderer` byte-identical in both AE2 branches |
| `client/ModClient` | - | **yes** | `InitScreens` | `RegisterMenuScreensEvent` |
| `client/SchedulerScreen` | - | **yes** | yes | `PacketDistributor`; `addButton` returns `AE2Button` (1.21.1) vs `Button` (1.20.1) |
| `menu/CpuStatus` | **yes** | - | - | `StreamCodec` / `RegistryFriendlyByteBuf` are 1.21.1-only |
| `menu/SchedulerMenu` | **yes** | **yes** | `AEBaseMenu` | `RegistryFriendlyByteBuf`, `PacketDistributor` |
| `mixin/CraftConfirmMenuMixin` | - | - | yes | Targets identical in both AE2 branches (same line numbers) |
| `mixin/CraftingBlockEntityMixin` | - | - | yes | `breakCluster` identical |
| `mixin/CraftingCPUClusterMixin` | - | - | yes | `isBusy` identical |
| `mixin/CraftingServiceMixin` | - | - | yes | `submitJob` identical |
| `mixin/CraftingCpuLogicMixin` | **yes** | - | yes | `writeToNBT`/`readFromNBT` gained a `HolderLookup.Provider` param in 1.21.1 |
| `mixin/ElapsedTimeTrackerInvoker` | - | - | yes | `decrementItems` identical |
| `mixin/ExecutingCraftingJobAccessor` | **yes** | - | **yes** | `writeToNBT` arity + `suspended` field exists only in AE2 19.x |
| `net/ModNetwork` | - | **yes** | - | `RegisterPayloadHandlersEvent` |
| `net/Scheduler*Payload` | **yes** | **yes** | - | `CustomPacketPayload` is 1.21.1-only |
| `park/ParkableCpu` | - | - | yes | Free of MC APIs |
| `registry/Mod*` | - | **yes** | - | `DeferredRegister` |
| `scheduler/CpuKey` | - | - | yes | Free |
| `scheduler/ManagedCpuState` | - | - | - | Free |
| `scheduler/PlanComplexity` | - | - | yes | `ICraftingPlan` identical |
| `scheduler/PreemptionManager` | - | - | yes | Free of MC/loader APIs |
| `scheduler/SchedulerBlockEntity` | **yes** | - | yes | `saveAdditional`/`loadAdditional` signatures |
| `scheduler/SchedulerRedstoneMode` | - | - | - | Free |
| `scheduler/SchedulerVisualState` | - | - | - | Free |
| `testplots/SchedulerTestPlots` | - | - | yes | `@TestPlotClass` is 19.x-only; 15.x uses `TestPlots.addPlotClass` |

## AE2 API differences found (19.2.17 vs 15.4.10)

Verified by diffing the published sources jars of both branches.

1. **NBT registries parameter** — every AE2 serialization entry point gained a
   `HolderLookup.Provider` parameter in the 1.21.1 branch:
   `CraftingCpuLogic.write/readFromNBT`, `ExecutingCraftingJob.writeToNBT`,
   `ListCraftingInventory.write/readFromNBT`, `GenericStack.read/writeTag`,
   `AEKey.toTagGeneric`. This is a Minecraft change, not an AE2 redesign.
2. **`ExecutingCraftingJob.suspended`** — AE2 19.x has native job suspension
   (`CraftingCpuLogic.isJobSuspended`/`setJobSuspended`, `NBT_SUSPENDED`).
   AE2 15.x has no such concept at all.
3. **`AE2Button`** — added in 19.x; `WidgetContainer.addButton` returns plain
   `Button` in 15.x.
4. **`InitScreens.register`** — takes `RegisterMenuScreensEvent` in 19.x,
   no event parameter in 15.x (uses `MenuScreens.register` directly).
5. **Grid node host discovery** — 19.x resolves *only* through the NeoForge
   capability (`AECapabilities.IN_WORLD_GRID_NODE_HOST`), so registration is
   mandatory. 15.x checks `blockEntity instanceof IInWorldGridNodeHost` first
   on both Forge and Fabric, so no registration is needed at all.
6. **Test plots** — 19.x discovers external plot classes via `@TestPlotClass`;
   15.x requires an explicit `TestPlots.addPlotClass(Class)` call.
7. **`CraftingCpuLogic.getLastModifiedOnTick`** — present in 19.x and in the
   Forge 15.4.10 build, absent from the Fabric 15.4.10 build. Unused here.

Everything else the addon touches — `CraftingCpuLogic.insert/trySubmitJob/tickCraftingLogic/
getWaitingFor/getAllWaitingFor`, `CraftingCPUCluster.isBusy/breakCluster/getBoundsMin/craftingLogic`,
`CraftingService.submitJob`, `CraftConfirmMenu.cpuMatches/broadcastChanges/getGrid/result/selectedCpu/cpuName`,
`ElapsedTimeTracker.decrementItems`, `CraftingLink`, `CraftingSubmitResult`, `CraftingSubmitErrorCode`,
`ICraftingPlan`, `AbstractTableRenderer`, `StackWithBounds`, `SettingToggleButton`, `AEBaseMenu`,
`AEBaseScreen`, `Tooltips`, `ButtonToolTips`, `GuiText`, `AEConfig`, `GridHelper` — is
API-identical between the two AE2 branches.

## AE2 versions selected

| Target | AE2 artifact | Version |
|---|---|---|
| 1.21.1 NeoForge | `org.appliedenergistics:appliedenergistics2` | 19.2.17 (unchanged) |
| 1.20.1 Forge | `appeng:appliedenergistics2-forge` | 15.3.6 |
| 1.20.1 Fabric | `appeng:appliedenergistics2-fabric` | 15.3.6 |

### Why 15.3.6 and not the newest 15.4.10

AE2's **Forge** builds from 15.4.0 onwards are published without reobfuscation: their class
files carry Mojang member names instead of SRG ones, so they cannot run on a production Forge
server at all. Verified by bisection on a real Forge dedicated server (both 47.2.20 and 47.4.9),
*without this addon installed* -- AE2 15.4.0+ dies on its own
`ae2.mixins.json:spatial.MinecraftServerMixin` with `@Shadow field levels was not located`,
while 15.3.6 and 15.2.16 boot normally. The same is visible statically:

```
$ javap -p -c appeng.blockentity.AEBaseBlockEntity   # SRG members = correctly reobfuscated
AE2 15.3.6:   f_58857_ f_58858_ m_46805_
AE2 15.4.0:   (none)
AE2 15.4.10:  (none)
```

The Fabric builds are unaffected (all versions are correctly remapped to intermediary), but both
1.20.1 targets are pinned to the same AE2 version so the shared `mc1201` sources compile against
one API surface. Every AE2 class this addon touches is byte-identical between 15.3.6 and 15.4.10,
so nothing is lost by pinning:

```
CraftingCpuLogic, ExecutingCraftingJob, ElapsedTimeTracker, ListCraftingInventory,
CraftingCPUCluster, CraftingService, CraftConfirmMenu, CraftingBlockEntity, ICraftingPlan,
AbstractTableRenderer, InitScreens, Scrollbar, TestPlots  -- all IDENTICAL
```
