# AE2: Crafting Scheduler — architecture

Target: **Minecraft 1.21.1 / NeoForge 21.1.248 / Applied Energistics 2 `19.2.17` (neoforge)**.

This document is the result of reading AE2's crafting execution subsystem before writing any code. It
records *where the state of a running crafting job actually lives*, which parts of it can be moved,
and what the minimum viable intervention into AE2 is to implement a real
`pause → run something else → resume` on a single Crafting CPU.

Everything below refers to real AE2 classes. No invented API.

---

## 1. Where AE2 keeps a running job

AE2's autocrafting is split across three layers.

### 1.1 The CPU multiblock — `appeng.me.cluster.implementations.CraftingCPUCluster`

A runtime object rebuilt by `CraftingCPUCalculator` whenever the multiblock changes. It owns:

| Field | Meaning |
| --- | --- |
| `blockEntities` | the crafting unit block entities of the multiblock |
| `storage` | total bytes (static sum of unit blocks) |
| `accelerator` | co-processor count |
| `myName` | display name, derived from custom-named units |
| `configManager` | `CPU_SELECTION_MODE` |
| **`craftingLogic`** | `public final CraftingCpuLogic` — *the actual job* |

Notably `getAvailableStorage()` returns the **static** capacity. AE2 checks `availableStorage < plan.bytes()`
only once, at submit time; there is no live byte accounting. This matters later (§6.4).

The cluster is *not* the persistence unit. Persistence lives on the core `CraftingBlockEntity`:

```
CraftingBlockEntity.saveAdditional  -> if core: cluster.writeToNBT -> craftingLogic.writeToNBT
CraftingBlockEntity.loadTag         -> if core: cluster.readFromNBT, or stash into previousState
CraftingCPUCluster.done()           -> replays previousState once the cluster is formed
```

`cluster.markDirty()` → `getCore().saveChanges()`. So **anything stored inside `CraftingCpuLogic` is
automatically saved with the world, survives `/stop`, crash, and chunk unload**, and is replayed on
multiblock (re)formation. This is the single most important fact for this addon.

### 1.2 The execution engine — `appeng.crafting.execution.CraftingCpuLogic`

```java
private ExecutingCraftingJob job = null;               // the job, or null when idle
private final ListCraftingInventory inventory;         // intermediate items held by the CPU
private final int[] usedOps = new int[3];              // op budget over 3 ticks
private boolean cantStoreItems;
```

Relevant behaviour:

* `trySubmitJob` refuses when `job != null` → `CraftingSubmitResult.CPU_BUSY`. **`job != null` is the
  definition of "CPU busy"** (`CraftingCPUCluster.isBusy()` → `craftingLogic.hasJob()`).
* `tickCraftingLogic` (called every tick from `CraftingService.onServerEndTick`):
  * if `job == null` → `storeItems()` (dump `inventory` into the network) and return;
  * if `job.link.isCanceled()` → `cancel()`;
  * **if `job.suspended` → return without scheduling any work** (AE2 already has this!);
  * otherwise push up to `coProcessors + 1` patterns per tick via `executeCrafting`.
* `executeCrafting` extracts pattern inputs out of `inventory`, calls `provider.pushPattern(...)` on a
  Pattern Provider, and on success **adds the pattern's outputs to `job.waitingFor`**.
* `insert(what, amount, type)` is the entry point for finished results (§1.4). It only accepts items
  present in `job.waitingFor`. **It returns 0 immediately when `job == null`.**
* `writeToNBT` / `readFromNBT` are `public` and round-trip `{ "inventory": ListTag, "job": CompoundTag }`.

### 1.3 The job itself — `appeng.crafting.execution.ExecutingCraftingJob`

Package-private members, fully serializable:

| Field | Purpose | In NBT |
| --- | --- | --- |
| `link` | `CraftingLink` binding the job to the CPU | `link` |
| `waitingFor` | `ListCraftingInventory` of items **expected to arrive** (emitted items + outputs of already-pushed patterns) | `waitingFor` |
| `tasks` | `Map<IPatternDetails, TaskProgress>` — remaining pattern pushes | `tasks` |
| `timeTracker` | `ElapsedTimeTracker` (started/completed work per key type) | `timeTracker` |
| `finalOutput` | `GenericStack` requested output | `finalOutput` |
| `remainingAmount` | how much of `finalOutput` is still owed | `remainingAmount` |
| `playerId` | `IPlayerRegistry` id for status packets | `playerId` |
| `suspended` | already-existing "do not schedule new work" flag | `suspended` |

