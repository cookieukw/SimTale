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

---

## 9. Um Terço dos Diálogos Exibindo Chave Crua (Rename pela Metade)

### Sintoma
Boa parte das respostas por chat não exibia texto: conversa fiada, gratidão, pedido de ajuda, "o que você sabe fazer", todo o minigame de adivinhação, aceite/recusa de trabalho, troca de profissão e as falas de cuidado com bebê.

### Diagnóstico (Causa Raiz)
Uma chave de tradução no Hytale é `<nome-do-arquivo>.<chave-interna>` — `npc-interactions.lang` contendo `gratitude.friend.1` responde por `npc-interactions.gratitude.friend.1`.

Em algum momento um subconjunto de famílias ganhou um segmento de agrupamento `chat.` **apenas do lado Java**, sem o rename correspondente nos arquivos `.lang`. O resultado é visível dentro do mesmo `switch`, no mesmo arquivo:

```java
case GREETING  -> getRandomVariant("npc-interactions.greeting",       tier, 5)  // ✅ existe
case GRATITUDE -> getRandomVariant("npc-interactions.chat.gratitude", tier, 5)  // ❌ arquivo tem "gratitude.*"
```

Uma auditoria estática cruzando as chaves referenciadas no Java (expandindo as duas sobrecargas de `getRandomVariant` para todas as 5 tiers × N variantes) contra as definidas nos `.lang` apontou **332 chaves inexistentes**, em 26 famílias.

Duas evidências descartaram a hipótese de "traduções incompletas": pt-BR e en-US estavam perfeitamente sincronizados (817 chaves cada, zero divergência), e **toda família existente tinha exatamente o número de variantes que o código sorteia** — nenhum caso de código pedindo 5 quando o arquivo tem 3, nem variantes escritas que o código nunca alcança. Os arquivos estavam certos; o lado Java é que havia derivado.

### Resolução
Removido o segmento `chat.` dos 25 literais afetados em `SimTaleChatHandler` e `MotherAIManager` — corrigir pelo código são 25 strings, contra 664 linhas se fosse pelos `.lang` nos dois idiomas.

O insulto exigiu tratamento distinto: é a única família que embute a tier no **nome** da chave (`insult_hostile.1`, `insult_friend.1`) em vez de recebê-la como segmento separado. Passou a usar a sobrecarga de 2 argumentos com a tier concatenada na base:

```java
getRandomVariant("npc-interactions.insult_" + tier.translationKey, 5)
```

Após a correção a auditoria acusa zero chaves quebradas. A única referência restante, `server.npc.npc.isBusy`, é nativa do Hytale (definida no `server.lang` do `HytaleServer.jar`).

---

## 10. Cama Nunca Registrada ao Ser Colocada

### Sintoma
Em mundo novo, colocar uma cama não produzia efeito nenhum: nenhuma NPC a reivindicava, nenhuma
dormia, e o `/simtale debugbeds` mostrava a lista vazia. Em mundos antigos tudo funcionava, o que
fazia o problema parecer intermitente.

### Diagnóstico (Causa Raiz)
`BedPlaceBlockEventSystem.handle()` registrava baú, plantação e terra arada — **mas não cama**. As
únicas coisas que povoavam o `BedRegistry` eram o `BedEntityRegistrySystem` (só camas que são
entidade, não bloco), a restauração de NPC persistida, e `BedWorldBootstrap.bootstrapLoadedRadius`,
que era chamado **apenas de dentro do `/simtale housecheck`** e da página de debug, como efeito
colateral.

Mundos antigos funcionavam porque alguém já tinha rodado o `housecheck` lá, e os registros são
`static`: o estado sobrevivia na memória da JVM e vazava até de um mundo para outro.

O sintoma "só aparece depois de reiniciar o mundo" era a mesma causa vista de outro ângulo.

### Resolução
*   Registro de cama adicionado ao evento de colocar, via `BedWorldBootstrap.registerBedAt`.
*   `registerBedAt` extraído para que a varredura e o evento usem **o mesmo** código, evitando que
    divirjam com o tempo.
*   `PlayerJoinHandler` passou a rodar a varredura (raio 32, com 2 s de atraso para os chunks
    carregarem), cobrindo mobília que já existia antes do servidor subir.
*   O log da varredura subiu para nível `info`.

### Lição
Um comando de diagnóstico que também corrige o estado esconde a falha que deveria expor.

---

## 11. Baús Invisíveis para as NPCs (Classificação por Nome)

### Sintoma
Com três baús colocados na casa, o `/simtale chestcheck` respondia que nenhum estava registrado, e
a NPC com fome ignorava baú cheio de comida.

### Diagnóstico (Causa Raiz)
`ChestRegistry.isChestId` exigia que o id do bloco contivesse `chest`, `barrel`, `cupboard` ou
`cabinet`. Qualquer bloco de armazenamento nomeado de outra forma era ignorado silenciosamente.

