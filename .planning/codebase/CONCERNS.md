# Technical Concerns

> Auditoria real feita em 13/09, lendo o código (grep/wc/du), não suposição genérica. Ver
> `testing_checklist.md` e `docs/historico/bugs-resolvidos.md` para o histórico de bugs que já
> motivou parte dos pontos abaixo.

## Identified Issues

- **Test Coverage:** Zero cobertura de teste automatizado complica qualquer refatoração.
- **Dependencies:** Dependência forte de `caskara` e de paths absolutos do SDK do Hytale
  (`local.properties` → `hytale.dir`) deixa o projeto menos portável sem configuração específica.
- **Duplicação de validação de entidade (47 ocorrências, ~20 arquivos):** o padrão
  `entityRef != null && entityRef.isValid()` está copiado manualmente em vez de centralizado num
  helper (`core/lifecycle/LifecycleUtils.java` já existe e seria o lugar natural). Já causou 3
  crashes reais e distintos (`RoutineAISystem`, `SetMoodSubCommand`, `BabyCareTickSystem` — ver
  bugs-resolvidos.md item 14), todos pela mesma causa: um novo loop copiado sem o `.isValid()`.
- **Mesmo bug corrigido em 1 lugar, ainda vivo em 4:** `FarmPostRegistry.claimNearest` ganhou um
  limite de distância (`CLAIM_SEARCH_RADIUS = 48.0`) depois que a fazendeira reivindicava
  espantalho do outro lado do mapa e nunca chegava. `FishingPostRegistry`, `LumberPostRegistry`,
  `BathRegistry` e `LeisureRegistry` têm o mesmo método `claimNearest` (cópia quase literal),
  mas nenhum dos quatro tem esse limite — o mesmo bug pode se repetir em pesca/lenha/banho/lazer.
- **Método morto:** `FishingPostRegistry.nearestTo(x, y, z)` não tem nenhuma chamada em todo o
  projeto.
- **Scan O(n²) por tick sem throttle:** `SimTaleTickSystem.processMountedSleepingNPCs()`
  (chamado de dentro de `tick()`, que já roda por NPC) varre `SimTale.ACTIVE_NPCS` inteiro de
  novo a cada chamada, sem nenhum throttle — mesma classe já usa throttle de 10 ticks em
  `SimTaleMarkerProvider.captureSnapshot()` ao lado, então o padrão certo já existe no projeto,
  só não foi aplicado aqui.
- **Scan "barato" que não é barato:** `ChildCarryHelper.carriedBy`/`findCarriedBy`, chamado todo
  tick por todo jogador via `ChildCarryReleaseTickSystem`, varre `ACTIVE_NPCS` inteiro (com
  `getComponent(MountedComponent)` por NPC) antes de checar se alguém está sendo carregado —
  custo O(jogadores × NPCs) por tick disfarçado de "cheapest check first".
- **`libs/Caskara.jar` (13,9 MB) commitado apesar de `libs/` estar no `.gitignore`** — foi
  adicionado à força em algum momento; sozinho é ~35% do peso do `.git` (~39 MB).
- **`web copia/` (2,6 MB) rastreada pelo git** — parece cópia de rascunho de `web/`, com
  imagens (`bg.png`, `logo.png`, `preview.png`) byte-a-byte idênticas ao original.

## Technical Debt

- **SDK Pathing:** dependência de `local.properties` para paths absolutos do SDK (`hytale.dir`)
  pode causar falha de build em ambientes novos se mal configurado.
- **Arquivos grandes demais, com responsabilidades misturadas** (não é só tamanho — os imports
  cobrem domínios sem relação direta):
  - `SimTaleCommand.java` (2346 linhas) — ~40 subcomandos `XxxSubCommand` (debug, ciclo de vida,
    testes sociais) todos no mesmo arquivo/dispatcher.
  - `RoutineAISystem.java` (1478 linhas) — mistura tick de rotina/agenda com movimentação,
    montaria, animação e relacionamento.
  - `InteractionManager.java` (925 linhas) — mistura diálogo/UI de NPC com combate/stats e
    inventário.
  - (Para contraste: `NPCInteractionPage.java`, `HouseManager.java` e `NPCSocialHelper.java`
    também são grandes, mas coesos — um assunto só cada — e não são candidatos a split.)
- **`scripts/*_backup.json` (~2,3 MB no total, incluindo `appearance_backup.json` de 1,8 MB)**
  rastreados pelo git — vale confirmar se ainda são necessários ou são sobra de sessão de teste.
