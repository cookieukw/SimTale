#!/usr/bin/env python3
"""
Gives every SimTale NPC role a `ReturnHome` state so the NPCs can actually walk to the
leash point the mod sets for them.

THE ORIGINAL BUG
----------------
The SimTale roles declared a single state, `Idle`, whose body motion is `WanderInCircle`.
Nothing in them could navigate to a destination. NPCMovementHelper called
`setState(ref, "Moving", ...)`, which produced a stream of

    State 'Moving.null' in 'SimTale_Human_Female_162' does not exist
    and was set by an external call

while the leash still dragged the NPC toward its target and the role kept playing the idle
animation — the NPC appeared to slide around on ice.

WHY THE FIRST FIX BROKE SPAWNING
--------------------------------
The first version of this script appended a `ReturnHome` block and left the mod to enter
that state from Java. Every role then failed to load and `NPCPlugin.spawnNPC()` returned
null, so `/simtale spawn` threw "Spawn result is null".

Per the official modding docs
(https://hytalemodding.dev/en/docs/guides/npc-workings/npc-states):

    You can make as many States as you want but REMEMBER they all must be LINKED together
    in some way from the StartState state. Otherwise your Role fails to validate.
    (...) Hytale outright refuses you to add or update Roles if States aren't linked.

A state only ever entered from outside the JSON is a *stray* state, and validation rejects
the entire role. Vanilla never hits this because its ReturnHome is always reachable from an
in-role action: Template_Intelligent sets it directly, and Test_Soft_Leash reaches it via
Component_Instruction_Soft_Leash's `_ExportStates`.

WHY THE ROLE'S OWN WANDER IS DISABLED
-------------------------------------
The roles ship with `WanderInCircle` (radius 10) inside Idle. Left in place alongside the
Idle -> ReturnHome link below (which fires at 2 blocks from the leash point), it produced a
feedback loop that made NPCs pace back and forth over the same patch of ground:

    WanderInCircle drifts the NPC away from its leash point
      -> past 2 blocks the Leash sensor fires -> ReturnHome
      -> Seek walks it back to within 1 block -> Idle
      -> WanderInCircle drifts it away again ...

It is also redundant now: the mod has its own wandering (TaskType.WANDERING) which is
anchored on the NPC's bed and has a timeout. So the role's motion is swapped for `Nothing`
and the mod is left as the single owner of movement decisions.

The original motion is not thrown away — it is saved to IDLE_MOTION_BACKUP so that --revert
can put it back exactly as it was.

WHAT THIS SCRIPT DOES
---------------------
1. Inside `Idle`, adds a `Leash` sensor that switches to `ReturnHome` once the NPC is more
   than ENTER_RANGE blocks from its leash point. This is the link that makes the state
   reachable, and it is what makes the role validate.
2. Adds the `ReturnHome` state itself: a pathfinding `Seek` toward the leash point, falling
   back to an action that returns to `Idle` on arrival. That closes the loop.

Because Idle now enters ReturnHome by itself, the mod does not strictly need setState() any
more — calling setLeashPoint() is enough to make an NPC walk, exactly like vanilla does.

The `Idle` block has to be restructured on the way: it currently carries a `BodyMotion`
directly, and an instruction cannot hold both `Actions` and `Instructions`
(https://hytalemodding.dev/en/docs/guides/npc-workings/npc-intro). The existing wander is
therefore pushed down into a child instruction, with the leash check as its sibling.

The `Sleep` state that RoutineAISystem also sets was deliberately left out: it would need a
link of its own, and inventing a fake transition purely to satisfy the validator is worse
than letting the mod's own sleep handling (block mount + Frozen + animation) do the work.

USAGE
-----
    python3 scripts/add_returnhome_state.py            # apply
    python3 scripts/add_returnhome_state.py --check    # report only, change nothing
    python3 scripts/add_returnhome_state.py --revert   # undo

Idempotent: re-running will not duplicate anything.
"""

import json
import sys
from pathlib import Path

ROLES_DIR = Path(__file__).resolve().parent.parent / "src/main/resources/Server/NPC/Roles"
# Holds each role's original Idle motion so --revert is lossless. Committed alongside the
# script; deleting it means the roles can only be restored from git.
IDLE_MOTION_BACKUP = Path(__file__).resolve().parent / "idle_motion_backup.json"

# What Idle steers with once the role's own wander is disabled: nothing. Verified as a valid
# standalone BodyMotion in vanilla (Intelligent/Neutral/Kweebec/Kweebec_Prisoner.json).
IDLE_MOTION_DISABLED = {"Type": "Nothing"}