O mesmo erro estava em `HouseManager.isChest`, com uma consequência pior: os baús não entravam no
`interior` da casa, então o `BLOCK_TO_HOUSE_ID` não tinha entrada para eles e o `canOpenChest`
recusava todos.

### Resolução
`ChestRegistry.isContainerAt` pergunta ao motor se o bloco tem `ItemContainerBlock` — o **mesmo**
componente que as NPCs já leem para procurar comida, de modo que registro e consumo não podem mais
discordar. O nome ficou apenas como fallback, marcado como não confiável.

### Lição
Terceira ocorrência do mesmo padrão no projeto (junto de `Root_Secondary_Consume_Food_T*` para
comida e dos caminhos de modelo de criança): **classificar por nome falha silenciosamente**.
Sempre que houver um dado do motor que responda a pergunta, use o dado.

---

## 12. Morte por Fome Instantânea Tornava a Inanição Decorativa

### Sintoma
A NPC morria no instante em que a barra de fome chegava a zero, independentemente da vida. O dano
de inanição e a cura ao comer não tinham efeito prático nenhum.

### Diagnóstico (Causa Raiz)
`RoutineAISystem` disparava `TaskType.DYING` em `npc.needs.hunger <= 0`, um teste anterior ao
sistema de inanição e que nunca foi removido quando ele chegou.

Junto disso, nenhuma das duas interrupções (sono e fome) excluía os estados `DYING`/`DEAD`/
`REAPING`: a NPC entrava em `DYING`, era arrancada de lá no mesmo tick, o teste disparava de novo no
tick seguinte, e o aviso de morte repetia para sempre sem ela nunca morrer.

### Resolução
*   A morte passou a vir do dano acumulado (`Needs.starvationDamage >= 200`, igual ao `MaxHealth`
    dos roles), o que dá as duas horas pedidas e faz a cura por comida importar.
*   O contador vive em `Needs` porque `Needs` é persistido, então a contagem sobrevive ao relog.
*   Comer zera o contador.
*   As duas interrupções passaram a respeitar o fluxo de morte.

### Nota técnica
O contador existe porque a RuneCore expõe `addHealth`/`subtractHealth` mas não um getter de vida
confiável. O custo assumido é que uma NPC ferida por outra causa não morre de fome mais cedo.

---

## 13. Qualquer Entidade Virava NPC do Mod

### Sintoma
Apertar F numa vaca abria o painel de interação de NPC. Mais grave: o jogador ficava preso na cama
sem conseguir sair, nem em modo criativo.

### Diagnóstico (Causa Raiz)
O caminho de re-anexar NPC depois de recarregar o mundo não tinha filtro algum. Ele lia o
`UUIDComponent` (que toda entidade tem), inventava um nome quando não havia registro salvo, e
executava `addComponent(targetRef, SIM_NPC_COMPONENT_TYPE, npc)`.

Como a query do `RoutineAISystem` é exatamente esse componente, a entidade adotada passava a rodar
a rotina de aldeão — ir para a cama, ser montada, receber `Frozen`. Aplicado a um jogador, isso
produz exatamente um jogador preso na cama que o criativo não solta, porque o criativo não remove
`MountedComponent`.

O código estava **duplicado em dois handlers**: `SimTaleEventHandler` (clique direito) e
`SimTaleUseNPCInteraction` (tecla F). Corrigir apenas o primeiro não teve efeito visível, porque o
teste foi feito com F.

### Resolução
Nos dois handlers:
*   Jogador nunca é adotado.
*   Só entidade com registro no shell `simtale` é re-anexada — sem registro, não há o que remontar.
*   O painel não abre para entidade adotada (marcador: `gender == null`, já que
    `SimNPCFactory.spawnNPC` sempre define gênero e o caminho de adoção nunca definia).
*   `/simtale forget` solta as adotadas **e apaga o registro**, porque o `SimTaleTickSystem`
    re-anexa qualquer entidade que ainda tenha registro quando o chunk carrega.
*   `/simtale unstick` passou a soltar também o próprio jogador.

### Lição
A busca que resolveu foi `grep addComponent(SIM_NPC_COMPONENT_TYPE)`. Antes de dar um problema por
corrigido, procure **todas** as ocorrências do padrão — código duplicado significa correção
duplicada.

Vale também a comparação com o `PlumbobSystem`, que já tinha o filtro certo em duas camadas: a
query (`Query.or`) como filtro grosso de performance, e uma checagem explícita dentro do `tick` como
regra de correção. Handlers de evento não têm query, então dependem inteiramente da checagem — e não
tinham nenhuma.

---

## 14. Referência de Entidade Inválida Repetida em Três Sistemas (RoutineAISystem, `/simtale setmood`, BabyCareTickSystem)

### Sintoma
Falha ocasional do servidor com `IllegalStateException: Invalid entity reference!`, disparada em
momentos diferentes: ao a NPC procurar parceiro de conversa (`RoutineAISystem`), ao rodar
`/simtale setmood` perto de uma NPC, e durante o tick de cuidado de bebês (`BabyCareTickSystem`).

