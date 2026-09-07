# Sistema de IA de Rotina (RoutineAISystem e Sono)

> **TL;DR**: Controla a autonomia dos NPCs. Eles realizam varreduras de ambiente para satisfazer necessidades biológicas (sono, alimentação, higiene), buscando ativamente camas, baús e água, e procuram outros NPCs para conversar quando se sentem sozinhos. Inclui um sistema otimizado de busca de caminhos (pathfinding throttling) e registro físico de camas.

---

## 1. O que é e para que serve
O `RoutineAISystem` é o motor de autonomia dos NPCs. Em vez de ficarem parados no mesmo lugar, os NPCs decidem suas ações baseado nas suas necessidades. Eles buscam camas vazias para dormir quando a energia está baixa, procuram contêineres/baús com comida ao sentirem fome, entram na água para tomar banho se a higiene cair, conversam espontaneamente com outros NPCs próximos para socializar e realizam trabalhos (como construir) em canteiros de obras.

---

## 2. Como funciona por dentro
O sistema opera através do `RoutineAISystem.java` e do componente `RoutineAIComponent.java` do ECS:

```
RoutineAISystem (Tick per NPC)
├── Evaluation Phase: Avalia se necessidades estão críticas (dano de inanição >= 200 -> Morte)
├── Decision Phase: Escolhe a tarefa (IDLE, FINDING_BED, MOVING_TO_BED, SLEEPING, etc.)
└── Action Phase: Executa a ação no mundo físico
    ├── NPCHungerHelper  → FINDING_FOOD, MOVING_TO_FOOD, EATING
    ├── NPCWorkHelper    → MOVING_TO_WORK, FARMING, HUNTING, PLANTING, MOVING_TO_DEPOSIT
    ├── NPCSocialHelper  → MOVING_TO_SOCIALIZE, SOCIALIZING, WANDERING
    └── NPCLeisureHelper → FINDING_LEISURE, MOVING_TO_LEISURE, DOING_HOBBY
```

O `RoutineAISystem` concentra a **decisão** (qual tarefa assumir a partir de `IDLE`) e o fluxo de sono; a **execução** dos demais fluxos é delegada a helpers estáticos, mantendo o arquivo principal navegável.

### O Fluxo de Sono e Busca de Camas
1.  **Low Energy Check**: Se a energia do NPC cair abaixo de 30 (ou 60 para NPCs com o traço `LAZY`), ele interrompe sua tarefa e entra em `FINDING_BED`.
2.  **Bed Resolution**:
    *   *Adultos*: Varrem o `BedRegistry` em busca da cama vazia e válida mais próxima (`!isBedTakenByAnotherNpc`). O registro é mantido por listeners de blocos físicos (`BedPlaceBlockEventSystem`, `BedBlockEventSystem`).
    *   *Crianças*: Não disputam camas solteiras na vila. Elas consultam diretamente `FamilyBonds.findParentBed(npc)` para herdar e vincular a cama cadastrada dos pais (seja por vínculo com NPC ativo ou casa registrada).
3.  **Approach Vector Calculation**: O NPC caminha até uma posição segura adjacente à cama (`getBedApproachPosition`), validando se o bloco de apoio e o bloco acima são transitáveis (ar) e o bloco de base é sólido.
4.  **Block Mounting & Co-Sleeping**:
    *   O NPC tenta montar na cama via `BlockMountAPI.mountOnBlock` e recebe o componente `Frozen` e a animação de sono (`Sleep`).
    *   *Fallback Familiar para Crianças*: Se o ponto de montagem nativo já estiver ocupado pelo pai ou mãe, a API nativa rejeita a montagem secundária. Em vez de descartar a cama do filho (como acontecia antes), o sistema posiciona a criança diretamente no colchão/ao lado dos pais (`bedPos.y + 0.6`), fixa sua leash com `NPCMovementHelper.pinLeashAt`, ativa a animação `Sleep` e mantém o estado em `TaskType.SLEEPING` sem apagar seu vínculo residencial.
    *   *Proteção de Sono*: Durante a noite, o `GrowthTickSystem` não interrompe nem remove da cama crianças adormecidas se o responsável se afastar temporariamente.
