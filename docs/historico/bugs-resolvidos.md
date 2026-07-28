# Histórico de Bugs Resolvidos

> **TL;DR**: Registro técnico de bugs críticos que afetaram o mod SimTale, listando seus sintomas, diagnóstico técnico (causa raiz) e a resolução aplicada.

---

## 1. Degradação de Performance do Servidor por Filtro de Entidades

### Sintoma
Queda acentuada de TPS (Ticks per Second) no servidor Hytale, resultando em lag massivo de movimentação e atrasos de conexão à medida que mais regiões do mapa eram exploradas ou mais entidades spawnadas.

### Diagnóstico (Causa Raiz)
Em versões anteriores, os sistemas de ticks (como o gerenciador de gravidez e rotinas) faziam varreduras periódicas buscando entidades de NPC no mundo chamando `world.getEntities()` ou realizando queries globais complexas a cada tick para filtrar quem possuía o componente `SimNPCComponent`. Quando o mundo acumulava milhares de entidades (animais, monstros, itens caídos), essa busca linear consumia muito processamento de CPU.

### Resolução
Eliminamos buscas globais no loop de ticks. Implementamos listas estáticas de registro rápido na classe principal:
*   `SimTale.ACTIVE_NPCS` (lista de `SimNPCComponent` ativos).
*   Os NPCs registram-se diretamente nessas listas ao spawnar (`SimTaleEventHandler.onEntitySpawn`) e removem-se ao descarregar o chunk ou morrer (`onEntityDespawn`/`RemoveReason`).
*   Os loops de ticks agora varrem apenas essa coleção reduzida na memória, reduzindo a complexidade de busca de $O(N)$ (todas as entidades do mundo) para $O(K)$ (apenas os NPCs SimTale ativos), eliminando o gargalo de CPU.

---

## 2. NPCs Travados ao Caminhar após Transições de Estado (Leash Clone Bug)

### Sintoma
NPCs que iniciavam tarefas autônomas de caminhada (como ir comer ou dormir) ficavam congelados no lugar e paravam de responder a tarefas de movimentação após sofrerem atualizações de status ou transição de chunk.

### Diagnóstico (Causa Raiz)
A caminhada do NPC no Hytale é controlada pela atualização da coleira de destino (`LeashPoint`). O sistema de ticks atualiza essa coleira de forma controlada (throttling) comparando com as variáveis `lastLeashPos` e `lastLeashTick` do `RoutineAIComponent`.
No entanto, o método `clone()` de `RoutineAIComponent` (usado internamente pelo ECS do Hytale ao substituir ou modificar componentes) não copiava os campos de coleira (`lastLeashPos` e `lastLeashTick`). A nova réplica do componente inicializava esses campos como `null`, reiniciando a lógica e paralisando as atualizações de rota do NPC.

### Resolução
Atualizado o método `clone()` em `RoutineAIComponent.java` para copiar de forma explícita todos os campos auxiliares de navegação:
```java
comp.lastLeashPos = this.lastLeashPos;
comp.lastLeashTick = this.lastLeashTick;
```

---

## 3. Duplicação de Bebês por Descarte ou Baús

### Sintoma
Jogadores conseguiam gerar múltiplos bebês idênticos descartando o item bebê (`simtale:Baby`) no chão ou colocando-o em baús, fazendo com que o sistema de co-parentalidade criasse múltiplos ciclos de cuidados para o mesmo filho.

### Diagnóstico (Causa Raiz)
A lógica original de verificação de custódia do `BabyCareTickSystem` buscava o item apenas na hotbar ativa do jogador. Se o item fosse movido para outro contêiner ou descartado no chão, o sistema não encontrava o portador oficial e permitia o spawn de novas entidades bebês no mundo, duplicando os registros.

### Resolução
Refatorada a detecção no `BabyCareTickSystem.java` para varrer os inventários de forma robusta e persistir a perda/descarte. O sistema sincroniza de forma transacional a custódia. Se o item bebê sai do inventário e é colocado em um baú, a custódia muda para o contêiner ou retorna para o NPC cônjuge de forma segura, destruindo duplicatas flutuantes no mundo.

---

## 4. Profissão e Gênero Perdidos em Clonagens e Recarga

### Sintoma
NPCs trocavam de profissão sozinhos e perdiam o gênero (com efeito colateral em diálogos flexionados e na elegibilidade para gravidez), tipicamente após transições de chunk ou reinício do servidor.

### Diagnóstico (Causa Raiz)
Dois caminhos independentes reintroduziam o mesmo defeito:

*   **`SimNPCComponent.clone()`** delega para o construtor `SimNPCComponent(entityId, name)`, que chama `assignRandomProfession()`. O método então copiava explicitamente personalidade, necessidades e relacionamentos, mas **não** copiava `profession`, `gender`, `family` nem `needs.hygiene` — logo, toda cópia de componente feita pelo ECS sorteava uma profissão nova e zerava o gênero.
*   **`SimNPCPersistence.loadAllNPCs()`** restaurava todos os campos salvos **exceto** `profession`, enquanto o `loadNPC()` (caminho paralelo, quase idêntico) restaurava. Como o `reassembleActiveNPCs()` usado após reinício passa pelo `loadAllNPCs()`, a profissão gravada no banco era descartada.