**The crafting plan is not retained.** Once submitted, `ICraftingPlan` is decomposed into
`tasks` (`patternTimes`), `waitingFor` (`emittedItems`) and the initial extraction (`usedItems`), and then
discarded. There is therefore nothing to "recalculate" on resume — `tasks` *is* the remaining plan.

`ExecutingCraftingJob`'s NBT constructor re-registers its link:
`((CraftingService) grid.getCraftingService()).addLink(link)`. That is exactly how a job survives a
server restart today.

### 1.4 The network layer — `appeng.me.service.CraftingService`

* Holds `Set<CraftingCPUCluster> craftingCPUClusters`, rebuilt on `GridCraftingCpuChange`.
* `onServerEndTick` ticks every CPU's logic and recomputes `currentlyCrafting` from
  `craftingLogic.getAllWaitingFor(...)`.
* `submitJob(plan, requester, target, prioritizePower, src)` is the **single funnel** every job goes
  through — the terminal (`CraftConfirmMenu:266`), machines (`MultiCraftingTracker:90`) and the test
  world all call it. `findSuitableCraftingCPU` skips CPUs where `isBusy()`.
* `insertIntoCpus` iterates all CPUs calling `craftingLogic.insert`. It is reached from
  `CraftingServiceStorage`, a global storage provider **mounted at priority `Integer.MAX_VALUE`**. This
  is how a furnace output pushed into the ME network gets intercepted and routed back into the CPU that
  is waiting for it, instead of landing in a disk.

### 1.5 Links — `CraftingLink` / `CraftingLinkNexus`

Two link objects per job when a machine requested it (one held by the CPU, one by the requester), tied
together by a `CraftingLinkNexus` keyed on a shared `craftId` UUID. Player-initiated jobs get a single
**standalone** link, and `CraftingService.addLink` returns early for standalone links — *there is no
nexus at all*.

`CraftingLinkNexus.isDead()` runs every tick and cancels the job if either side goes missing for 60
ticks. Its CPU-side check is `craftingService.hasCpu(this.cpu.getCpu())` — i.e. it only asks whether the
**cluster** is still in the grid, never whether that cluster currently has a job. This is what makes
detaching a job from a CPU survivable.

---

## 2. Data ownership summary

| Data | Owner | Serializable | Notes |
| --- | --- | --- | --- |
| Remaining pattern pushes (`tasks`) | CPU (`ExecutingCraftingJob`) | yes | the live remainder of the plan |
| Intermediate items | CPU (`CraftingCpuLogic.inventory`) | yes | real items, removed from the network |
| Expected results (`waitingFor`) | CPU (`ExecutingCraftingJob`) | yes | includes in-flight processing outputs |
| Progress / elapsed time | CPU (`ElapsedTimeTracker`) | yes | |
| Final output + remaining amount | CPU | yes | |
| CPU-side crafting link | CPU | yes | re-registered from NBT on load |
| Requester-side link + nexus | network (`CraftingService.craftingLinks`) | via the requester | dies after 60 ticks if either side disappears |
| Pushed-but-unfinished operations | **outside AE2** — in the Pattern Provider / machine | no | the only truly non-serializable state |
| CPU list, providers, watchers | network | no | rebuilt on grid change |
| Crafting plan | nowhere after submit | n/a | discarded |

**Conclusion:** with the single exception of work already pushed into external machines, *the whole
state of a running job is CPU-local and already round-trips through NBT*. AE2 does this on every server
restart. Pausing is therefore not a serialization problem — it is a *slot ownership* problem.

---

## 3. Why `cancel + resubmit` is not an option

`CraftingCpuLogic.cancel()` → `finishJob(false)` → `link.cancel()`, `waitingFor.clear()`, `job = null`,
`storeItems()`. That:

* breaks the crafting link (requesting machines are told the job failed),
* **drops `waitingFor` on the floor** — items already in a furnace come back later, `insert` returns 0,
  they land in a disk, and the plan that expected them is gone,