5.  **Wake Up Phase**: Ao preencher a energia (100) ou atingir a duração máxima da soneca, o NPC executa a animação de despertar (`Wake`), é desmontado da cama e teleportado de volta para a posição adjacente de apoio.

### O Fluxo de Socialização (`NPCSocialHelper` e `SimTaleJuiceHelper`)
A socialização ocorre espontaneamente quando um NPC ocioso tem a necessidade `social` abaixo de 85 ou durante caminhadas pela vila (chance aleatória):

1.  **Seleção e Reserva de Alvo**: Varre `SimTale.ACTIVE_NPCS` num raio de 20 blocos. Só NPCs em `IDLE` ou `WANDERING` podem ser abordados (`NPCSocialHelper.isAvailableToTalk`). O parceiro recebe `reservedForSocialUuid`, garantindo que ele espere e não saia correndo antes do anfitrião chegar.
2.  **`MOVING_TO_SOCIALIZE`**: Caminha até o parceiro revalidando se continua acessível. Timeout de 400 ticks.
3.  **Início do Bate-papo (`SOCIALIZING`)**:
    *   Ao chegar a 2,5 blocos, ambos param por 140 ticks (~7 segundos).
    *   Os pontos de *leash* de ambos são ancorados na posição exata em que estão para evitar puxões da IA padrão do Hytale.
    *   Ambos viram de frente um para o outro com `teleportRotation`.
4.  **Diálogo Contextual Turno-a-Turno**:
    *   **Seleção de Tópico**: O sistema pontua o assunto mais relevante no momento: inimizade/hostilidade, romance (se casados/namorados), necessidades críticas (fome $< 40$, cansaço $< 40$), humor ativo, profissão (fazenda, corte de lenha, patrulha de guarda, pesca), escuridão da noite ou amenidades sobre a vila e clima.
    *   **Turno 1 (~0.7s)**: O anfitrião fala sua linha de abertura (`.a`), executa a animação facial de fala (`Talk.blockyanim`) e o parceiro ouve atentamente.
    *   **Turno 2 (~3.5s)**: O convidado responde com a réplica compatível (`.b`), executa a fala (`Talk.blockyanim`) e o anfitrião escuta.
    *   **Filtro de Audição (4 blocos)**: Jogadores a até 4 blocos escutam o bate-papo no chat `[Vila]`. Se nenhum jogador estiver perto, as mensagens de texto são suprimidas para manter o chat limpo e economizar CPU.
5.  **Resultado e Física de Empurrão**:
    *   Restaura +35 de `social` para ambos, melhora relação e propaga contágio de humor.
    *   Se a conversa for hostil (`ENEMIES` ou traço `AGGRESSIVE`), o NPC agressor desfere um empurrão físico real (`SimTaleJuiceHelper.playShove`) com animação de soco, expressão de fúria e repulsão por *knockback*!

### Expressividade e Cumprimentos de Proximidade (`SimTaleJuiceHelper`)
*   **Cumprimentos por Proximidade**: Ao aproximar-se a 4.5 metros de um morador, ele se vira, acena (`Wave`), sorri e envia uma saudação no chat de acordo com o nível de relacionamento (cooldown de 45s).
*   **Flerte Visual**: Cantadas bem-sucedidas disparam rubor facial alegre (`Cheerful`), emote de mandar beijo (`Blow_Kiss`) e partículas de corações (`Hearts`) sobre a cabeça do NPC.
*   **Animações Autênticas de Trabalho**: Agricultores utilizam golpes de enxada (`Till`), lenhadores desferem machadadas (`Chop`) e pescadores observam o horizonte (`Look_Around`).