### Diagnóstico (Causa Raiz)
Os três sistemas percorrem `SimTale.ACTIVE_NPCS` procurando a NPC mais próxima ou um parceiro
válido, e todos os três só verificavam `entityRef == null` (ou `!= null`) antes de ler componentes
daquele ref. Um `entityRef` pode continuar não-nulo mas já estar inválido — a entidade foi
removida (morte, despawn, descarregamento de chunk) sem que `ACTIVE_NPCS` tivesse sido podada
ainda — e ler `TransformComponent` (ou qualquer outro componente) desse ref lança a exceção.

### Resolução
Acrescentado `&& entityRef.isValid()` à condição de descarte nos três loops:
*   `RoutineAISystem` — busca de parceiro de socialização.
*   `SimTaleCommand` — subcomando `setmood`, busca da NPC mais próxima do jogador.
*   `BabyCareTickSystem` — busca do cônjuge NPC do jogador portador do bebê.

### Lição
Mesmo padrão do item 13: um `grep` por `entityRef == null` (ou `!= null`) no restante do projeto
logo depois de achar o primeiro caso teria revelado os outros dois de uma vez, em vez de um crash
de cada vez em produção.

---

## 15. PlumbobSystem — Remoção Dupla por Corrida entre Despawn Manual e Varredura de Órfãos

### Sintoma
Crash do mundo inteiro com `IllegalStateException: Invalid entity reference!` ao esconder o
plumbob de uma criança carregada no colo, ou quando o dono de um plumbob montava um veículo,
virava Ceifador, ou saía em expedição.

### Diagnóstico (Causa Raiz)
`despawnPlumbob` remove o ref de `trackedPlumbobRefs` e enfileira `commandBuffer.removeEntity` no
mesmo instante, mas o `CommandBuffer` só aplica a remoção no fim da passada de tick inteira — a
entidade continua "presente" para o resto do sistema até lá. Esse mesmo ref também bate na query
do próprio `PlumbobSystem` via `PersistentModel`. Se ele for revisitado pela varredura de órfãos
(topo do `tick()`) antes de o buffer aplicar a primeira remoção, `trackedPlumbobRefs` já não o
contém, a varredura o lê como órfão e enfileira uma **segunda** `removeEntity` para o mesmo ref. O
`CommandBuffer` aplica as duas em ordem: a primeira invalida o ref, a segunda lança a exceção,
derrubando o mundo inteiro.

### Resolução
Novo conjunto `pendingDespawns` (`ConcurrentHashMap.newKeySet()`) em `PlumbobSystem.java`:
`despawnPlumbob` registra o ref ali antes de enfileirar a remoção, e a varredura de órfãos passa a
tentar `pendingDespawns.remove(thisRef)` primeiro — se o ref estava lá (removido com sucesso),
ele já tem uma remoção enfileirada e a varredura não enfileira outra.

---

## 16. Criança Carregada é Solta Automaticamente Durante o Dia (Conflito Sono/Montaria)

### Sintoma
Ao pegar uma criança no colo (sistema de montaria usado para carregá-la no ombro), o jogador via a
mensagem de sucesso no chat, mas a criança nunca aparecia visualmente montada — o efeito parecia
não ter acontecido, sem nenhum erro no log.

### Diagnóstico (Causa Raiz)
`SimTaleTickSystem.processMountedSleepingNPCs` usava a simples presença de `MountedComponent`
(`mounted != null`) como um segundo gatilho de entrada para a lógica de "a NPC acordou", ao lado do
gatilho correto (`ai.currentTask == SLEEPING/WAKING`). Como `MountedComponent` é o mesmo componente
genérico da engine usado tanto para "dormindo numa cama"/"sentada numa cadeira" quanto para "sendo
carregada no colo" — é o único lugar do código que cria essa segunda situação — qualquer pickup
feito fora do período noturno (`sleepPeriodClosed == true`, verdadeiro na maior parte do dia)
satisfazia esse gatilho já no tick seguinte, disparando
`commandBuffer.tryRemoveComponent(ref, MountedComponent.getComponentType())` e desfazendo a
montaria cerca de 30ms depois de criada — tempo curto demais para o cliente chegar a desenhar a
criança no ombro do jogador.

### Resolução
Separado o efeito colateral desejado (reposição de energia da criança enquanto carregada, já que
sua rotina normal fica suspensa no colo) da lógica de acordar. O top-up de energia continua
disparando incondicionalmente sempre que `MountedComponent` está presente, mas o restante da lógica
de "acordar" (remover a montaria, resetar a tarefa, teleportar de volta para a cama) agora exige
explicitamente `ai.currentTask == SLEEPING || ai.currentTask == WAKING` — o único caso em que
`MountedComponent`, nesse sistema, de fato representa "dormindo" em vez de "no colo".
