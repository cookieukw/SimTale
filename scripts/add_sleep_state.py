#!/usr/bin/env python3
"""
Da a todos os roles do SimTale um estado `Sleep`, para que a NPC deitada assuma a pose de
dormir em vez de ficar de pe rotacionada.

O PROBLEMA
----------
RoutineAISystem chama `stateSupport.setState(ref, "Sleep", ...)` ao montar a NPC na cama, mas
nenhum role do SimTale declarava esse estado. O servidor avisava a cada tentativa:

    State 'Sleep.null' in 'SimTale_Human_Female_28' does not exist
    and was set by an external call

Sem o estado, a NPC continuava em `Idle`. O modelo ficava na pose padrao — de pe — apenas
rotacionado para a horizontal. Um corpo em pe deitado de lado tem a origem nos pes, nao no meio,
e por isso ele aparecia deslocado ao longo da cama.

Isso resistiu a varias tentativas de correcao por posicao. As evidencias que fecharam o
diagnostico:

  * o jogador deita centralizado na MESMA cama, entao o ponto de montagem do asset esta certo;
  * mover o TransformComponent dois blocos nao mudou nada na tela;
  * alterar o attachmentOffset do MountedComponent tambem nao;
  * o log so reclamava do estado inexistente.

Ou seja: nunca foi posicao, era pose.

O LINK, E POR QUE ELE PRECISA SER REAL
--------------------------------------
Estados precisam ser alcancaveis a partir do StartState, senao o role inteiro falha na
validacao e `NPCPlugin.spawnNPC()` passa a devolver null — foi assim que a primeira versao do
add_returnhome_state.py quebrou o spawn.

O gatilho aqui nao e inventado: e o mesmo padrao que Template_Intelligent usa para os NPCs do
proprio jogo dormirem — um sensor `Block` procurando o conjunto de camas ao alcance de 1 bloco:

    { "Type": "Block", "Range": 1, "Blocks": {"Compute": "BedBlockSet"}, "Reserve": true }

A NPC do SimTale so fica a 1 bloco de uma cama quando o mod a montou nela, entao a condicao e
verdadeira exatamente no momento certo e nunca dispara sozinha por acidente.

O conjunto de blocos vem de Server/BlockTypeList/SimTale_Beds.json, que o mod passa a enviar.
Vanilla aponta para "Trork_Bedroll"; nenhum conjunto equivalente para moveis de cama existia,
entao o mod define o seu com os 15 ids de cama do jogo.

Sair do Sleep continua por conta do mod: RoutineAISystem chama setState("Idle") ao acordar,
e o estado Idle e sempre valido.

USO
---
    python3 scripts/add_sleep_state.py            # aplica
    python3 scripts/add_sleep_state.py --check    # so relata
    python3 scripts/add_sleep_state.py --revert   # desfaz

Idempotente: rodar de novo nao duplica nada.
"""

import json
import sys
from pathlib import Path

ROLES_DIR = Path(__file__).resolve().parent.parent / "src/main/resources/Server/NPC/Roles"

STATE_NAME = "Sleep"
IDLE_STATE = "Idle"
MARKER = "add_sleep_state.py"
BED_BLOCK_SET = "SimTale_Beds"

# Entrada em Sleep, colocada DENTRO do bloco Idle. E este o link que torna o estado alcancavel.
IDLE_TO_SLEEP = {
    "$Comment": f"Injected by {MARKER} - enter Sleep once SimTale has mounted us on a bed. "
                f"Also the link that makes {STATE_NAME} reachable; without it the role fails to validate.",
    "Sensor": {
        "Type": "Block",
        "Range": 1,
        "Blocks": BED_BLOCK_SET,
    },
    "Actions": [
        {
            "Type": "State",
            "State": STATE_NAME,
        }
    ],
}

# O estado em si. BodyMotion "Nothing" para o role nao tentar mover quem esta dormindo — o mod
# ja cuida da posicao pelo sistema de montagem do jogo.
SLEEP_STATE = {
    "$Comment": f"Injected by {MARKER} - stay put and hold the sleeping pose.",
    "Sensor": {
        "Type": "State",
        "State": STATE_NAME,
    },
    "Instructions": [
        {
            "$Comment": "Quem dorme nao anda. A saida do estado e feita pelo mod ao acordar.",
            "BodyMotion": {
                "Type": "Nothing",
            },
        }
    ],
}