### O Fluxo de Perambulação (`WANDERING`)
NPCs ociosos têm uma pequena chance por tick de dar uma volta. O destino é sorteado por ângulo e raio (até 8 blocos) **ancorado na cama do NPC**, não na posição atual — isso mantém a vila coesa em vez de espalhar os moradores pelo mapa. NPCs sem cama perambulam em torno de onde estiverem. Um timeout de 300 ticks (`wanderTimer`) devolve o NPC a `IDLE` caso o destino sorteado seja inalcançável.

### O Fluxo de Lazer (`NPCLeisureHelper`)
Quando a necessidade `fun` cai abaixo de 40, o NPC ocioso vai fazer o próprio hobby — a **única** fonte de reposição de `fun` no mod.

O destino depende do `Hobby` sorteado em `NPCPreferences`:

| Hobby | Destino | Animação |
| :--- | :--- | :--- |
| `FISHING` | bloco de água num raio de 15 | `Idle` |
| `MINING` | bloco de pedra | `Smith` |
| `GARDENING` | bloco de plantação | `Smith` |
| `READING`, `SLEEPING` | a própria cama (ou onde estiver, se não tiver casa) | `Idle` |

A varredura de blocos reusa o padrão do banho: cursor `Vector3i` reaproveitado, comparação de id sem alocação e `getChunkIfInMemory`, de modo que nunca força carregamento de chunk de dentro do tick. Não achando nada por perto, o NPC não fica preso — cai no fallback de relaxar em casa, com ganho de `fun` reduzido (0,15/tick contra 0,25/tick no local adequado). Ao terminar, ganha humor `HAPPY`.

### Ligações do Hobby com os Outros Sistemas
O hobby, que antes só aparecia na UI e no prompt da IA, hoje influencia três pontos:

*   **Presente** (`InteractionManager`): item relacionado ao hobby rende afinidade alta. A regra fica abaixo das listas explícitas de favoritos/odiados — preferência pessoal ganha de interesse genérico — e acima de `trash`/`basic`, para que um jardineiro leia sementes como presente atencioso em vez de item comum.
*   **Conversa** (`NPCSocialHelper`): dois NPCs com o mesmo hobby ganham amizade e afinidade extras ao conversar. Só vale em conversa amigável — hobby em comum não melhora discussão.
*   **Trabalho** (`NPCWorkHelper`): ao concluir uma tarefa, o NPC cujo hobby combina com a profissão (`MINING`+Minerador, `FISHING`+Pescador, `GARDENING`+Fazendeiro) ganha `fun` e humor; quem trabalha em algo alheio ao próprio gosto perde um pouco de `fun`.

### Throttling de Movimentação (`moveTo()`)
Para evitar sobrecarga de processamento no servidor Hytale, a IA não recalcula caminhos a cada tick. O método `moveTo()` implementa um controle de vazão (throttling):
```java
double d2 = ai.lastLeashPos.distanceSquared(targetPos);
boolean needsUpdate = d2 > LEASH_UPDATE_THRESHOLD_SQ; // 0.25 (1/2 bloco de distância)
```
A rota física do NPC (sua `LeashPoint` no Hytale) só é atualizada se a coordenada do destino tiver se movido mais do que 0.5 blocos em relação ao último cálculo, poupando CPU.

---

## 3. Decisões de Design e Por Quê
*   **Auto-cura do Registro de Camas (Self-Healing)**:
    *   *Decisão*: Durante a movimentação, o sistema verifica fisicamente se a cama salva do NPC ainda existe no mundo real. Caso o bloco tenha sido quebrado offline ou o chunk tenha carregado pela primeira vez após reinicialização, o sistema auto-registra a cama no `BedRegistry` ou limpa o vínculo do NPC se destruído.
    *   *Por quê*: Camas salvas no Caskara podem ficar dessincronizadas se blocos forem destruídos por explosões ou comandos administrativos sem disparar o listener de bloco. A validação sob demanda garante que NPCs nunca fiquem travados tentando dormir em camas que não existem mais.