* returns intermediates to the network, so a resubmitted plan has to be recomputed from scratch against
  a changed inventory, producing a different plan and different byte cost.

It also loses `tasks`, which is the only record of remaining work.

---

## 4. The one thing AE2 cannot do: free the execution slot

Everything needed for a pause already exists except one operation:

> Move `CraftingCpuLogic.job` out of the "active" slot **without cancelling it**, so that
> `hasJob()` becomes `false` and the CPU accepts a new job, while the old job's state stays alive and
> keeps accepting in-flight results.

AE2 has no such method, and `job` is private. This is the whole reason the addon needs Mixin.

### 4.1 Why `suspended` alone is not enough

AE2 19.2 already has `job.suspended` / `CraftingCpuLogic.setJobSuspended(boolean)`, and its semantics
are exactly right for the *scheduling* half of a pause: no new patterns are pushed, but `insert()` keeps
accepting in-flight results. It does **not** free the slot — `job != null`, so `trySubmitJob` still
returns `CPU_BUSY`. The addon reuses `suspended` where it helps, but the slot problem remains.

### 4.2 Why not "drain everything first, then park"

An obvious alternative is: set `suspended = true`, wait until `waitingFor` is empty (no in-flight work),
and only then detach. Rejected, because:

* `waitingFor` is seeded at job start with `plan.emittedItems()`, which are satisfied by *level
  emitters* driving external processes. Those can take arbitrarily long or never complete, so
  `waitingFor` is not guaranteed to reach empty.
* Draining makes the pause asynchronous. `CraftingService.submitJob` must return an
  `ICraftingSubmitResult` (with a real `ICraftingLink` for machine requesters) **synchronously**. A
  deferred express job cannot produce a link, which breaks `ICraftingRequester` bookkeeping for the
  "request started by a machine" case.
* Waiting several seconds for a furnace defeats the point of an express craft.

So the addon parks **immediately** and keeps the parked job able to receive results (§5.2). Draining is
therefore not a precondition for anything; `DRAINING_IN_FLIGHT_WORK` exists as an observable state (the
paused job still has results outstanding) and `pauseProcessingTimeoutTicks` only flags a pause whose
in-flight results never arrive, so a player can go find the stalled machine. Neither ever cancels or
discards anything.

---

## 5. Design: "parking" a job

### 5.1 The park slot

A second, inactive job slot is added to `CraftingCpuLogic` by Mixin:

```
job              (unchanged)  -- the actively scheduled job; null == CPU free
inventory        (unchanged)  -- intermediates of the active job

acs$parkedJob         (new)   -- ExecutingCraftingJob, paused, never scheduled
acs$parkedInventory   (new)   -- its intermediate items, kept separate
acs$parkOwner         (new)   -- UUID of the Scheduler that owns this park (the lock)
acs$parkedComplexity  (new)   -- estimated total operations of the parked job
```

**Park** (synchronous, one server tick):

1. `acs$parkedJob = job; job = null`
2. move every entry of `inventory` into a fresh `acs$parkedInventory`, then `inventory.clear()`
3. `acs$parkOwner = schedulerId`
4. `cluster.markDirty()`

No link is cancelled, no item is created or destroyed, nothing is written to the network. `hasJob()` is
now `false`, so `trySubmitJob` accepts the express job in the *same tick*.

**Unpark** is the exact inverse, and requires `job == null`.

The intermediates staying in `acs$parkedInventory` rather than being flushed to the network is
deliberate: flushing would let a player or another job consume them, and the paused job would then
resume into a state its `tasks` cannot satisfy.

### 5.2 In-flight processing while parked

The difficult case is a result that finishes after its job has already been parked:

```
job pushes  Iron Dust -> Furnace     (waitingFor += Iron Ingot)
job is parked
furnace finishes                     (Iron Ingot inserted into the ME network)
```

The insertion reaches `CraftingServiceStorage` → `insertIntoCpus` → `craftingLogic.insert` on every CPU.
The addon injects at the `RETURN` of `insert` and, for whatever the active job did not consume, runs the
**same algorithm AE2 uses**, against the parked job:

* consume from `acs$parkedJob.waitingFor` (never more than is expected — this is what prevents dupes);
* if the key matches the parked job's `finalOutput`, forward it to `parkedJob.link.insert(...)` and
  decrement `remainingAmount`;
