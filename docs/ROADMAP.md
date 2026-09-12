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

### 3. A casa é identificada pela cama, e isso é estrutural

`HouseManager.scanHouseFromBed()` parte da cama, e `HouseData.bedPos` é o que casa uma casa
existente com um novo scan. Tirar essa dependência não é trocar um campo: é mudar o critério de
identidade e migrar os registros já salvos. É um item de esforço médio, não pequeno.

---

## 🟢 Esta semana

### Contexto da IA — os campos que faltam
**Onde**: `ai/NpcContextBuilder.java`

O prompt já monta gênero, personalidade, humor, profissão, trabalho atual, necessidades (fome,
energia, social, diversão, higiene), preferências, hobby, família e o relacionamento com o
jogador. Já é mais da metade da sua lista.

Falta, e é só ler dado que já existe e concatenar:

| Campo | Fonte |
|---|---|
| Localização | `TransformComponent` |
| Estado (vida/fome/energia) | fome e energia já estão; falta vida |
| Casa | `HouseManager.OWNER_TO_HOUSE_ID` |
| Players próximos | varredura por raio, igual à que `ConstructionSystem` já faz |
| Horário | `world.getTick()` |
| Relacionamentos com outros NPCs | o mapa já existe (ver ⚠️ #2) |

**Fácil porque** é montagem de string sobre dado pronto, sem sistema novo. Não tem risco de
regressão fora do prompt.

**Ficam de fora desta semana**: *clima* (preciso achar a API de weather do servidor),
*inventário resumido* (depende do inventário compartilhado, ver 🟡) e *eventos recentes*
(depende de um log de eventos que não existe).

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

**Bug concreto, achado em teste (bug #5, `testing_checklist.md` seção 2)**: duas NPCs usando a
mesma porta ao mesmo tempo ainda conflitam — uma fecha enquanto a outra está abrindo. A troca do
cone de 60° por uma checagem geométrica (`DoorBlockUtils.isInFrontOfDoor`) melhorou bastante, mas
não eliminou a corrida. Provavelmente precisa de algum tipo de lock por porta (a NPC que está no
meio do gesto de abrir segura o estado até terminar), não só de uma checagem melhor de lado.

### Comportamento ocioso — NPC olhando para parede
**Onde**: `systems/RoutineAISystem.java`, estado `IDLE`

Mesma família dos ajustes de rotação/animação que já fizemos na câmera de interação. Encosta em
código que já conheço bem.

### ~~Fome — revisão~~ ✅ FEITO
**Onde**: `systems/NPCFoodHelper.java` (novo), `NPCHungerHelper`, `RoutineAISystem`

Classificação de comida passou a usar o dado do item (`isConsumable` + o tier
`Root_Secondary_Consume_Food_T1..T3`) em vez do nome. A heurística antiga aceitava
`Plant_Crop_Mushroom_Cap_Brown` (não é comestível) e rejeitava `Ingredient_Dough` (é).

Escolha agora ordena por **tier → gosto → distância**: comida preparada antes de crua, favorita
antes de odiada. Antes pegava o primeiro slot do baú mais próximo.

Comer restaura fome (25/45/65) e vida (6/14/24, via `StatHelper` do RuneCore). Fome abaixo de 5
tira vida, calibrada para 2 h de vida cheia até a morte.

Interrupção por fome adicionada, espelhando a do sono — antes a fome só era checada em `IDLE`, e
uma NPC sempre ocupada nunca comia. Com cooldown (`nextFoodSearchTick`) para não repetir a
tempestade de buscas que a cama teve.

**Ficou de fora, de propósito**: baú do mundo continua ignorado (decisão de design — a NPC só usa
o que o jogador colocou).

### Morte por fome — falta confirmar
**Onde**: `NPCHungerHelper.tickStarvation` → `RoutineAISystem` (`DYING` / `REAPING`)

`StatHelper.subtractHealth` chegando a zero **não foi verificado**. Se o motor matar a entidade
direto, a NPC some sem passar pelo fluxo de morte do SimTale (corpo, ceifador, registro). Teste
antes de considerar a fome fechada.

### Reputação por profissão
**Onde**: campo novo em `SimNPCComponent` + codec + `NpcContextBuilder`

Contar trabalhos concluídos por profissão e expor "melhor pescador da vila" no contexto. O
sistema de trabalho já marca `currentJob`, então é só acumular. Entra como 🟢 **se** o escopo
for só o número e o texto; vira 🟡 se for para influenciar comportamento.

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

### Casa — persistência independente da cama
Ver ⚠️ #3. Escopo: id permanente, centro, limites e dono salvos; quebrar cama não apaga a casa;
outro NPC não registra a mesma casa; só destruir a estrutura libera. Inclui **migração dos
registros já gravados**, senão as casas existentes somem.

### Recursos compartilhados / inventário global
`ChestRegistry` existe e `HouseData.chests` já guarda os baús de cada casa — a fundação está
pronta. Falta a camada lógica que trata o conjunto como um estoque só: consultar, depositar,
consumir, compartilhar excedente.

Depende de: casa com identidade estável (item acima). Fazer antes disso é construir sobre chão
que vai mudar.

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
