# SimTale — Roadmap

Classificação por esforço real, feita depois de conferir o que já existe no código.
Cada item traz **por que** caiu naquela categoria e **onde encosta** no projeto.

| Categoria | Significado |
|---|---|
| 🟢 **Esta semana** | Encaixa em sistema que já existe. Mexe em poucos arquivos. |
| 🟡 **Próximas atualizações** | Precisa de sistema novo ou toca várias partes. |
| 🔴 **Futuro distante** | Depende de coisa que o mod (ou o Hytale) ainda não tem. |

---

## ⚠️ Três coisas que precisam ser ditas antes

Descobertas ao conferir o código para montar este roadmap. Elas mudam o escopo de itens que na
lista original pareciam pequenos.

### 1. Function Calling não existe — não é "revisar", é "construir"

A lista pede *"verificar se o Function Calling está funcionando corretamente"*. Ele não está
quebrado: **não existe**. Uma busca por `tool` / `function` em `ai/AiRequest.java`,
`ai/GenericHttpAiProvider.java` e `ai/providers/*.java` não retorna nada. O que existe hoje é
um pipeline de texto puro: monta prompt → manda → recebe string → mostra no chat.

Isso reclassifica o bloco inteiro de **"Ferramentas da IA"** de "revisar" para "implementar do
zero", e é o maior item de todo o roadmap. Detalhes na seção 🟡.

### 2. Relacionamento NPC↔NPC — ✅ implementado nesta sessão

Isto descrevia uma lacuna que já foi fechada: `NPCSocialHelper.applyChatOutcome` agora escreve de
verdade em `SimNPCComponent.relationships` a cada conversa espontânea (`bondNpcs` para
amizade/afinidade/confiança, `tryCourtship` para romance e, quando os dois lados cruzam o mesmo
patamar do pedido de casamento do jogador, casamento autônomo). Ver "Relacionamentos entre NPCs" na
seção ✅ Feito abaixo e `docs/SISTEMA_RELACIONAMENTOS.md` para os números exatos.

### 3. A casa é identificada pela cama — ✅ implementado nesta sessão
A casa agora possui identidade própria com UUID e âncora centróide independente, suportando um conjunto de camas (`Set<HouseBlockPos> beds`). A quebra de uma cama realoca dinamicamente o morador para outra cama da mesma casa ou o deixa em busca de cama livre, sem dissolver o imóvel, seus donos, portas ou baús.

---

## 🟢 Esta semana

### ~~Contexto da IA — os campos que faltam~~ ✅ FEITO (15/09)
**Onde**: `ai/NpcContextBuilder.java`

O prompt já montava gênero, personalidade, humor, profissão, trabalho atual, necessidades (fome,
energia, social, diversão, higiene), preferências, hobby, família e o relacionamento com o
jogador. Agora também manda, lendo dado que já existia em outro lugar do mod — nenhum sistema
novo:

| Campo | Fonte |
|---|---|
| Localização | `TransformComponent` |
| Vida | `EntityStatMap` + `DefaultEntityStatTypes.getHealth()` |
| Casa | `HouseManager.OWNER_TO_HOUSE_ID` (só tem/não tem — `HouseData` não guarda nome) |
| Players próximos | raio de 20 blocos, igual ao que `RoutineAISystem.SOCIALIZE_SEARCH_RANGE_SQ` já usa pra achar parceiro de conversa |
| Horário | `world.getTick()` |
| Relacionamentos com outros NPCs | `SimNPCComponent.relationships`, via `LifecycleUtils.findNPCById` |

Compilado limpo (161 arquivos, contra as três libs). Puramente aditivo — nenhum campo antigo
mudou, só apareceram linhas novas no prompt. Detalhes e reteste sugerido em
`testing_checklist.md`, seção 14.