* otherwise store it in `acs$parkedInventory`;
* decrement the parked job's `ElapsedTimeTracker` so progress stays honest;
* `SIMULATE` and `MODULATE` follow identical branches, as `MEStorage` requires.

So a parked CPU **does not schedule new work but still settles work that was already dispatched**.
Nothing is left orphaned in a machine, and nothing is double-counted, because `waitingFor` is the single
accounting ledger and it moves with the job.

Two consequences fall out for free:

* If the parked job's `remainingAmount` reaches 0 while paused, it is *finished* in place
  (`link.markDone()`), its leftovers are returned to the network, and there is nothing to resume.
* If the parked job's link is cancelled from the outside (requester removed, nexus timeout), the tick
  hook notices `parkedJob.link.isCanceled()` and finishes it as cancelled, returning
  `acs$parkedInventory` to the network. No stuck job, no lost items.

### 5.3 Persistence

`CraftingCpuLogic.writeToNBT` is extended with an `acs_park` compound holding the parked job in exactly
the same shape AE2 writes an active job (`{ inventory, job }`), plus the owner UUID and complexity.

Because this rides inside the CPU's own NBT, it inherits AE2's persistence for free:

* `/stop`, crash, restart — the core `CraftingBlockEntity` saves it;
* chunk unload — `disconnect()` destroys the cluster but the block entity keeps the tag;
* multiblock re-formation — `CraftingCPUCluster.done()` replays `previousState`.

On load, the parked job has to be turned back into a live `ExecutingCraftingJob`. AE2's NBT constructor
is package-private, so instead of duplicating it the addon *reuses* it: the parked tag is fed through
`CraftingCpuLogic.readFromNBT` (public) into the active slot and immediately moved to the park slot; if
an express job was also saved, it is stashed and restored around that. This keeps the deserialization
logic — including link re-registration with `CraftingService` — entirely AE2's.

**The park state lives on the CPU, not on the Scheduler.** That is a deliberate safety choice: breaking
the Scheduler, or losing it in a network split, can never orphan a paused job.

### 5.4 Destroying a parked CPU

`CraftingBlockEntity.breakCluster()` cancels the job and drops `craftingLogic.getInventory()` as items.
It knows nothing about the park slot. The addon injects at its `HEAD` and unparks first (cancelling the
express job if one is running), so AE2's own cancel-and-drop path handles both jobs. No new drop logic,
no lost items.

---

## 6. Design: the Crafting Scheduler device

### 6.1 Binding to CPUs — network-wide selection, not proximity

The Scheduler is an ME device with a grid node and an idle/active power draw. It enumerates
`grid.getCraftingService().getCpus()` and the player ticks the CPUs it may manage.

Physical attachment to a CPU multiblock was considered and rejected:

* `CraftingCPUCluster` is a runtime object with no stable identity; it is destroyed and rebuilt whenever
  any unit block changes. A physical binding would have to be re-derived on every multiblock update
  anyway, so it buys nothing over a logical one.
* AE2 already treats CPUs as network-level resources (`ICraftingService.getCpus()`, CPU selection in the
  terminal). A network-level controller matches that model.
* A Scheduler bolted to one CPU could not implement victim selection across several CPUs, which is a
  requirement.

CPUs are identified by the **`BlockPos` of the cluster's core block entity** (`getBoundsMin()`), which is
stable across cluster rebuilds as long as the multiblock keeps a block there, and is trivially
serializable. When a selected CPU disappears the selection is kept (greyed out as `Unavailable`) rather
than deleted, so rebuilding the multiblock restores the setting.

### 6.2 Ownership and locking

Two Schedulers on one grid must never both drive the same CPU. The lock is `acs$parkOwner` on the CPU
itself:

* a Scheduler may only park a CPU whose `acs$parkOwner` is null;
* only the owner may unpark it or submit an express job onto it;
* a CPU that is parked by *another* Scheduler is displayed as `Managed elsewhere` and skipped by victim
  selection.

Because parking happens on the server thread inside one tick, this is a plain atomic claim — no extra
synchronisation is needed. A Scheduler that is destroyed or loses power releases its claim by unparking
(§6.6).