STATE_NAME = "ReturnHome"
IDLE_STATE = "Idle"
MARKER = "add_returnhome_state.py"

# Idle hands over at ENTER_RANGE, ReturnHome gives up at EXIT_RANGE. Keeping them apart
# gives hysteresis, so an NPC parked near its target cannot flip between the two states.
ENTER_RANGE = 2
EXIT_RANGE = 1

IDLE_TO_RETURN_HOME = {
    "$Comment": f"Injected by {MARKER} - leave Idle when SimTale moves our leash point away. "
                f"Also the link that makes {STATE_NAME} reachable, without which the role fails to validate.",
    "Sensor": {
        "Type": "Leash",
        "Range": ENTER_RANGE,
    },
    "Actions": [
        {
            "Type": "State",
            "State": STATE_NAME,
        }
    ],
}

RETURN_HOME_STATE = {
    "$Comment": f"Injected by {MARKER} - walk to the leash point set by SimTale.",
    "Sensor": {
        "Type": "State",
        "State": STATE_NAME,
    },
    "Instructions": [
        {
            "Sensor": {
                # Integer range, matching _Core/Tests/Test_Soft_Leash.json ("Range": 3).
                "Type": "Leash",
                "Range": EXIT_RANGE,
            },
            "BodyMotion": {
                "Type": "Seek",
                "SlowDownDistance": 2,
                "StopDistance": 0.5,
                # Vanilla's ReturnHome uses 1 (a sprint home after combat); SimTale uses this
                # for everyday errands like walking to a bed, so it walks instead.
                "RelativeSpeed": 0.6,
                "UsePathfinder": True,
            },
        },
        {
            "$Comment": "Arrived (the Leash sensor stopped firing) - hand control back to Idle.",
            "Actions": [
                {
                    "Type": "State",
                    "State": IDLE_STATE,
                }
            ],
        },
    ],
}


def is_injected(entry: dict) -> bool:
    return MARKER in str(entry.get("$Comment", ""))


WANDER_WRAPPER_NOTE = "Original Idle motion"


def is_wander_wrapper(entry: dict) -> bool:
    """
    True for the wrapper this script builds around the role's original Idle motion.

    It must be told apart from the blocks that were genuinely *added*: on revert those get
    deleted, but this one holds the role's own motion and has to be unwrapped instead.
    Deleting it left Idle with no BodyMotion at all.
    """
    return WANDER_WRAPPER_NOTE in str(entry.get("$Comment", ""))


def is_leash_link(entry: dict) -> bool:
    """
    True for the Idle -> ReturnHome transition specifically.

    Checked by looking at what the instruction *does*, not by its comment: the wrapper this
    script builds around the original wander also carries the marker comment, so a
    marker-based test reported the link as already present and silently skipped inserting
    it — leaving ReturnHome stray and the role invalid all over again.
    """
    for action in entry.get("Actions") or []:
        if action.get("Type") == "State" and action.get("State") == STATE_NAME:
            return True
    return False


def find_state_blocks(role: dict):
    """
    Returns the inner Instructions list holding the per-state blocks, or None if the role
    does not have the expected shape.

    Shape: Instructions -> [ { Sensor: {Type: Any}, Instructions: [ <per-state blocks> ] } ]
    """
    outer = role.get("Instructions")
    if not isinstance(outer, list) or not outer:
        return None
    inner = outer[0].get("Instructions")
    return inner if isinstance(inner, list) else None


def find_block_for_state(blocks: list, state: str):
    for entry in blocks:
        sensor = entry.get("Sensor") or {}
        if sensor.get("Type") == "State" and sensor.get("State") == state:
            return entry
    return None


def copy(obj):
    """Deep copy so each role gets its own instance rather than a shared reference."""
    return json.loads(json.dumps(obj))


