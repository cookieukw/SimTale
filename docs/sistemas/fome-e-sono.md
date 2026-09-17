# Fome, Sono e Morte

> **TL;DR**: O sono é decidido pelo relógio do mundo (guardas têm turno invertido), a comida é
> classificada pelo dado do item e não pelo nome, e a morte por fome vem do dano acumulado de
> inanição — não do momento em que a barra chega a zero.

---

## 1. Sono por horário

Antes, o sono era disparado **só** por exaustão. Uma NPC com energia cheia perambulava a noite
inteira e a vila nunca ficava quieta.

`NPCSleepHelper.isSleepPeriod(npc, world)` responde se a janela de sono daquela NPC está aberta:

```java
boolean night = progress < 0.25f || progress > 0.75f;
return isNightWatch(npc) ? !night : night;
```

O corte de noite (0.25 / 0.75 do `WorldTimeResource.getDayProgress`) é **o mesmo** que o
`InteractionManager` já usava para as falas noturnas, para diálogo e rotina nunca discordarem sobre
que horas são.

Quando o recurso de tempo não pode ser lido, a função devolve `false` — degrada para o
comportamento antigo em vez de prender a vila na cama.

### Guardas

`Profession.GUARD` roda o **turno invertido**: acordado a noite toda, dormindo de dia. A energia
decai igual à de todo mundo e recupera na cama, só em horário trocado. Por isso não foi preciso
criar nenhuma regra especial para energia zero.

### Acordar

O campo `RoutineAIComponent.sleepingOnSchedule` distingue os dois motivos de estar na cama:

| Dormiu por | Acorda quando |
|---|---|
| Horário | a janela de sono fecha |
| Exaustão | energia chega a 100 ou `SLEEP_DURATION_TICKS` |

Sem essa distinção a NPC pularia da cama no meio da noite assim que a energia enchesse.

---

## 2. Classificação de comida

`NPCFoodHelper` usa o **dado do item**, nunca o nome:

```java
Map<InteractionType, String> interactions = item.getInteractions();
String id = interactions.get(InteractionType.Secondary);
// Root_Secondary_Consume_Food_T1..T3
```

O tier vem de graça como sinal de "cru vs. preparado": carne crua, ingredientes e colheitas herdam
T1 dos seus templates, enquanto qualquer coisa cozida declara T2 ou T3.

| Tier | Fome | Vida |
|---|---|---|
| T1 (cru) | +25 | +6 |
| T2 | +45 | +14 |
| T3 (preparado) | +65 | +24 |

A heurística antiga procurava `"food_"` no id: aceitava `Plant_Crop_Mushroom_Cap_Brown` (não é
comestível) e rejeitava `Ingredient_Dough` (é).

### Escolha

`scoreFor` ordena por **tier → gosto → distância**. O tier domina de propósito: uma torta odiada
ainda ganha de um pedaço de carne crua favorita, que é o comportamento que um sim de vila quer.

---

## 3. Os três limiares

| Fome | O que acontece | Onde |
|---|---|---|
| < 50 | procura comida **se estiver ociosa** | `RoutineAISystem`, branch IDLE |
| < 25 | **larga a tarefa** para comer | interrupção de fome |
| < 5 | começa a perder vida | `NPCHungerHelper.tickStarvation` |

A interrupção existe porque a fome só era checada em `IDLE`: uma NPC sempre ocupada morria de fome
com a despensa cheia. O sono já funcionava assim; a fome não, e a assimetria não tinha motivo.

Ambas as interrupções respeitam `nextBedSearchTick` / `nextFoodSearchTick`. Sem esse cooldown, uma
NPC sem cama ou comida alcançável re-entrava na busca **todo tick** — uma sessão real registrou
3447 rejeições da mesma cama em poucos segundos.

---

## 4. Morte

A morte vem do **dano acumulado de inanição**, não da barra de fome:

```java
if (npc.needs.starvationDamage >= NPCHungerHelper.LETHAL_STARVATION_DAMAGE) {
    ai.currentTask = TaskType.DYING;
```

Antes, `hunger <= 0` matava na hora, o que tornava todo o sistema de inanição decorativo: a NPC
morria assim que a barriga esvaziava, muito antes do dano importar, e curar comendo não mudava nada.

### Por que um contador e não a vida real

A RuneCore expõe `addHealth`/`subtractHealth` mas **não** um getter de vida confiável, e o caminho
de leitura do stat map do motor não pôde ser confirmado no bytecode. Então o dano é aplicado de
verdade (a barra de vida reflete) **e** acumulado em `Needs.starvationDamage`, com o limite de 200
igual ao `MaxHealth` declarado nos roles.

**Custo assumido**: uma NPC já ferida por outra coisa não morre de fome mais cedo.
**Ganho**: duas horas determinísticas, e a contagem sobrevive ao relog porque `Needs` é persistido.

Comer zera o contador — quem comeu no último segundo não pode cair morto nos ticks seguintes.

### Estados de morte são inegociáveis

As interrupções de sono e fome ignoram `DYING`, `DEAD` e `REAPING`. Sem isso, a interrupção
arrancava a NPC do estado de morte, o teste disparava de novo no tick seguinte, e o aviso de "está
morrendo" repetia para sempre sem ela nunca morrer.

### Linha do tempo (fome 100, vida 200)

| Marco | Fome | Acumulado |
|---|---|---|
| Procura comida se ociosa | 50 | ~6,9 h |
| Interrompe a tarefa | 25 | ~10,4 h |
| Começa a perder vida | 5 | ~13,2 h |
| Morte | — | ~15,2 h |

---

## 5. Alimentar pela mão e Celebrações

Com fome em **50 ou menos**, dar comida de presente faz a NPC comer na hora em vez de guardar
(`InteractionManager.tryFeed`). Restaura fome e vida pelo mesmo tier do sistema de baú, zera a
inanição e dá afinidade bem maior que um presente comum.

Comida odiada ainda alimenta, com ganho reduzido e −10 de `fun`: ela come reclamando, não recusa.

Usa o mesmo `NPCFoodHelper` do baú, então não é possível forçar goela abaixo algo que ela não
comeria sozinha. Sem fome, a comida volta a ser um presente comum.

### Bolo de Aniversário (Birthday Cake)
Celebrar com um morador oferecendo o **Bolo de Aniversário** restaura integralmente a fome e saciedade, concede um bônus expressivo de afinidade e eleva o humor do colono para o nível máximo de alegria (**Mood: EXCITED** com intensidade 1.0).

---

## 6. Estoque Compartilhado da Vila e Sino de Recolher

### Armazém Comunitário (`VillageStockManager`)
Quando um morador fica com fome, ele prioriza os baús da sua própria residência. Se a despensa de sua casa estiver desprovida de alimentos (ou se ele for um colono novo sem residência definida), o sistema recorre aos **baús compartilhados da vila** (`ChestRegistry.isShared(pos) == true`). Isso permite a construção de celeiros e armazéns centrais que abastecem todos os cidadãos da colônia.

### O Sino da Vila (`TownBell`) e Recolher Imediato
Além do ciclo natural de dia/noite gerenciado pelo relógio do mundo, o jogador pode utilizar o **Sino da Vila (`TownBell`)**. Ao ser tocado, o sino reproduz um repique sonoro em 3D e aciona a flag `npc.forceSleep = true` para todos os moradores (exceto Guardas e o Ceifador) dentro de um raio de 50 blocos, ordenando que todos interrompam suas tarefas e retornem imediatamente para suas camas para dormir.