### 6.3 Interception of new crafting requests

The addon injects at the `RETURN` of `CraftingService.submitJob`. If — and only if — AE2's own attempt
failed with `NO_CPU_FOUND`, `NO_SUITABLE_CPU_FOUND` or `CPU_BUSY`, the scheduler pipeline runs:

```
plan complexity <= maxExpressComplexity ?
  -> pick victim CPU (§6.5)
  -> park victim
  -> cpuCluster.submitJob(grid, plan, src, requester)     // real result, real link
       success -> record express job, return the new result
       failure -> unpark immediately, return AE2's original result
```

Because this happens inside the original call, the terminal and `ICraftingRequester` machines both get a
normal synchronous answer with a proper link. Nothing about AE2's own scheduling changes when no
Scheduler is present or when preemption is disabled.

### 6.4 Complexity metric

`ICraftingPlan` exposes `patternTimes()` — a map of pattern → how many times it must be pushed. The sum
of its values is the **estimated number of crafting operations**, which is the natural unit here: it
counts sub-crafts and processing operations alike and is unaffected by a single item having a huge tree.
`bytes()` and `patternTimes().size()` (distinct crafting steps) are also recorded and shown in the GUI.

A *running* job no longer has its plan, so the Scheduler records the submitted plan's complexity next to
the job (persisted with the park state) and estimates remaining work as
`complexity × (1 − ElapsedTimeTracker.getProgress())`.

Note on bytes: while an express job runs, the CPU physically holds both jobs' intermediates. AE2 does not
enforce byte limits at runtime (§1.1), so this cannot fail, but the Scheduler still refuses to park a CPU
whose capacity is not comfortably larger than the express plan's `bytes()`.

Maximum nesting depth is fixed at **1** for this version: one parked job per CPU, and a CPU already
running an express job is never chosen as a victim again. That rule is enforced structurally (there is
exactly one park slot) rather than by a counter.

### 6.5 Victim selection

Candidate CPUs must be: managed by this Scheduler, active, busy, not already parked, not currently
running an express job, large enough for the express plan, and running a job whose estimated complexity
is at least `minimumJobComplexityForPreemption`.

Candidates are ordered by:

1. most estimated remaining operations (pause the job that has the longest to go);
2. then fewest co-processors (leave the fast CPU free for whatever comes next);
3. then smallest storage that still fits (avoid wasting the big CPU);
4. ties broken by core `BlockPos` so the choice is deterministic and reproducible.

### 6.6 State machine

Per managed CPU, not a set of booleans:

```
IDLE ──────────► RUNNING ──────────► PAUSE_REQUESTED
                    ▲                       │
                    │                       ▼
                    │             RUNNING_EXPRESS_JOB
                    │                       │
                    │                       ▼
                    │              EXPRESS_COMPLETED
                    │                       │
                    │            ┌──────────┴───────────┐
                    │            ▼                      ▼
                    │   DRAINING_IN_FLIGHT_WORK       PAUSED
                    │            └──────────┬───────────┘
                    │                       ▼
                    └── RESUMED ◄────── RESTORING ◄──┐
                                            │        │
                                            └──► ERROR (retries)
```

Two states outside the happy path: `UNAVAILABLE` (the CPU is not reachable right now — network split,
unloaded chunk, dismantled multiblock) and `UNSUPPORTED` (a CPU implementation this addon will not
touch). Neither ever decides anything; they just stop the Scheduler from acting.

`ERROR` is entered when a resume is impossible (CPU gone, offline, or unexpectedly busy). It is *not*
terminal and never discards state: the park stays on the CPU and the Scheduler retries every
`resumeRetryTicks` until it succeeds. `RESUMED` is a one-tick display state that falls back to `RUNNING`.

### 6.7 Failure and edge-case handling