### Resolução
*   `clone()` passou a copiar explicitamente `profession`, `gender`, `family` e `needs.hygiene`, além de preservar o `yaw` da cama (perdido pelo construtor de 3 argumentos do `BedPos`).
*   Os dois caminhos de carga foram unificados em um único `applyData(component, data)` privado, eliminando a duplicação de ~45 linhas que permitiu a divergência. A reconstrução de relacionamentos passou a tolerar chaves UUID malformadas individualmente, em vez de abortar a carga inteira do NPC.

---

## 5. Gravação em Disco a Cada Segundo por NPC

### Sintoma
Degradação de performance proporcional ao número de NPCs ativos, com I/O de disco constante mesmo com o servidor ocioso.

### Diagnóstico (Causa Raiz)
No `SimTaleTickSystem`, o ramo de interação espontânea era avaliado a **cada tick** para todo NPC sem trabalho ativo:

```java
} else {
    if (Math.random() < 0.05) {
        InteractionManager.performInteraction(npc, npc.entityId, null, InteractionType.RANDOM);
    }
}
```

Com 20 ticks por segundo e 5% de chance, cada NPC disparava aproximadamente uma interação por segundo — e `performInteraction()` termina chamando `SimNPCPersistence.saveNPC()`. Como efeito colateral, o `playerUuid` passado era o próprio `entityId` do NPC, poluindo o mapa de relacionamentos com uma relação do NPC consigo mesmo.

### Resolução
A avaliação foi restringida a uma janela periódica (`absoluteTick % 200 == 0`), reduzindo a frequência em duas ordens de grandeza sem alterar o comportamento observável.

---

## 6. Provider de IA Sobrescrito e Padrão Apagado

### Sintoma
Configurar `"provider": "openrouter"` no `simtale-ai.json` desativava a IA por completo, sem erro visível.

### Diagnóstico (Causa Raiz)
Duas falhas somadas no registro de provedores:

*   O OpenRouter era registrado como `new OpenAIProvider(url, key, model)`, e o `OpenAIProvider` fixava `config.providerId = "openai"`. Como o `NpcAiManager` indexa por id num `Map`, registrar ambos fazia um sobrescrever o outro, e o id `"openrouter"` nunca chegava a existir.
*   `setDefaultProvider(String id)` fazia `this.defaultProvider = providers.get(id)` sem verificação. Um id inexistente atribuía `null`, apagando o provedor padrão válido já registrado.

### Resolução
Adicionado ao `OpenAIProvider` um construtor que aceita `providerId` explícito (o OpenRouter agora registra como `"openrouter"`), e `setDefaultProvider` passou a retornar `boolean`, preservando o padrão anterior e emitindo aviso no log quando o provedor configurado não está disponível.

---

## 7. Saudações Multipalavra Nunca Reconhecidas no Chat

### Sintoma
Dizer "bom dia" ou "boa noite" para um NPC caía no diálogo genérico de conversa fiada em vez da saudação apropriada.

### Diagnóstico (Causa Raiz)
Em `SimTaleChatHandler.ChatIntent.detect()`, as saudações eram testadas com `hasWord()`, que divide a mensagem por espaços e compara token a token:

```java
hasWord(message, "olá", "ola", "hi", "oi", "bom dia", "boa tarde", "boa noite", "e aí")
```

Nenhum token resultante de `split("\\s+")` pode conter espaço, então as entradas multipalavra eram inalcançáveis por construção.

### Resolução
As saudações compostas foram movidas para `hasPhrase()` (que usa `contains`), mantendo as de palavra única em `hasWord()` para preservar a checagem de fronteira de palavra.

---

## 8. NPCs Congelados em Estados sem Tratador

### Sintoma
NPCs ociosos paravam de agir por longos períodos e só voltavam a se mexer quando ficavam com sono.

### Diagnóstico (Causa Raiz)
A fase de decisão do `RoutineAISystem` atribuía os estados `WANDERING` e `MOVING_TO_SOCIALIZE`, mas nenhum bloco do sistema os tratava. Nada chamava `moveTo()` nem devolvia o NPC para `IDLE`, então ele permanecia parado até que o *interrupt* de energia baixa o levasse para `FINDING_BED`. Como a transição para `WANDERING` tinha chance por tick, na prática os NPCs passavam boa parte do tempo travados.

### Resolução
Criado o `NPCSocialHelper`, implementando `MOVING_TO_SOCIALIZE`, `SOCIALIZING` e `WANDERING` com condição de saída e timeout em todos os estados. O `MOVING_TO_WANDER`, redundante com `WANDERING` e sem nenhuma referência no código, foi removido do enum.
