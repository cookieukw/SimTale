#!/usr/bin/env python3
"""
Adds the `ReturnHome` state to every SimTale NPC role so the NPCs can actually walk to
the leash point the mod sets for them.

WHY
---
The SimTale roles only ever declared a single state, `Idle`, whose body motion is
`WanderInCircle`. There was no state capable of navigating to a destination. The mod's
NPCMovementHelper called `setState(ref, "Moving", ...)`, which produced a stream of

    State 'Moving.null' in 'SimTale_Human_Female_162' does not exist
    and was set by an external call

in the server log. The leash still dragged the NPC toward its target while the role kept
playing the idle animation — the NPC appeared to slide around on ice.

The block injected below is a faithful port of how vanilla Hytale does exactly this. See
Assets/Server/NPC/Roles/_Core/Templates/Template_Intelligent.json, the "ReturnHome" state:
a `Leash` sensor that fires while the NPC is away from its leash point, a `Seek` body
motion with `UsePathfinder` to walk there, and a fallback instruction that drops back to
`Idle` on arrival. Vanilla computes the sensor range from a `LeashDistance` parameter;
SimTale roles declare no Parameters block, so a literal is used instead.

USAGE
-----
    python3 scripts/add_returnhome_state.py            # apply
    python3 scripts/add_returnhome_state.py --check    # report only, change nothing

Idempotent: re-running it will not duplicate the state.
"""

import json
import sys
from pathlib import Path

ROLES_DIR = Path(__file__).resolve().parent.parent / "src/main/resources/Server/NPC/Roles"

STATE_NAME = "ReturnHome"
SLEEP_STATE_NAME = "Sleep"

# Mirrors Template_Intelligent.json's ReturnHome block. RelativeSpeed is lowered from
# vanilla's 1 (a full sprint home after combat) to a walking pace, since SimTale NPCs use
# this for everyday errands like walking to a bed or a chest.
RETURN_HOME_INSTRUCTION = {
    "$Comment": "Injected by scripts/add_returnhome_state.py - walk to the leash point set by SimTale.",
    "Sensor": {
        "Type": "State",
        "State": STATE_NAME,
    },
    "Instructions": [
        {
            "Sensor": {
                "Type": "Leash",
                "Range": 1.0,
            },
            "BodyMotion": {
                "Type": "Seek",
                "SlowDownDistance": 2,
                "StopDistance": 0.5,
                "RelativeSpeed": 0.6,
                "UsePathfinder": True,
            },
        },
        {
            "$Comment": "Arrived (the Leash sensor stopped firing) - hand control back to Idle.",
            "Actions": [
                {
                    "Type": "State",
                    "State": "Idle",
                }
            ],
        },
    ],
}


# RoutineAISystem also calls setState(ref, "Sleep") when an NPC mounts its bed, and that
# state was missing too. The mod already handles sleeping itself (block mount, Frozen
# component, Sleep animation), so all the role has to do is stop the idle wander from
# fighting it — hence a plain `Nothing` motion rather than vanilla's much larger
# Component_Instruction_Wild_Sleep_State.
SLEEP_INSTRUCTION = {
    "$Comment": "Injected by scripts/add_returnhome_state.py - stay put while SimTale handles sleeping.",
    "Sensor": {
        "Type": "State",
        "State": SLEEP_STATE_NAME,
    },
    "BodyMotion": {
        "Type": "Nothing",
    },
}


def find_instruction_list(role: dict):
    """
    Returns the inner Instructions list that holds the per-state blocks, or None when the
    role does not follow the expected shape.

    Shape: Instructions -> [ { Sensor: {Type: Any}, Instructions: [ <per-state blocks> ] } ]
    """
    outer = role.get("Instructions")
    if not isinstance(outer, list) or not outer:
        return None
    inner = outer[0].get("Instructions")
    if not isinstance(inner, list):
        return None
    return inner


def has_state(instructions: list, state: str) -> bool:
    for entry in instructions:
        sensor = entry.get("Sensor") or {}
        if sensor.get("Type") == "State" and sensor.get("State") == state:
            return True
    return False


def copy(obj):
    """Deep copy so every role gets its own instance instead of a shared reference."""
    return json.loads(json.dumps(obj))


def main() -> int:
    check_only = "--check" in sys.argv

    if not ROLES_DIR.is_dir():
        print(f"ERRO: pasta de roles nao encontrada: {ROLES_DIR}")
        return 1

    files = sorted(ROLES_DIR.rglob("*.json"))
    if not files:
        print(f"ERRO: nenhum role .json em {ROLES_DIR}")
        return 1

    patched = skipped = malformed = 0

    for path in files:
        try:
            role = json.loads(path.read_text(encoding="utf8"))
        except json.JSONDecodeError as exc:
            print(f"  JSON invalido, ignorado: {path.name} ({exc})")
            malformed += 1
            continue

        instructions = find_instruction_list(role)
        if instructions is None:
            print(f"  formato inesperado, ignorado: {path.relative_to(ROLES_DIR)}")
            malformed += 1
            continue

        missing = []
        if not has_state(instructions, STATE_NAME):
            missing.append(RETURN_HOME_INSTRUCTION)
        if not has_state(instructions, SLEEP_STATE_NAME):
            missing.append(SLEEP_INSTRUCTION)

        if not missing:
            skipped += 1
            continue

        if not check_only:
            # Appended last so the existing Idle block keeps priority in the instruction
            # list; these blocks only matter once the mod sets the matching state.
            for block in missing:
                instructions.append(copy(block))
            path.write_text(json.dumps(role, indent=2, ensure_ascii=False) + "\n", encoding="utf8")

        patched += 1

    verb = "precisam de patch" if check_only else "corrigidos"
    print()
    print(f"{verb}: {patched}")
    print(f"ja completos ({STATE_NAME} + {SLEEP_STATE_NAME}): {skipped}")
    print(f"ignorados (formato/JSON): {malformed}")
    print(f"total de arquivos: {len(files)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
