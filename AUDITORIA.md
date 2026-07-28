# Auditoria SimTale

Varredura completa de `src/main/java` (111 arquivos, ~13.9k linhas). Este documento lista
o que foi **corrigido** e o que ficou **pendente** com arquivo e linha.

Não foi possível compilar aqui (o projeto exige JDK 21+ e o ambiente só tem JRE 11).
A verificação foi estática: balanceamento estrutural de todos os arquivos, checagem de que
todo `LOGGER` usado tem declaração, que os `return` novos estão em métodos `void`, e
conferência das assinaturas reais no bytecode do `HytaleServer.jar`
(`ItemContainer.canAddItemStack`, `ItemStack(String,int)`, `getQuantity():int`).
**Compile antes de subir em servidor de verdade.**

---

## 1. Bugs corrigidos

### 1.1 O Grim Reaper nunca aparecia
`RoutineAISystem.java:145` procurava o reaper com `other.name.contains("Reaper")`, mas
`SimNPCFactory` nomeia ele **"Dona Morte"**. A condição jamais era verdadeira, então nenhum
NPC morto era colhido — o corpo ficava travado em `TaskType.DEAD` para sempre.

Agora existe `SimNPCComponent.isReaper`, setado na factory e testado no despacho.

### 1.2 O provider Gemini nunca funcionou
`GeminiProvider.transform()` montava um `AiProviderConfig` local que era **descartado**,
zerava `systemPrompt` e `messages`, e enfiava o `contents` real dentro do mapa de metadata.
O corpo que ia para a API era literalmente `{"contents": ""}` mais um campo `metadata`
inválido. Toda requisição era rejeitada.

Reescrito: o formato agora é declarado uma vez na config via `promptStructurer`
(`AiProviderConfig.java`), e `GenericHttpAiProvider` aplica.

### 1.3 O provider OpenAI/OpenRouter provavelmente também falhava
`GenericHttpAiProvider.buildJsonPayload` sempre anexava `"metadata": {...}` ao corpo. O
metadata do SimTale carrega inteiros (`friendship`, `romance`); a API da OpenAI exige
strings e rejeita o tipo errado com HTTP 400. Também emitia um `systemPrompt` de topo
desconhecido. Ambos agora são condicionais (`sendMetadata`, off por padrão).

### 1.4 `customModel`/`customUrl` vazavam entre providers
`SimTale.java:81-99` aplicava o mesmo `customModel` aos três providers. Configurar um
modelo de OpenRouter também mandava aquele id para o Gemini e para a OpenAI, quebrando os
dois. Agora o override só vale para o provider selecionado em `provider`.

### 1.5 `GrowthTickSystem` expulsava a criança errada
`GrowthTickSystem.java:97` fazia `ACTIVE_CHILDREN.remove(i)` **depois** de
`onBecameAdult()` já ter feito `remove(child)`. Os índices tinham deslizado, então a
remoção por índice eliminava uma criança diferente, ainda em crescimento — ela parava de
crescer silenciosamente. Trocado para remoção por identidade.

### 1.6 Perda de itens ao depositar no baú
`NPCWorkHelper.java:280-286` removia o item do inventário do NPC e **depois** entregava a
referência já esvaziada ao baú. Com baú cheio, o loot sumia. Agora copia, checa
`canAddItemStack`, deposita, e só então limpa o slot.
(No mesmo bloco havia um `cb.getItemContainer();` duplicado com o resultado descartado.)

### 1.7 Registro antigo apagava a personalidade do NPC
`SimNPCPersistence.applyData` atribuía `personality`, `needs` e `stats` sem checar null —
os outros campos eram todos protegidos. Um registro parcial zerava `personality` e todo
`npc.personality.traits` seguinte estourava NPE. Agora são condicionais, e `traits` nulo
vira um set vazio.

### 1.8 `clone()` perdia os traços e compartilhava relacionamentos
`SimNPCComponent.clone()` construía uma `Personality` nova sem copiar `traits` — o clone
saía sem `GREEDY`/`SHY`/`LAZY`. E `new HashMap<>(relationships)` é cópia rasa: os objetos
`Relationship` eram compartilhados, então mexer na afinidade do clone mexia na do original.
Ambos corrigidos.

### 1.9 `BedRegistry.addOrReplace` nunca substituía
O laço de dedupe (raio de 2 blocos) dava `return` para **qualquer** cama próxima, inclusive
a da posição exata — o `removeIf` + `add` logo abaixo era código inalcançável. O nome do
método mentia e o `yaw` de uma cama nunca podia ser corrigido. Reestruturado: posição exata
substitui, posição vizinha é ignorada como segunda metade da cama.

