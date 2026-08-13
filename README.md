# AE2: Crafting Scheduler

A NeoForge addon for **Applied Energistics 2** that gives AE2 a real
`pause → run something else → resume` for autocrafting jobs.

* **Minecraft** 1.21.1
* **NeoForge** 21.1.248
* **Applied Energistics 2** 19.2.17 (neoforge)

---

## The problem

You start `100 000 Glass`. Every Crafting CPU is now busy. Ten minutes later you urgently need
`4 Calculation Processors` — and in stock AE2 that request just waits for a CPU to free up.

## What this adds

One block, the **Crafting Scheduler**. It is an ME device that manages Crafting CPUs you select. When
a request cannot be placed because everything is busy, it:

```
Crafting CPU
100 000 Glass
Progress: 46%
        ↓   small request arrives
100 000 Glass          PAUSED — 46%
4 Calculation Processors   RUNNING
        ↓
4 Calculation Processors   COMPLETED
        ↓
100 000 Glass          RESUMED — 46%
```

The big job is **not cancelled and not recalculated**. Its remaining pattern pushes, its intermediate
items, its expectation ledger, its crafting link and its progress all stay exactly as they were.

The Scheduler does not make anything faster. It does not add co-processors, does not reduce recipe
costs, and does not increase CPU storage. It only decides *when* a CPU works on which job.

---

## How the pause actually works

The full research write-up is in **[ARCHITECTURE.md](ARCHITECTURE.md)**. The short version:

AE2 already keeps everything a job needs inside the CPU, and already round-trips all of it through NBT
on every server restart. The single thing it cannot do is move a job out of the active slot *without
cancelling it* — `job != null` is AE2's definition of "CPU busy".

So this addon adds exactly that: a second, inactive **park slot** on the CPU.

| | active slot | park slot |
| --- | --- | --- |
| scheduled by AE2 | yes | never |
| accepts results dispatched before the pause | yes | **yes** |
| holds its own intermediate items | yes | yes, kept separate |
| saved with the world | yes | yes |

A parked job stops producing new work but keeps settling work already sent to machines. That is what
makes the awkward case safe:

```
job pushes  Iron Dust → Furnace      (now expecting 1 Iron Ingot)
job is parked
furnace finishes                     → the ingot is routed to the parked job, not into a disk
```

Nothing is orphaned, and nothing is double-counted, because `waitingFor` — AE2's ledger of expected
results — moves with the job and is the only thing allowed to authorise an insert.

### Intervention into AE2

Three additive mixins, no method replaced, no `@Redirect`:

| Mixin | Target | Purpose |
| --- | --- | --- |
| `CraftingCpuLogicMixin` | `CraftingCpuLogic` | the park slot, in-flight routing, NBT round-trip, watchdog |
| `CraftingServiceMixin` | `CraftingService` | offers a failed `submitJob` to the Schedulers on that grid |
| `CraftingBlockEntityMixin` | `CraftingBlockEntity` | restores a paused job before AE2 drops a broken CPU |

Plus two `@Accessor`/`@Invoker` interfaces that expose existing package-private members unchanged.

---

## Using it

1. Craft a **Crafting Scheduler** and attach it to your ME network (it needs a channel and a little
   power).
2. Right-click it. You get a list of every Crafting CPU on the network.
3. Tick the CPUs the Scheduler is allowed to pause. Nothing is ever touched without an explicit tick.
4. That is all. Requests that AE2 cannot place now get a chance to preempt.

In the GUI:

* **left-click** a CPU row — toggle whether it may be managed
* **right-click** a CPU row holding a paused job — cancel the express job and resume immediately
* **click the redstone line** — Ignore Redstone / Active With Signal / Active Without Signal
* a **comparator** on the Scheduler outputs how many jobs it is currently holding

CPUs that this addon cannot pause safely (a third-party `ICraftingCPU` implementation) are listed as
`UNSUPPORTED` and are never touched.

---

## Safety properties

In priority order, and in this order deliberately:

1. **No duplication.** Items only ever move between the CPU inventory, the park inventory, the network
   and pattern providers. A parked job can never accept more than its `waitingFor` ledger says it is
   owed.
2. **No item loss.** Every path that ends a park — CPU broken, job cancelled, Scheduler removed —
   restores the job first and then goes through AE2's own cancel-and-drop code.
3. **No stuck jobs.** Every wait is bounded:
   * an express job that cannot finish is cancelled after `expressJobTimeoutTicks`, and the original
     job resumes;
   * a park whose Scheduler stopped checking in (broken, unpowered, redstone-off, cut off by a network
     split) is resumed by the CPU itself after `orphanedParkTimeoutTicks`;
   * `ERROR` preserves state and retries every `resumeRetryTicks` instead of discarding anything.

### Situations that are handled explicitly

| Situation | Behaviour |
| --- | --- |
| Server `/stop`, restart, crash | park is in the CPU's own NBT; the Scheduler re-adopts it by owner id |
| Restart during an express job | both jobs are restored; the express finishes, then the original resumes |
| Chunk unload | nothing is released; the park is re-adopted on load |
| Scheduler broken | resumes everything it holds first |
| Scheduler unpowered / redstone-off | same |
| CPU multiblock broken | paused job is restored, then AE2 drops both jobs' items normally |
| CPU shrunk or renamed | irrelevant; CPUs are keyed by position, and AE2 does not re-check bytes mid-job |
| CPU offline | `ERROR`, retried; state untouched |
| ME network split | the CPU keeps its own park and resumes it via the watchdog |
| Express job cancelled | detected next tick, original resumes |
| Original job cancelled from outside | park is finished as cancelled, items go back to the network |
| Two Schedulers on one grid | the park owner id is an exclusive lock; only the owner may act |
| Request made by a machine, not a player | the pause is synchronous, so the machine still gets a real crafting link |

