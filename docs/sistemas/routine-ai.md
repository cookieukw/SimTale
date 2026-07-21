# Sistema de IA de Rotina (RoutineAISystem e Sono)

> **TL;DR**: Controla a autonomia dos NPCs. Eles realizam varreduras de ambiente para satisfazer necessidades biológicas (sono, alimentação, higiene), buscando ativamente camas, baús e água. Inclui um sistema otimizado de busca de caminhos (pathfinding throttling) e registro físico de camas.

---

## 1. O que é e para que serve
O `RoutineAISystem` é o motor de autonomia dos NPCs. Em vez de ficarem parados no mesmo lugar, os NPCs decidem suas ações baseado nas suas necessidades. Eles buscam camas vazias para dormir quando a energia está baixa, procuram contêineres/baús com comida ao sentirem fome, entram na água para tomar banho se a higiene cair, conversam espontaneamente com outros NPCs próximos para socializar e realizam trabalhos (como construir) em canteiros de obras.

---

## 2. Como funciona por dentro
O sistema opera através do `RoutineAISystem.java` e do componente `RoutineAIComponent.java` do ECS:

```
RoutineAISystem (Tick per NPC)
├── Evaluation Phase: Avalia se necessidades estão críticas (Fome <= 0 -> Morte)
├── Decision Phase: Escolhe a tarefa (IDLE, FINDING_BED, MOVING_TO_BED, SLEEPING, etc.)
└── Action Phase: Executa a ação no mundo físico
```

### O Fluxo de Sono e Busca de Camas
1.  **Low Energy Check**: Se a energia do NPC cair abaixo de 30 (ou 60 para NPCs com o traço `LAZY`), ele interrompe sua tarefa e entra em `FINDING_BED`.
2.  **Unclaimed Bed Registry Scan**: Varre o `BedRegistry` em busca da cama vazia e válida mais próxima. O registro de camas é atualizado via eventos físicos de colocação/quebra de blocos no mundo (`BedPlaceBlockEventSystem`, `BedBlockEventSystem`).
3.  **Approach Vector Calculation**: O NPC caminha até uma posição segura adjacente à cama (`getBedApproachPosition`), validando se o bloco de apoio e o bloco acima são transitáveis (ar) e o bloco de base é sólido.
4.  **Block Mounting & Teleportation**: O NPC é montado na cama via `BlockMountAPI` e posicionado geometricamente no colchão. Ele recebe o componente `Teleport` nativo para fixar sua física (evitando que deslize para fora da cama), o componente `Frozen` e ativa a animação de sono (`Sleep`).
5.  **Wake Up Phase**: Ao preencher a energia (100) ou atingir a duração máxima da soneca, o NPC executa a animação de despertar (`Wake`), é desmontado da cama e teleportado de volta para a posição adjacente de apoio.

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

---

## 6. Pontos em Aberto / Dívida Técnica
*   **Desgaste de Comida Infinito**: Os NPCs detectam baús com comida, mas a alimentação é puramente cosmética no cômputo de inventário (eles se aproximam e comem, mas não consomem um item físico de comida do baú para evitar esvaziar os estoques do jogador).

---

## 7. Perguntas Frequentes (FAQ)

### O que acontece se dois NPCs tentarem dormir na mesma cama?
O `BedRegistry` mapeia quais camas estão ativas. O método `getBedPos()` busca todas as camas registradas e remove as coordenadas que já estão salvas no componente `bedLocation` de qualquer outro NPC ativo. Portanto, a cama é desduplicada e nunca ocupada por dois indivíduos.

### NPCs bebês e toddlers dormem em camas?
Não. Bebês e Toddlers (crianças pequenas) pulam a rotina de busca de camas e são alimentados/cuidados em turnos diretamente pelos pais em seus inventários ou berços (gerenciados pelo `BabyCareTickSystem`).