*   **Papel de "host" na conversa (`socializeHost`)**:
    *   *Decisão*: Quando dois NPCs conversam, ambos entram em `SOCIALIZING`, mas apenas quem iniciou (o host) aplica os ganhos de social, relacionamento e humor. O convidado apenas aguarda a conversa terminar.
    *   *Por quê*: Sem essa distinção, os dois lados aplicariam a recompensa completa ao fim do próprio timer, dobrando os ganhos de uma única conversa. Um `boolean` no `RoutineAIComponent` foi preferido a inferir o papel pelo estado (ex.: `socializeTargetId == null`), que seria mais econômico mas ilegível na manutenção.

*   **Perambulação ancorada na residência**:
    *   *Decisão*: O destino do passeio é sorteado em torno da `bedLocation`, não da posição atual do NPC.
    *   *Por quê*: Ancorar na posição atual produz um passeio aleatório (*random walk*), em que cada volta parte de onde a anterior terminou. Ao longo de horas de servidor os NPCs derivam para longe e a vila se esvazia. Ancorar em casa garante que o NPC sempre orbite a própria residência.

*   **Filtro de disponibilidade antes de abordar**:
    *   *Decisão*: Só NPCs em `IDLE` ou `WANDERING` podem ser escolhidos como parceiros de conversa.
    *   *Por quê*: Sem o filtro, um NPC carente escolheria como alvo alguém dormindo, caçando ou já conversando, e ao chegar interromperia a tarefa do outro. O filtro também impede que um terceiro NPC invada uma conversa em andamento.

---

## 4. Nível de Complexidade e Robustez
*   **Nível**: 🔴 Complexo/frágil
*   **Risco**: O sistema depende fortemente da integridade física do mundo (chunks carregados) e da física de colisão de Hytale. Se o Hytale alterar a API de `BlockMountAPI` ou as colunas de colisão de blocos de cama, a subida dos NPCs pode falhar ou eles podem travar na colisão de blocos sólidos.

---

## 5. Dificuldades Encontradas e Resolvidas

### Bug histórico do `clone()` sem clonar a Leash
*   **Problema**: NPCs reiniciavam suas posições de caminhada ou ficavam parados ao sofrer atualizações ou transições de chunk.
*   **Causa Raiz**: O método `clone()` de `RoutineAIComponent.java` não copiava os campos `lastLeashPos` e `lastLeashTick` da instância anterior.
*   **Correção**: Atualizado o método `clone()` para repassar e instanciar esses campos corretamente, mantendo a consistência do pathfinding do NPC após cópias de estado.

### NPCs escorregando das camas e travando na grama
*   **Problema**: Ao serem montados na cama, a física do Hytale empurrava as entidades lateralmente, fazendo-as deslizar para fora da casa.
*   **Causa Raiz**: O NPC era posicionado no mesmo nível vertical de colisão da cama (Y da cama), fazendo a colisão empurrá-lo para o lado.
*   **Correção**: O teletransporte do NPC foi ajustado para Y + 2.0 (acima do topo da colisão sólida do bloco da cama), fazendo o NPC cair verticalmente de forma perfeita no colchão sem causar colisão lateral.

### Estados sem tratador congelando a IA (`WANDERING` / `MOVING_TO_SOCIALIZE`)
*   **Problema**: NPCs ociosos paravam de agir por longos períodos e só voltavam a se mexer quando ficavam com sono.
*   **Causa Raiz**: A fase de decisão atribuía os estados `WANDERING` e `MOVING_TO_SOCIALIZE`, mas nenhum bloco do sistema tratava esses estados. Nada chamava `moveTo()` nem devolvia o NPC para `IDLE`, então ele permanecia parado até que o *interrupt* de energia baixa o levasse para `FINDING_BED`. Como a transição para `WANDERING` tinha chance por tick, na prática os NPCs passavam boa parte do tempo travados.
*   **Correção**: Criado o `NPCSocialHelper`, que implementa `MOVING_TO_SOCIALIZE`, `SOCIALIZING` e `WANDERING` com condição de saída e timeout em todos eles. O estado `MOVING_TO_WANDER`, redundante com `WANDERING` e sem nenhuma referência no código, foi removido do enum.

