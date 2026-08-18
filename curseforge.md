# SUMMARY (short field, shown under the project title)

Pause a running AE2 crafting job, push an urgent craft through the freed CPU, then resume the original exactly where it left off.


---


# DESCRIPTION (project page body)

## The 3 AM problem

You queue a build order across every Crafting CPU you own. Forty minutes of work, all lanes full.

Two minutes later you need one bucket of a fluid. Or a single processor. Or sixteen glass panes for the thing you are standing in front of right now.

There is nowhere to craft it. Every CPU is busy. So you either sit and wait, or you cancel a job and throw away everything it had already done.

## What this mod adds

One block: the **Crafting Scheduler**.

It does not craft anything. It does not make anything faster. It does not change what your CPUs are capable of. All it does is decide **when** a CPU works on which of the jobs you have given it.

When a craft cannot start because everything is busy, the Scheduler picks one of the CPUs you allowed it to manage, **pauses** the job running there, hands the free CPU to your urgent craft, and — as soon as that finishes — puts the original job back and carries on.

The paused job keeps everything. Its progress. Its half-finished intermediate items. Its place in the crafting tree. It does not restart from zero and it does not drop anything on the floor. It resumes from the exact step it stopped on, and it even collects results from machines that were still chewing on something when the pause happened.

## Getting started

Craft the Scheduler:

|   |   |   |
|---|---|---|
| Quartz Glass | Engineering Processor | Quartz Glass |
| Calculation Processor | Crafting Unit | Calculation Processor |
| Quartz Glass | Fluix Crystal | Quartz Glass |

Place it anywhere on your ME network and right-click it.

You get a board showing every Crafting CPU on the network, what each one is doing, and how far along it is. Pick a CPU, press **Manage CPU**, and it joins the Scheduler's list.

That is the whole setup. Now craft as you normally would — when something urgent has nowhere to go, the Scheduler makes room.

**The Scheduler will never touch a CPU you have not handed it.** Everything else on your network keeps behaving exactly as it did before you placed the block.

## It will not eat your stuff

This mod moves half-finished crafting jobs around, so the obvious question is what happens when something goes wrong. The answer is always the same: the job comes back.

- **Break the Scheduler** — every job it was holding is resumed first, before the block ever comes out of the ground.
- **Cut the power, flip the redstone off, split the network** — same thing. Jobs resume, nothing is abandoned.
- **Unload the chunk, restart the server, rebuild the multiblock** — paused jobs live on the CPU itself, not in the Scheduler. They survive, and the Scheduler recognises its own work when things come back.
- **Remove the Scheduler with a pickaxe mid-pause** — the CPU notices it has been orphaned and resumes on its own after about a minute.
- **The urgent craft gets stuck** — it cannot hold your main job hostage forever. After five minutes (configurable) it is cancelled and the original job resumes.
- **A Crafting CPU the mod cannot safely pause** — from another mod, for example — is labelled as such in the interface and is left alone. No crashes, no gambling.

## Keeping automation polite

Left alone, automated machines would happily interrupt a huge job every time they wanted four screws. So there are two limits.

A craft only counts as **urgent** if it is genuinely small — a couple of hundred pattern pushes at most. Anything bigger waits its turn like everyone else.

And a running job is only worth interrupting if it is genuinely big. A request coming from a machine will not pause a job that was nearly finished anyway. You pressing **Start** yourself is treated as the more important signal, and is allowed through where automation is not.

Both thresholds are yours to move, and automatic interruption can be switched off completely — the Scheduler will still hold and resume jobs, it just will not start anything on its own initiative.

## Controls

- **Manage / Release CPU** — add or remove a CPU from the Scheduler's list.
- **Cancel Express** — drop the urgent craft and bring the original job back immediately. Right-clicking a CPU in the list does the same thing.
- **Redstone** — ignore the signal, run only with it, or run only without it. Switching the Scheduler off this way resumes everything it was holding first.
- Hover anything in the interface for the full story on that CPU.

## Also configurable

How many jobs one Scheduler may hold at once, how long it waits before retrying a resume, how much power it draws, and a diagnostic log for when you want to watch every decision it makes.

## Languages

English · Русский · 简体中文

Translations are welcome.

## Requires

Applied Energistics 2.
