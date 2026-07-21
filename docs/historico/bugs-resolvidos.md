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
