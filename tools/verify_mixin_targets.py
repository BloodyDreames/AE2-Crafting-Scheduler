#!/usr/bin/env python3
"""Verify every mixin target against the *production* AE2 jar of each target.

Development builds resolve mixin targets by their development names, so they cannot tell you
whether a mixin will still apply in a released jar. This script checks each `@Inject`, `@Shadow`,
`@Accessor` and `@Invoker` target of this mod against the AE2 jar that ships to players, applying
the generated refmap where one exists.

Usage:
    python tools/verify_mixin_targets.py <target> <ae2-production.jar> [<our-production.jar>]

    target: neoforge | forge | fabric
"""

import json
import re
import subprocess
import sys
import zipfile
from pathlib import Path

# (owner class, member name, kind) for every mixin target in this mod.
# 'm' = method, 'f' = field.
COMMON_TARGETS = [
    ("appeng.menu.me.crafting.CraftConfirmMenu", "result", "f"),
    ("appeng.menu.me.crafting.CraftConfirmMenu", "selectedCpu", "f"),
    ("appeng.menu.me.crafting.CraftConfirmMenu", "cpuName", "f"),
    ("appeng.menu.me.crafting.CraftConfirmMenu", "getGrid", "m"),
    ("appeng.menu.me.crafting.CraftConfirmMenu", "cpuMatches", "m"),
    # Overrides AbstractContainerMenu#broadcastChanges, so this one is remapped in production.
    ("appeng.menu.me.crafting.CraftConfirmMenu", "broadcastChanges", "m"),
    ("appeng.blockentity.crafting.CraftingBlockEntity", "getCluster", "m"),
    ("appeng.blockentity.crafting.CraftingBlockEntity", "breakCluster", "m"),
    ("appeng.me.cluster.implementations.CraftingCPUCluster", "isBusy", "m"),
    ("appeng.me.cluster.implementations.CraftingCPUCluster", "craftingLogic", "f"),
    ("appeng.me.service.CraftingService", "submitJob", "m"),
    ("appeng.me.service.CraftingService", "grid", "f"),
    ("appeng.crafting.execution.CraftingCpuLogic", "cluster", "f"),
    ("appeng.crafting.execution.CraftingCpuLogic", "job", "f"),
    ("appeng.crafting.execution.CraftingCpuLogic", "inventory", "f"),
    ("appeng.crafting.execution.CraftingCpuLogic", "postChange", "m"),
    ("appeng.crafting.execution.CraftingCpuLogic", "insert", "m"),
    ("appeng.crafting.execution.CraftingCpuLogic", "trySubmitJob", "m"),
    ("appeng.crafting.execution.CraftingCpuLogic", "tickCraftingLogic", "m"),
    ("appeng.crafting.execution.CraftingCpuLogic", "getAllWaitingFor", "m"),
    ("appeng.crafting.execution.CraftingCpuLogic", "getWaitingFor", "m"),
    ("appeng.crafting.execution.CraftingCpuLogic", "writeToNBT", "m"),
    ("appeng.crafting.execution.CraftingCpuLogic", "readFromNBT", "m"),
    ("appeng.crafting.execution.CraftingCpuLogic", "cancel", "m"),
    ("appeng.crafting.execution.ElapsedTimeTracker", "decrementItems", "m"),
    ("appeng.crafting.execution.ExecutingCraftingJob", "link", "f"),
    ("appeng.crafting.execution.ExecutingCraftingJob", "waitingFor", "f"),
    ("appeng.crafting.execution.ExecutingCraftingJob", "timeTracker", "f"),
    ("appeng.crafting.execution.ExecutingCraftingJob", "finalOutput", "f"),
    ("appeng.crafting.execution.ExecutingCraftingJob", "remainingAmount", "f"),
    ("appeng.crafting.execution.ExecutingCraftingJob", "writeToNBT", "m"),
]

# Only AE2 19.x has a per-job suspend flag.
NEOFORGE_ONLY_TARGETS = [
    ("appeng.crafting.execution.ExecutingCraftingJob", "suspended", "f"),
]


def load_refmap(our_jar):
    """Returns {simple mixin name: {member: production name}} from our generated refmap."""
    if our_jar is None:
        return {}
    with zipfile.ZipFile(our_jar) as zf:
        names = [n for n in zf.namelist() if n.endswith("refmap.json")]
        if not names:
            return {}
        data = json.loads(zf.read(names[0]))
    remapped = {}
    for members in data.get("mappings", {}).values():
        for member, target in members.items():
            # e.g. "Lappeng/menu/.../CraftConfirmMenu;m_38946_()V" -> m_38946_
            match = re.search(r";([^(]+)\(", target) or re.match(r"([^(]+)\(", target)
            if match:
                remapped[member] = match.group(1)
    return remapped


def members_of(jar, owner):
    out = subprocess.run(
        ["javap", "-p", "-cp", str(jar), owner],
        capture_output=True, text=True)
    if out.returncode != 0:
        return None
    found = set()
    for line in out.stdout.splitlines():
        line = line.strip().rstrip(";")
        match = re.search(r"([A-Za-z_$][\w$]*)\s*\(", line)
        if match:
            found.add(match.group(1))
            continue
        match = re.search(r"([A-Za-z_$][\w$]*)$", line)
        if match:
            found.add(match.group(1))
    return found


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    target, ae2_jar = sys.argv[1], Path(sys.argv[2])
    our_jar = Path(sys.argv[3]) if len(sys.argv) > 3 else None

    checks = list(COMMON_TARGETS)
    if target == "neoforge":
        checks += NEOFORGE_ONLY_TARGETS

    refmap = load_refmap(our_jar)
    if refmap:
        print(f"refmap entries applied: {refmap}")
    else:
        print("no refmap (production names are the development names)")

    cache, failures = {}, []
    for owner, member, _kind in checks:
        if owner not in cache:
            cache[owner] = members_of(ae2_jar, owner)
        present = cache[owner]
        if present is None:
            failures.append(f"{owner}: CLASS NOT FOUND in {ae2_jar.name}")
            continue
        expected = refmap.get(member, member)
        if expected not in present:
            failures.append(f"{owner}#{member} -> expected '{expected}', not present")

    print(f"\n{target}: checked {len(checks)} mixin targets against {ae2_jar.name}")
    if failures:
        for line in failures:
            print("  FAIL " + line)
        return 1
    print("  all mixin targets resolve in the production jar")
    return 0


if __name__ == "__main__":
    sys.exit(main())