### 1.10 `NoSuchElementException` em cinco pontos
`Universe.get().getWorlds().values().iterator().next()` estourava sempre que rodava sem
mundo carregado (boot, unload, shutdown): `BabyCareTickSystem:57,66`,
`LifecycleUtils:68`, `GrowthManager:111,156,172`. Criado `core/WorldUtil.java` que devolve
`null`, e os chamadores checam.

### 1.11 `assert` usado como validação (12 ocorrências)
`assert x != null` é **removido em runtime** sem `-ea`, que é o padrão da JVM. Não eram
checagens, eram comentários caros. Substituídos por checagem real em `SimTaleCommand` (5),
`SimDebugCommand`, `NPCInteractionPage`, `NPCPregnancyPage`, `SimTaleUseNPCInteraction` (2),
`PlumbobSystem`, `NpcContextBuilder`.

Em `NpcContextBuilder` o efeito era pior: o `assert` ficava dentro de um
`catch (Throwable ignored)`, então o idioma do jogador falhava em silêncio e caía sempre
para `en-US`.

### 1.12 `SocialStats.addXP` só subia um nível
Usava `if` em vez de `while`; um ganho grande de XP deixava o NPC parado acima do limiar
até a próxima interação.

### 1.13 Outros
- `SimTaleEventHandler:212` logava em **INFO a cada clique direito de cada jogador** — flood
  garantido no console. Removido.
- `UUID.fromString` sem guarda em metadata de item de bebê (`SimTaleEventHandler`) →
  exceção estourando do handler de clique. Agora ignora o item e loga.
- `Needs.tickDecay` estourava NPE com `traits` nulo vindo da desserialização.
- `HouseManager.canOpenChest` estourava NPE com `npcId` nulo, abortando a varredura de fome.
- 6 `printStackTrace()` → logger (`SimTaleTickSystem`, `AiConfigManager`, `MagicDataLoader`).
- 18 `System.out.println` → logger (registries, bootstrap, `NPCWorkHelper`, `RoutineAISystem`).
- `catch (Throwable ignored)` vazio em `InteractionManager:563` → `RuntimeException` (o
  original engolia `OutOfMemoryError` e `StackOverflowError`).
- `MagicDataLoader` cacheava `null` quando o Gson devolvia nulo, reparseando o JSON a cada
  chamada.
- `Thread.sleep` em `SimTaleChatHandler.sendReply` engolia o `InterruptedException` sem
  restaurar a flag.
- `.exceptionally()` adicionado no future da IA em `InteractionManager` — exceções sumiam
  sem rastro nenhum.
- `getProfession()` no chat não reconhecia `construtor` nem `caçador`, apesar de
  `BUILDER`/`HUNTER` existirem no enum e estarem implementados em `NPCWorkHelper`.
- Duas cópias da lógica "isto é um baú?" (`HouseManager.isChest` e `ChestRegistry.isChestId`)
  — a primeira agora delega.
- Regex recompilada duas vezes por NPC a cada mensagem de chat (`findTargetNpc`) —
  precompilada em `Pattern` estático, e a mensagem passa a ser dividida uma vez só.

---

## 2. Pendente — decisão sua

### 2.1 Performance dos ticks (o mais sério)

**`PlumbobSystem.getQuery()` devolve `UUIDComponent`** (`PlumbobSystem.java:50`). Isso é
*toda entidade do mundo*, todo tick — cada item no chão, cada projétil. E para cada uma
faz `playerPlumbobs.containsValue(thisRef)`, que é O(n) sob lock. Deveria consultar
`SIM_NPC_COMPONENT_TYPE` + `Player`, e o mapa deveria ser bidirecional.

**Busca de banho: ~10.500 leituras de bloco por NPC a cada 40 ticks**
(`RoutineAISystem.java:498-523`), cada uma alocando um `Vector3i` novo e chamando
`getId().toLowerCase()` — mais 10 mil strings temporárias. Vale cachear blocos de água num
registry, como já é feito com camas e plantações.

**Varreduras lineares O(n²) por tick:**
- `SimTaleTickSystem:94-100` percorre `ACTIVE_NPCS` inteiro para cada NPC. Um `Map<UUID,
  SimNPCComponent>` resolve.
- `RoutineAISystem:81` percorre `ACTIVE_CHILDREN` inteiro para cada NPC.
- `RoutineAISystem:646-652` monta um `HashSet<String>` com concatenação de coordenadas a
  cada busca de cama.
- `ConstructionSystem:62-75` percorre todos os NPCs para cada canteiro.

**Picos de I/O sincronizado:** `SimTaleTickSystem:111` faz `absoluteTick % 600 == 0`, então
*todos* os NPCs gravam em disco no mesmo tick. Espalhe com
`(absoluteTick + entityId.hashCode()) % 600`. E `InteractionManager` chama `saveNPC()` a
cada interação.