| Event | Behaviour |
| --- | --- |
| Express job never completes (missing ingredient) | after `expressJobTimeoutTicks` the express job is cancelled and the original is resumed — a stuck express craft can never hold the main job hostage |
| Express job cancelled by the player | detected on the next tick, resume immediately |
| Original (parked) job cancelled | park is finished as cancelled, leftovers returned to the network, CPU released |
| Scheduler broken | `setRemoved` unparks every CPU it owns first; if an express job is running it is cancelled first |
| Scheduler loses power / redstone-disabled | claims are released the same way; the addon never leaves a CPU parked without an owner (a watchdog unparks any park whose owner has not checked in for `orphanedParkTimeoutTicks`) |
| CPU multiblock broken | `breakCluster` hook (§5.4) |
| CPU shrinks / loses storage | resume is still attempted; AE2 does not re-check bytes on an existing job, so the job simply continues |
| CPU offline / controller unpowered | `ERROR`, retry when active again; state untouched |
| Network split | the CPU keeps its park in its own NBT; the Scheduler on the other side sees the CPU disappear, drops it from its list, and the watchdog on the CPU side restores the job |
| CPU renamed | irrelevant — CPUs are keyed by core `BlockPos`, not name |
| Server restart while paused | park is in the CPU's NBT; the Scheduler re-adopts it by owner UUID on load |
| Server restart during an express job | both jobs are in the CPU's NBT; the express job resumes as a normal AE2 job and the Scheduler re-adopts the park and resumes it afterwards |
| Player logs out | irrelevant; `playerId` only controls status packets |
| Two Schedulers | §6.2 |

### 6.8 Compatibility with other addons

The Scheduler works through `ICraftingService.getCpus()` and only ever touches CPUs whose concrete type
is `CraftingCPUCluster` (which is what AE2 addons extending autocrafting reuse). Any `ICraftingCPU`
implementation that is *not* a `CraftingCPUCluster`, or whose logic class no longer carries the park slot
(i.e. was not patched by our Mixin), is listed in the GUI as **`Unsupported CPU`**, excluded from victim
selection, and never crashes the game. The intercept in `CraftingService.submitJob` is a no-op whenever
no eligible Scheduler exists, so ExtendedAE / AdvancedAE / ExtendedAE Plus behave exactly as they do
without this mod unless a player explicitly opts a CPU in.

---

## 7. Total intervention into AE2

Three mixins, all additive, none replacing AE2 behaviour:

| Mixin | Target | What it does |
| --- | --- | --- |
| `CraftingCpuLogicMixin` | `appeng.crafting.execution.CraftingCpuLogic` | adds the park slot, park/unpark, in-flight routing at `insert` RETURN, cancellation watch in `tickCraftingLogic` HEAD, NBT round-trip in `write/readFromNBT` TAIL |
| `CraftingServiceMixin` | `appeng.me.service.CraftingService` | at `submitJob` RETURN, offers a failed submit to the scheduler pipeline |
| `CraftingBlockEntityMixin` | `appeng.blockentity.crafting.CraftingBlockEntity` | at `breakCluster` HEAD, unparks so AE2's own drop logic covers parked items |

Plus three `@Accessor`/`@Invoker` interfaces (`ExecutingCraftingJob`, `ElapsedTimeTracker`) that expose
existing package-private members without changing them.

No AE2 method is overwritten, no `@Redirect`, no access transformer (NeoForge ATs cannot target mod
classes anyway). If the Mixin fails to apply, the addon disables itself and reports `Unsupported CPU`
rather than crashing.

---

## 8. Invariants

The implementation is built to hold these, in this priority order:

1. **No duplication.** Items only ever move between `inventory`, `acs$parkedInventory`, the network and
   pattern providers. `waitingFor` is the single ledger for expected results and is never copied, only
   moved with its job.
2. **No loss.** Every path that destroys a park (CPU broken, job cancelled, scheduler removed) routes
   through AE2's own cancel/drop code with the job restored first.
3. **No stuck jobs.** Every wait is bounded: express job timeout, orphaned-park watchdog, resume retry.
   `ERROR` preserves state and retries rather than dropping it.
4. Faithful pause/resume — no recalculation, no re-pushing of already dispatched patterns
   (`tasks` is decremented at push time, before the park).
5. GUI and conveniences.

---

## 9. Acceptance criterion

> Start `100000 Glass`, request `4 Calculation Processors` while every managed CPU is busy, and the
> Scheduler parks the glass job at its exact progress, runs the processors on the freed CPU, and resumes
> the glass job from the same `tasks`, `waitingFor`, `inventory` and `remainingAmount` — with no cancel,
> no recalculation, and no change to the total item count in the world.