**Ficam de fora**: *clima* (preciso achar a API de weather do servidor), *inventário resumido*
(depende do inventário compartilhado, ver 🟡) e *eventos recentes* (depende de um log de eventos
que não existe).

### ~~Portas — detecção e abertura~~ ✅ FEITO
**Onde**: `systems/NPCDoorHelper.java` (substituiu `HouseDoorManager`, removido)

Eram dois bugs, e qualquer um sozinho já anulava o sistema: o estado "aberto" era calculado por
manipulação de string e saía **idêntico** ao fechado (nenhuma porta abria, para NPC nenhum), e a
busca só olhava portas da casa registrada do próprio NPC. Agora delega para `DoorBlockUtils` +
`DoorInteraction.getDoorAtPosition` do motor. Detalhes em
`docs/sistemas/reconhecimento-casas.md`.

**Ainda em aberto**: integração com pathfinding. O NPC abre a porta quando chega perto, mas se o
pathfinder trata porta fechada como intransponível, ele pode nunca traçar a rota que passa por
ela. Precisa de teste em jogo para saber se é problema real.

**Bug concreto, achado em teste (bug #5, `testing_checklist.md` seção 2)** — ✅ CORRIGIDO (15/09):
duas NPCs usando a mesma porta ao mesmo tempo ainda conflitavam — uma fechava enquanto a outra
estava abrindo. A troca do cone de 60° por uma checagem geométrica (`DoorBlockUtils.isInFrontOfDoor`)
melhorou bastante mas não eliminava a corrida sozinha, exatamente como esta seção já previa: era
preciso o lock por porta descrito acima. Implementado reaproveitando o próprio `OPENED_DOORS`
como trava (`putIfAbsent` reivindica a porta atomicamente antes de decidir o que fazer com ela, em
vez de decidir e só depois marcar) — sem mapa/objeto novo. Detalhes e retest pendente em
`testing_checklist.md`.

### ~~Comportamento ocioso — NPC olhando para parede~~ ✅ FEITO (15/09)
**Onde**: `systems/RoutineAISystem.java`, estado `IDLE`

Mesma família dos ajustes de rotação/animação já feitos na câmera de interação
(`faceConversationPartner`) e na saudação de proximidade (`checkPlayerProximityGreeting`) — os
dois já viravam a NPC para encarar alguém com `teleportRotation(new Rotation3f(0f,
atan2(-dx,-dz), 0f))`.

A causa: esse giro só acontecia dentro do cooldown de 45s da própria saudação. Fora dele, uma NPC
`IDLE`/`WANDERING` ficava com a rotação que sua última tarefa deixou — inclusive de frente pra
parede — não importa se tinha jogador do lado. `faceNearbyPlayerWhileIdle` desacopla o giro do
cooldown: roda para as duas tarefas "livres" (mesmo par que `NPCSocialHelper.isAvailableToTalk`
já usa), a ~2x por segundo por NPC (escalonado pelo id da entidade, não todo tick, pra não
multiplicar o scan de players por NPC ociosa no servidor).

Compilado limpo (161 arquivos, contra as três libs). Zero campo novo, zero sistema novo — só mais
uma chamada à mesma fórmula de rotação que já existia duas vezes. Detalhes e reteste sugerido em
`testing_checklist.md`, seção 7.

### ~~Fome — revisão~~ ✅ FEITO
**Onde**: `systems/NPCFoodHelper.java` (novo), `NPCHungerHelper`, `RoutineAISystem`

Classificação de comida passou a usar o dado do item (`isConsumable` + o tier
`Root_Secondary_Consume_Food_T1..T3`) em vez do nome. A heurística antiga aceitava
`Plant_Crop_Mushroom_Cap_Brown` (não é comestível) e rejeitava `Ingredient_Dough` (é).

Escolha agora ordena por **tier → gosto → distância**: comida preparada antes de crua, favorita
antes de odiada. Antes pegava o primeiro slot do baú mais próximo.

Comer restaura fome (25/45/65) e vida (6/14/24, via `StatHelper` do RuneCore).

~~Fome abaixo de 5 tira vida, calibrada para 2 h de vida cheia até a morte.~~ **Correção
(15/09): essa frase estava errada.** Fome baixa NÃO tira vida — checado o `NPCHungerHelper`
inteiro e todo `subtractHealth`/`addHealth` do projeto, e o único efeito de fome abaixo de 5 é
`tickStarvation` forçar `IDLE` + chorar + humor `SAD`. É decisão de design **deliberada**, já
registrada em `testing_checklist.md` seção 11 (09/08): existia um gatilho `fome <= 0 → DYING` que
matava na hora (oposto do pretendido) e foi removido de propósito, junto com as constantes de dano
por fome (`STARVATION_DAMAGE` etc.) que não sobreviveram à migração para `EntityStats`. Aging e
doença é que vão ser causa de morte; fome só custa utilidade. Ver item abaixo.

Interrupção por fome adicionada, espelhando a do sono — antes a fome só era checada em `IDLE`, e
uma NPC sempre ocupada nunca comia. Com cooldown (`nextFoodSearchTick`) para não repetir a
tempestade de buscas que a cama teve.

**Ficou de fora, de propósito**: baú do mundo continua ignorado (decisão de design — a NPC só usa
o que o jogador colocou).

### ~~Morte por fome — falta confirmar~~ ✅ Esclarecido (15/09)
**Onde**: `NPCHungerHelper.tickStarvation` → `RoutineAISystem` (`DYING` / `REAPING`)

A pergunta partia de uma premissa que não existe no código: fome não chama `subtractHealth` em
lugar nenhum (busca no projeto inteiro, zero ocorrências ligadas a fome — os únicos
`subtractHealth` são o custo de 50 HP do parto em `PregnancyManager`). Fome nunca chega a 0 HP,
então não há "morte por fome" pra verificar — é `tickStarvation` que faz o trabalho todo, e ele só
mexe em humor/tarefa, nunca em vida (ver item "Fome — revisão" acima, corrigido no mesmo dia).

Boa notícia à parte, sobre a pergunta em si (o motor mata direto e pula o fluxo do SimTale?):
`RoutineAISystem.tick()` já tem, hoje, uma checagem genérica no topo (linhas ~308-320) que lê
`EntityStatMap`/`DefaultEntityStatTypes.getHealth()` de qualquer NPC fora de
`DYING`/`DEAD`/`REAPING`/`EXPEDITION` e, achando `<= 0`, entra em `DYING` na hora — comentário no
próprio código diz que foi feita pro dano de combate real (arma de jogador), que antes não
disparava o fluxo de morte nenhum. Ou seja: **qualquer** causa de 0 HP diferente de fome (combate,
complicação de parto) já cai corretamente no `DYING → DEAD → REAPING`. A única ressalva pequena:
como `EXPEDITION` está na lista de exceções, uma NPC que morresse de combate durante uma expedição
(caçador/minerador, ~2 min, modelo reduzido) só entraria em `DYING` depois que a expedição
"terminasse" e desse a recompensa — cenário bem improvável (a NPC fica reduzida/fora de alcance
durante a expedição) e não relacionado a fome, então não mexi nisso agora.

**Isso deixa uma decisão de verdade pra tomar, não um bug**: fome continua não devendo matar
(mantém a decisão de 09/08), ou vocês querem reintroduzir dano por fome agora que o roteamento
genérico pra `DYING` existe e funcionaria de graça? Não decidi sozinho — é mudança de balanceamento
do jogo, não correção de bug, e a nota de 09/08 tinha um raciocínio de design explícito por trás
(duas causas de morte competindo complicam as duas).

### ~~Reputação por profissão~~ ✅ FEITO (15/09)
**Onde**: campo novo em `SimNPCComponent` + persistência + `NpcContextBuilder`

Contar trabalhos concluídos por profissão e expor "melhor pescador da vila" no contexto. O
sistema de trabalho já marca `currentJob`, então era só acumular — e havia um único ponto para
fazer isso: `NPCWorkHelper.applyWorkSatisfaction`, já chamado pelos 5 lugares que fecham um ciclo
de trabalho (colheita, plantio, pesca, corte, expedição). `jobsCompleted` persiste pelo mesmo
caminho dos campos de emoção (`SimNPCData` + `SimNPCPersistence`), então sobrevive a restart —
sem isso "melhor da vila" reiniciaria do zero toda vez e não significaria nada.

Ficou 🟢 como esperado: escopo é só número exibido no prompt + uma frase quando a NPC lidera sua
profissão entre as carregadas no momento (e só conta como "lidera" se tiver alguém pra comparar —
ser a única da profissão não gera a frase). Nenhum comportamento muda; é decoração de contexto,
igual aos outros campos desta seção.

Compilado limpo (161 arquivos, contra as três libs). Detalhes e reteste sugerido em
`testing_checklist.md`, seção 14.

---

### Itens sem comportamento: Sino da Vila e Bolo de Aniversário
**Onde**: `SimTaleItemRegistry.java` / `RuneCoreItemManager`

Achado ao revisar `testing_checklist.md` seção 16: dos 7 itens que só existiam como objeto
(modelo, textura, receita, nome traduzido) sem nenhum comportamento no clique, 4 já foram
implementados (Planta da Casa, Registro do Estalajadeiro, Luneta do Almoxarife, Diário do
Inspetor). Faltam **Sino da Vila** e **Bolo de Aniversário** — o **Contrato de Imigração** já
tem comportamento (crescimento de população, ver seção 0 do checklist), então já pode sair
dessa lista.

Sino da Vila e Bolo de Aniversário precisam de comportamento novo de verdade (não é só ligar a
peça que já existe, como foi com os outros 4) — por isso 🟡, não 🟢.

### Mensagem de afinidade ao nascer devia distinguir "sem filhos" de "filho(s) longe"
**Onde**: `FamilyBonds` / o comando ou fluxo que reporta o vínculo pai-filho ao nascer

Achado junto com a confirmação de que `FamilyBonds` grava afinidade certa nos dois sentidos ao
nascer (`testing_checklist.md` seção 10): a mensagem hoje não diferencia "não há filhos no
mundo" de "há, mas nenhum por perto". Puramente cosmético — não muda o cálculo de afinidade,
só a clareza da mensagem.

---

## 📣 Trabalho extra desta noite: diversidade de diálogos (fora da lista acima, 15/09)

Pedido separado, feito só depois de esgotar a fila 🟢 acima — nada aqui competiu por prioridade
com o roadmap de verdade. Seis frentes aditivas, todas compiladas juntas e limpas: mais
variantes de reação a trabalho e a presente/item pra criança, tópico dedicado pra duas crianças
conversando, saudação de proximidade reconhecendo "papai"/"mamãe", mais variantes de voz jovem
e de conversa de casal, e comentário sobre evento do mundo (guarda que vence uma luta, Kweebec
avistado). Documentação completa, com o que testar em cada uma, em `testing_checklist.md`,
seção 24.

Ficou de fora só "brincadeira" como botão de interação jogador↔criança — precisaria de um
`InteractionType` novo e mexer no menu visual (seção 🟡, mesma família do item "Interações
contextuais"), isso sim reestruturando.

## 🟡 Próximas atualizações

### Ferramentas da IA (Function Calling) — **o item maior do roadmap**
Ver ⚠️ #1: é construção do zero. Precisa de:

1. **Protocolo** — declarar as ferramentas no formato de cada provedor. E aí mora o problema:
   OpenAI e Gemini têm formatos de function calling **diferentes**, e hoje `GeminiProvider` e
   `OpenAIProvider` são as duas implementações. Ou se cria uma abstração de ferramenta que cada
   provedor traduz, ou se escolhe um só para começar.
2. **Parsing** — `ai/JsonParser.java` é feito à mão. Respostas com chamada de função são bem
   mais aninhadas que texto puro; provavelmente precisa evoluir.
3. **Execução** — mapear cada ferramenta para uma ação no mundo, na thread do mundo, com
   validação (a IA vai pedir coisa impossível).
4. **Loop** — chamar função, devolver resultado ao modelo, continuar a conversa.

Sugestão de fatiamento: começar por **3 ferramentas só de leitura** (`buscar_npcs`,
`buscar_players`, `buscar_blocos`). Elas não alteram o mundo, então erro da IA não causa dano, e
já validam o protocolo inteiro. Depois liberar as de ação (`andar_ate`, `seguir`, `entregar`).

### ~~Relacionamentos entre NPCs~~ ✅ FEITO
**Onde**: `systems/NPCSocialHelper.java` (`bondNpcs`, `tryCourtship`), `core/FamilySystem.java`,
`core/lifecycle/FamilyBonds.java` (`areCloseFamily`)

Quando dois NPCs terminam uma conversa espontânea (`SOCIALIZING`), o desfecho agora grava de
verdade em `Relationship`: amizade/afinidade/confiança sempre (pior se hostil), e romance também,
quando a conversa foi agradável. Cruzando o mesmo patamar do pedido de casamento com aliança do
jogador (romance ≥ 80, amizade ≥ 70, nos dois lados), as duas NPCs se casam sozinhas e seguem o
mesmo fluxo de gravidez/nascimento/crescimento que já existia para casal jogador+NPC — nada novo
foi necessário ali, ele já era genérico o bastante.

Ficou de fora do escopo original e entrou de brinde: cada NPC agora tem seu próprio número de
"quantos filhos eu quero" (`FamilySystem.desiredChildren`, 0 a 4, sorteado uma vez e persistido),
que passou a condicionar o rolamento diário de gravidez natural **só entre dois NPCs** — o
casamento de um jogador com uma NPC não tem essa preferência armazenada e continua com o
comportamento de sempre.

**Ainda em aberto**: sem decaimento e sem limite diário dedicado para este ganho específico
(o `interactionsToday` de sempre ainda cobre spam do lado do jogador, não conversa NPC-NPC).

### ~~Casa — persistência independente da cama~~ ✅ FEITO (17/09)
Residência migrada para identidade estável própria (UUID e centróide `anchorPos`), conjunto de múltiplas camas (`Set<HouseBlockPos> beds`), e desacoplamento de cama única. Quebra de cama realoca moradores sem apagar a casa; apenas a perda total de portas ou destruição de paredes encerra o imóvel.

### ~~Recursos compartilhados / inventário global~~ ✅ FEITO (17/09)
Implementado via `VillageStockManager` e `ChestData.shared`. Baús de vila podem ser alternados entre privados e comunitários (`/simtale chestshare`). Moradores com fome checam o estoque compartilhado se a casa estiver sem comida, e trabalhadores sem baú doméstico depositam seus excedentes no estoque central da vila (`/simtale villagestock`).

### Profissões executando tarefas + sistema de objetivos
O `Profession` enum já mapeia para `JobType`, e existem estados de trabalho (`MOVING_TO_WORK`,
`FARMING`, `HUNTING`, `MOVING_TO_DEPOSIT`). O que falta é a camada
**Objetivo → Planejamento → Tarefas → Execução**, que é um planejador de verdade, acima da
máquina de estados atual.

Especialização por eficiência (qualquer NPC supre, o especialista supre melhor) é a parte
barata; o planejador é a cara.

### IA Proativa + Eventos para IA
Depende do function calling estar de pé, senão a IA "decide iniciar interação" mas não tem como
agir. As configurações (intervalo, chance, cooldown global e por NPC, desligar) são simples —
entram junto do `AiConfig`, que já existe e já é configurável.

### Interações contextuais
Diálogos condicionados a situação (alta/baixa afinidade, casados, rivais, após presente, chuva,
noite). O gatilho depende dos relacionamentos NPC↔NPC estarem funcionando. Depois disso é, em
boa medida, trabalho de conteúdo.

### Morte — revisão
Morrer normalmente e remover o evento da entidade que leva a alma. Os estados `DYING`, `DEAD` e
`REAPING` já existem. 🟡 porque mexer em remoção de entidade tem o risco de escrita estrutural
no meio do tick que já nos mordeu antes (`Store is currently processing!`).

### Sistema de favores, eventos da vila, manutenção
Já estavam marcados como "próximas atualizações" na sua lista e concordo. Todos dependem de
relacionamentos e de objetivos estarem funcionando primeiro.

### Exploração quando sem tarefas
Explorar arredores e alimentar a memória. `MemoryManager` já existe e persiste. O estado
`WANDERING` também. Fica 🟡 porque "descobrir recursos e locais" precisa de um modelo de
lugar/recurso conhecido que ainda não existe.

---

## 🔴 Futuro distante

### Conhecimento imperfeito (fofoca degradando com a distância da fonte)
Conceitualmente a melhor ideia da lista, e a que precisa de mais base pronta: exige que
informação vire objeto com **procedência e confiança**, não um valor solto. Só faz sentido
depois que memória, relacionamentos e propagação de eventos entre NPCs estiverem sólidos.

### Construção coletiva
Vários NPCs no mesmo canteiro com divisão de trabalho. Hoje `ConstructionSystem` conta
construtores próximos só para acelerar; não há divisão de tarefa nem coordenação.

### Ocupação autônoma, rotas aprendidas, lugares favoritos
Todos precisam de memória espacial persistente por NPC, que não existe.

### Economia avançada e pedidos automáticos de recursos
Depende do inventário compartilhado, das profissões produzindo de verdade e do sistema de
favores. É o topo da pilha.

### Mudança automática de casa
Na lista está junto do bloco de casas, mas depende de o NPC saber avaliar e escolher uma casa
nova — perto de "ocupação autônoma". A parte barata (detectar que a casa deixou de valer) entra
junto do item 🟡 de persistência.

---

## 📌 Filosofia do projeto (registrada, não é tarefa)

**IA offline é a principal.** Objetivos, profissões, casas, construção, economia, memória,
relacionamentos, socialização, exploração e decisões. O mod funciona 100% sem internet.

**IA generativa é opcional.** Conversa natural, improviso, comentário espontâneo, curiosidade,
humor, roleplay. Ela **não controla** comportamento — só enriquece personalidade e diálogo.

> Vale registrar uma tensão real aqui: o bloco "Ferramentas da IA" dá à IA generativa poder de
> **agir** (andar, seguir, entregar item, definir objetivos), o que encosta na fronteira que
> esta seção estabelece. Não é contradição se as ferramentas forem tratadas como *sugestões*
> que a IA offline pode acatar ou recusar — mas se forem comandos diretos, quem joga sem IA
> generativa passa a ter NPCs mensuravelmente mais burros, e a promessa de "experiência completa
> para todos" se perde. Decidir isso **antes** de implementar as ferramentas, porque muda a
> arquitetura.

---

## Ordem sugerida

1. **Esta semana**: contexto da IA, portas, ocioso, fome. São independentes entre si — dá para
   fazer em qualquer ordem e cada uma é verificável sozinha.
2. **Depois**: casa com id permanente → inventário compartilhado → relacionamentos NPC↔NPC.
   Nessa ordem, porque cada um destrava o seguinte.
3. **Em paralelo**, quando quiser: function calling começando pelas ferramentas de leitura.
   Não depende de nada acima.