### 2.2 Concorrência

`InteractionManager:158` e `SimTaleChatHandler.sendReply` mandam mensagem e leem
componentes a partir de threads do `ForkJoinPool`. `SimTaleUseNPCInteraction` já mostra o
jeito certo (`world.execute(...)`). Se o engine do Hytale não for thread-safe, isso é
corrupção de estado intermitente — o tipo de bug que só aparece com servidor cheio.

O `SimTaleChatHandler` também escreve em `npc.currentConversationPartner`, `npc.profession`
e `npc.currentJob` a partir da thread de evento, enquanto os tick systems leem os mesmos
campos.

### 2.3 Persistência de estado no `RoutineAISystem`

O método termina com `commandBuffer.replaceComponent(ref, ROUTINE_AI_COMPONENT_TYPE, ai)`
(linha 637), mas há **10 `return` antecipados** que pulam essa linha (127, 153, 156, 292,
308, 348, 425, 444, 530, 559, 599). Ou a chamada final é desnecessária (mutação já é
in-place) ou essas saídas perdem a mudança de estado. Não dá para as duas coisas serem
verdade — vale definir e padronizar.

### 2.4 Falsos positivos na classificação de intenção do chat

`ChatIntent.detect` usa `contains()` em fragmentos curtos:
- `"fei"` → **INSULT** para "feito", "feira", "feijão", "confeitaria"
- `"top"` → **COMPLIMENT** para "topo", "estopa"
- `"gat"` → **COMPLIMENT** para "gato", "gatilho"
- `"some"`, `"ruim"`, `"lixo"` disparam INSULT em contextos inocentes

E `INSULT_CHAT` só devolve uma frase: não aplica penalidade de relacionamento nem grava
memória, ao contrário do `InteractionManager`. Insultar pelo chat não tem consequência.

### 2.5 Estados de tarefa sem timeout

`WANDERING` e `MOVING_TO_SOCIALIZE` têm timeout. `MOVING_TO_BATH`, `MOVING_TO_WORK`,
`MOVING_TO_DEPOSIT`, `MOVING_TO_CONSTRUCTION` e `BATHING` não têm. Se o destino for
inalcançável, o NPC fica preso até a interrupção de energia baixa arrastá-lo para a cama.

### 2.6 Inconsistências menores

- **Ids de bloco:** `NPCMovementHelper.isStandable` compara com `"Empty"`, `HouseManager.isSolid`
  com `"empty"`/`"air"`, e `NPCWorkHelper` escreve `"hytale:empty"`. Se o id real tiver o
  prefixo de namespace, `isStandable` sempre devolve false e o NPC tenta entrar na cama pela
  posição da própria cama.
- `CropRegistry.isCropId` é case-sensitive; os outros registries usam `toLowerCase`.
- `NPCPreferences.PROFESSION_POOL` não inclui `HUNTER` — ninguém pode gostar ou desgostar de caçador.
- `HouseManager.isSolid` devolve `false` para bloco nulo (chunk descarregado), então o
  flood fill 3D **escapa pela área não carregada** e a casa é reprovada como
  `TOO_LARGE_OR_UNENCLOSED`.
- `registerHouse` não desindexa a casa anterior nas mesmas posições → entradas obsoletas em
  `BLOCK_TO_HOUSE_ID`.
- `OWNER_TO_HOUSE_ID` é preenchido e nunca lido.
- `refreshDailyState` chama de "dia" um bloco de 20 minutos (`System.currentTimeMillis() / 1200000L`).
- `InteractionManager:126` usa o formato dos dados como sentinela de erro (`memoryEvent == null
  && friendship == 0 && affinity == 0`). Qualquer interação legítima com efeito zero é
  descartada por acidente. Um campo booleano explícito no record seria mais seguro.
- Prompt da IA misturando idiomas: `mood.ptName` e `"há X segundos atrás"` em português
  dentro de um prompt em inglês (`NpcContextBuilder`). "há ... atrás" também é redundante.
- `Message.raw` com português fixo convivendo com `Message.translation` no mesmo arquivo
  (`SimTaleTickSystem:237`, `GrowthManager`, `SimTaleEventHandler` — que ainda tem strings
  em inglês junto).
- `SimTaleEventHandler` fixa `prefabName = "TavernHouse"` para qualquer blueprint.
- `Personality.createDefault()` quebra se `Trait` tiver menos de 3 valores.
- `Relationship.updateStatus()`: `romance > 20` vira `CRUSH` antes de checar amizade, então
  um `BEST_FRIEND` com romance baixo é rebaixado para `CRUSH`.

---

## 3. Arquivos alterados

40 arquivos modificados, 1 novo (`core/WorldUtil.java`). Nada commitado — `git diff` mostra
tudo, `git checkout -- src/` reverte.