### NPE ao acordar com a cama destruída
*   **Problema**: Crash pontual (`NullPointerException`) durante o estado `SLEEPING`.
*   **Causa Raiz**: O bloco de `SLEEPING` acessava `npc.bedLocation.x` sem verificação de nulo, ao contrário de `MOVING_TO_BED` e `ENTERING_BED`. Se outro sistema liberasse a cama enquanto o NPC dormia, o acesso falhava.
*   **Correção**: Adicionada verificação no início do estado, que desmonta o NPC e o devolve a `IDLE` de forma limpa.

---

## 6. Pontos em Aberto / Dívida Técnica
*   **`MemoryEvent.ATTACKED` nunca é gravado**: só o `InteractionManager` escreve memórias. O trecho do `SimTaleTickSystem` que deixa o NPC `SCARED` ao apanhar é, portanto, código inalcançável — falta um *handler* de dano que registre o evento.
*   **Inconsistência de caminho de animação**: convivem `Characters/Animations/Default/Idle.blockyanim` (fluxo de sono) e `Characters/Animations/Actions/Idle.blockyanim` (demais fluxos). Um dos dois provavelmente falha em silêncio.
*   **Conversa sem retorno visível**: a socialização altera estado interno (necessidades, relacionamento, humor) mas não emite fala nem indicação visual. Para o jogador que observa, dois NPCs parados de frente um para o outro são indistinguíveis de dois NPCs travados.

---

## 7. Perguntas Frequentes (FAQ)

### O que acontece se dois NPCs tentarem dormir na mesma cama?
*   **NPCs Estranhos**: O método `isBedTakenByAnotherNpc` bloqueia a apropriação indevida — a cama é desduplicada no `BedRegistry` e não pode ser tomada por outro morador não aparentado.
*   **Família e Filhos**: Cônjuges e filhos (`FamilyBonds.isChildOf`) têm permissão explícita para compartilhar a cama da residência. Se um dos pais já estiver deitado e ocupando o assento nativo de montagem, a criança deita junto no colchão sem ser expulsa ou perder seu vínculo de lar.

### Crianças e filhos precisam de uma cama separada na casa?
Não. As crianças vinculadas a pais herdam automaticamente a cama da família (`FamilyBonds.findParentBed`). Elas dividem a cama dos pais até atingirem a fase adulta (`ADULT`), momento em que se tornam independentes e saem para reivindicar sua própria residência na vila.

### NPCs bebês e toddlers dormem em camas?
Não. Bebês e Toddlers (crianças pequenas) pulam a rotina autônoma de busca de camas e são cuidados em turnos diretamente pelos pais em seus inventários, braços ou berços (gerenciados pelo `BabyCareTickSystem`).

### O que acontece se um NPC for dormir no meio de uma conversa?
O *interrupt* de energia baixa tem prioridade sobre qualquer tarefa e leva o NPC para `FINDING_BED`, chamando `clearAutonomyState()` para limpar `socializeTargetId`, `socializeHost` e `wanderTimer`. O parceiro que ficou para trás não trava: o lado convidado encerra sozinho quando o próprio timer de conversa expira.

### Dois NPCs podem escolher um ao outro ao mesmo tempo?
Podem. Ambos entram em `MOVING_TO_SOCIALIZE` como host e caminham um na direção do outro. O primeiro a chegar rebaixa o outro para convidado ao iniciar a conversa, e como o ramo do convidado sai antes de aplicar recompensas, os ganhos não são contados duas vezes.

### A relação NPC↔NPC é persistida?
Sim, sem código adicional. Ela usa o mesmo `Map<UUID, Relationship>` do `SimNPCComponent` que já guarda as relações com jogadores — o `SimNPCPersistence` serializa o mapa inteiro, independente de a chave ser um jogador ou outro NPC.