def is_injected(entry: dict) -> bool:
    return isinstance(entry, dict) and MARKER in str(entry.get("$Comment", ""))


def is_sleep_link(entry: dict) -> bool:
    """Reconhece o link Idle -> Sleep pela ACAO, nao pelo comentario.

    Uma versao anterior deste tipo de script identificava blocos pelo texto do $Comment e
    acabou confundindo dois blocos diferentes, deixando de inserir o link. Olhar a acao e o
    unico criterio que nao mente.
    """
    if not isinstance(entry, dict):
        return False
    for action in entry.get("Actions", []) or []:
        if isinstance(action, dict) and action.get("Type") == "State" \
                and action.get("State") == STATE_NAME:
            return True
    return False


def is_sleep_state(entry: dict) -> bool:
    if not isinstance(entry, dict):
        return False
    sensor = entry.get("Sensor")
    return isinstance(sensor, dict) and sensor.get("Type") == "State" \
        and sensor.get("State") == STATE_NAME


def find_state_list(role: dict):
    """Devolve a lista de instrucoes onde os estados vivem (o filho do sensor 'Any')."""
    instructions = role.get("Instructions")
    if not isinstance(instructions, list):
        return None
    for block in instructions:
        if not isinstance(block, dict):
            continue
        sensor = block.get("Sensor")
        if isinstance(sensor, dict) and sensor.get("Type") == "Any":
            inner = block.get("Instructions")
            if isinstance(inner, list):
                return inner
    return None


def find_idle_block(state_list):
    for block in state_list:
        if not isinstance(block, dict):
            continue
        sensor = block.get("Sensor")
        if isinstance(sensor, dict) and sensor.get("Type") == "State" \
                and sensor.get("State") == IDLE_STATE:
            return block
    return None


def process(path: Path, check_only: bool, revert: bool):
    role = json.loads(path.read_text(encoding="utf-8"))

    state_list = find_state_list(role)
    if state_list is None:
        return "sem-lista-de-estados"

    idle = find_idle_block(state_list)
    if idle is None:
        return "sem-estado-idle"

    idle_children = idle.get("Instructions")
    if not isinstance(idle_children, list):
        return "idle-sem-instrucoes"

    has_link = any(is_sleep_link(e) for e in idle_children)
    has_state = any(is_sleep_state(e) for e in state_list)

    if revert:
        if not has_link and not has_state:
            return "nada-a-reverter"
        idle["Instructions"] = [e for e in idle_children if not (is_injected(e) and is_sleep_link(e))]
        novos = [e for e in state_list if not (is_injected(e) and is_sleep_state(e))]
        state_list[:] = novos
        if not check_only:
            path.write_text(json.dumps(role, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        return "revertido"

    if has_link and has_state:
        return "ja-aplicado"

    if not has_link:
        # O link vai na FRENTE das demais instrucoes de Idle. Ordem importa: as instrucoes sao
        # avaliadas em sequencia, e a de movimento do Idle rodaria antes, mantendo a NPC no
        # estado errado por um tick.
        idle_children.insert(0, json.loads(json.dumps(IDLE_TO_SLEEP)))

    if not has_state:
        state_list.append(json.loads(json.dumps(SLEEP_STATE)))

    if not check_only:
        path.write_text(json.dumps(role, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return "aplicado"


def main():
    args = sys.argv[1:]
    check_only = "--check" in args
    revert = "--revert" in args

    arquivos = sorted(ROLES_DIR.rglob("SimTale_*.json"))
    if not arquivos:
        print(f"Nenhum role encontrado em {ROLES_DIR}")
        return

    contagem = {}
    problemas = []
    for path in arquivos:
        try:
            resultado = process(path, check_only, revert)
        except Exception as e:
            resultado = "erro"
            problemas.append((path.name, str(e)[:80]))
        contagem[resultado] = contagem.get(resultado, 0) + 1

    print(f"roles analisados: {len(arquivos)}")
    for k, v in sorted(contagem.items()):
        print(f"  {k}: {v}")
    for nome, err in problemas[:10]:
        print(f"  ! {nome}: {err}")
    if check_only:
        print("\nMODO CHECK: nada foi escrito.")


if __name__ == "__main__":
    main()