def apply(role: dict, key: str, backup: dict) -> bool:
    """Patches the role in place. Returns True when anything changed."""
    blocks = find_state_blocks(role)
    if blocks is None:
        return False

    idle = find_block_for_state(blocks, IDLE_STATE)
    if idle is None:
        return False

    changed = False

    # 1. Idle must be able to hold child instructions. It ships with a bare BodyMotion, so
    #    push that down into a child of its own first.
    if "Instructions" not in idle:
        wander = {"$Comment": f"{WANDER_WRAPPER_NOTE}, nested by {MARKER}."}
        if "BodyMotion" in idle:
            wander["BodyMotion"] = idle.pop("BodyMotion")
        if "HeadMotion" in idle:
            wander["HeadMotion"] = idle.pop("HeadMotion")
        idle["Instructions"] = [wander]
        changed = True

    # 2. Disable the role's own wander, saving it first so --revert stays lossless.
    for child in idle["Instructions"]:
        if not is_wander_wrapper(child):
            continue
        motion = child.get("BodyMotion")
        if motion == IDLE_MOTION_DISABLED:
            break  # already disabled
        if motion is not None:
            backup.setdefault(key, motion)
            child["BodyMotion"] = copy(IDLE_MOTION_DISABLED)
            changed = True
        break

    # 3. The leash check goes first so it is evaluated before anything else in Idle.
    if not any(is_leash_link(e) for e in idle["Instructions"]):
        idle["Instructions"].insert(0, copy(IDLE_TO_RETURN_HOME))
        changed = True

    # 4. The ReturnHome state itself, as a sibling of Idle.
    if find_block_for_state(blocks, STATE_NAME) is None:
        blocks.append(copy(RETURN_HOME_STATE))
        changed = True

    return changed


def revert(role: dict, key: str, backup: dict) -> bool:
    """Removes everything this script injected, restoring the original shape."""
    blocks = find_state_blocks(role)
    if blocks is None:
        return False

    changed = False

    before = len(blocks)
    blocks[:] = [e for e in blocks if not is_injected(e)]
    changed |= len(blocks) != before

    idle = find_block_for_state(blocks, IDLE_STATE)
    if idle and isinstance(idle.get("Instructions"), list):
        # Drop what was added, but keep the wrapper holding the role's own motion.
        children = [
            e for e in idle["Instructions"]
            if is_wander_wrapper(e) or not is_injected(e)
        ]
        if len(children) != len(idle["Instructions"]):
            idle["Instructions"] = children
            changed = True

        # Put the original wander back before collapsing.
        original = backup.get(key)
        for child in children:
            if is_wander_wrapper(child) and original is not None:
                if child.get("BodyMotion") != original:
                    child["BodyMotion"] = copy(original)
                    changed = True
                break

        # Collapse the wrapper back into Idle once it is the only child left.
        if len(children) == 1 and is_wander_wrapper(children[0]):
            only = children.pop()
            del idle["Instructions"]
            for k in ("BodyMotion", "HeadMotion"):
                if k in only:
                    idle[k] = only[k]
            changed = True

    return changed


def main() -> int:
    check_only = "--check" in sys.argv
    do_revert = "--revert" in sys.argv

    if not ROLES_DIR.is_dir():
        print(f"ERRO: pasta de roles nao encontrada: {ROLES_DIR}")
        return 1

    files = sorted(ROLES_DIR.rglob("*.json"))
    if not files:
        print(f"ERRO: nenhum role .json em {ROLES_DIR}")
        return 1

    backup = {}
    if IDLE_MOTION_BACKUP.is_file():
        try:
            backup = json.loads(IDLE_MOTION_BACKUP.read_text(encoding="utf8"))
        except json.JSONDecodeError:
            print(f"AVISO: backup ilegivel, sera recriado: {IDLE_MOTION_BACKUP.name}")

    if do_revert and not backup:
        print("AVISO: sem backup do movimento original do Idle.")
        print("       O revert vai desfazer o resto, mas nao restaura o WanderInCircle.")
        print("       Nesse caso use: git checkout -- src/main/resources/Server/NPC/Roles")

    touched = untouched = malformed = 0

    for path in files:
        try:
            role = json.loads(path.read_text(encoding="utf8"))
        except json.JSONDecodeError as exc:
            print(f"  JSON invalido, ignorado: {path.name} ({exc})")
            malformed += 1
            continue

        if find_state_blocks(role) is None:
            print(f"  formato inesperado, ignorado: {path.relative_to(ROLES_DIR)}")
            malformed += 1
            continue

        key = str(path.relative_to(ROLES_DIR))
        probe = copy(role)
        changed = revert(probe, key, backup) if do_revert else apply(probe, key, backup)

        if not changed:
            untouched += 1
            continue

        if not check_only:
            path.write_text(json.dumps(probe, indent=2, ensure_ascii=False) + "\n", encoding="utf8")
        touched += 1

    if not check_only and not do_revert and backup:
        IDLE_MOTION_BACKUP.write_text(
            json.dumps(backup, indent=2, ensure_ascii=False) + "\n", encoding="utf8")

    verb = "revertidos" if do_revert else ("precisam de patch" if check_only else "corrigidos")
    print()
    print(f"{verb}: {touched}")
    print(f"ja no estado desejado: {untouched}")
    print(f"ignorados (formato/JSON): {malformed}")
    print(f"total de arquivos: {len(files)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