---

## Configuration

Server config (`serverconfig/ae2_crafting_scheduler-server.toml`):

| Key | Default | Meaning |
| --- | --- | --- |
| `scheduler.enableScheduler` | `true` | master switch; when off, AE2 is left completely untouched |
| `scheduler.allowAutomaticPreemption` | `true` | let failed requests trigger a pause automatically |
| `scheduler.maxPausedJobsPerScheduler` | `8` | how many jobs one Scheduler may hold at once |
| `preemption.maxExpressComplexity` | `128` | a job may only jump the queue below this many operations |
| `preemption.minimumJobComplexityForPreemption` | `1000` | a job is only paused above this many operations |
| `timeouts.pauseProcessingTimeoutTicks` | `1200` | flag a pause whose in-flight results never arrive (diagnostic only) |
| `timeouts.expressJobTimeoutTicks` | `6000` | cancel a stuck express job and resume the original |
| `timeouts.orphanedParkTimeoutTicks` | `1200` | CPU-side watchdog for a park with no live owner |
| `timeouts.resumeRetryTicks` | `20` | retry interval for a blocked resume |
| `misc.energyUsagePerTick` | `1.0` | AE/t drawn by an active Scheduler |
| `misc.debugLogging` | `false` | log every decision — turn on when hunting a dupe or a deadlock |

**Operations**, not item counts: the sum of `ICraftingPlan.patternTimes()`, i.e. how many pattern
pushes the whole crafting tree needs. One ME Controller is a single item with a huge tree; 100 000
Glass is a huge item count with a trivial one. Counting operations gets both right.

**Nesting depth is fixed at 1.** A CPU running an express job is never chosen as a victim again, so
`A paused → B running → B paused → C running` cannot happen. This is enforced structurally: there is
exactly one park slot per CPU.

### Debug log

With `debugLogging = true`:

```
[Scheduler] Express request detected: ae2:me_drive x1 (12 operations, 5 steps, 340 bytes)
[Scheduler] Selected CPU: Main CPU at BlockPos{x=12, y=64, z=-30}
[Scheduler] Pausing job: Glass x100000 (48231 estimated operations) on CPU ...
[Scheduler] Job safely paused, 3 operations still in flight
[Scheduler] Starting express job: ME Drive x1
[Scheduler] Accepted in-flight result for paused job: Iron Ingot x1
[Scheduler] Express job completed on CPU ...
[Scheduler] Resume successful on CPU ...
```

---

## Building

JDK 21 is required.

```sh
./gradlew build
```

AE2 and GuideME are resolved from Maven Central at the versions pinned in `gradle.properties`.

## Tests

```sh
./gradlew runGameTestServer
```

Game tests are written against AE2's own test-world framework (`@TestPlotClass`), so they build a real
ME network with a controller, Crafting CPUs, a pattern provider and molecular assemblers.

Current status: **all 72 required tests pass** — the four below plus AE2's own 68.

| Test | What it proves |
| --- | --- |
| `scheduler_pause_resume` | a big job is paused mid-flight, a small job runs on the freed CPU, the big job resumes and finishes — and the item cell is stocked with *exactly* the ingredients both jobs need, so the final counts prove nothing was duplicated or lost |
| `scheduler_park_survives_nbt` | a paused job survives the CPU's `writeToNBT`/`readFromNBT` round trip — the same code a server restart runs — and still resumes correctly |
| `scheduler_removal_resumes_job` | breaking the Scheduler while it holds a paused job resumes that job instead of stranding it |
| `scheduler_picks_one_cpu` | with several busy managed CPUs, exactly one is paused and every background job still completes in full |

The run also enables AE2's own `ae2` game-test namespace, so AE2's stock autocrafting tests act as a
regression check that the mixins did not change vanilla behaviour. They pass, which is the evidence
that the three mixins are additive.

Scenarios that a game test cannot express (a genuine server restart, a real chunk unload, a live
network split) are covered by design instead — the park lives in the CPU's NBT and the CPU-side
watchdog is the backstop. See the table under *Safety properties*.

The tests earn their keep: writing them surfaced two real defects that no amount of reading would have
found — the Scheduler's grid node was not created as an in-world node (so it never joined a network at
all), and the paused-job insert hook read AE2's `amount` parameter after AE2 had already reassigned it,
which would have under-reported results to a paused job that shared an ingredient with the express one.

## Compatibility

The Scheduler works through `ICraftingService.getCpus()` and only ever touches CPUs whose concrete type
is AE2's `CraftingCPUCluster`. ExtendedAE, AdvancedAE, ExtendedAE Plus and anything else that builds on
AE2's crafting CPU keep working unchanged; anything that does not is listed as `UNSUPPORTED` and
skipped. With no Scheduler on a network, or with `enableScheduler = false`, AE2 behaves exactly as it
does without this mod.

## License

MIT.
