# Registro de mobília lê o mundo antes do bloco existir

**Estado:** fechada · **Aberta e corrigida em:** 09/08 · **Arquivo:** `systems/BedPlaceBlockEventSystem.java`

---

## Descrição

Ao colocar um bloco, o registro de mobília (cama, baú, plantação, terra, postos) consulta o mundo
na posição alvo para resolver âncora, yaw e presença de contêiner. Nesse instante o bloco **ainda
não existe**, então todas essas consultas respondem sobre ar.

Sintoma prático: baú colocado não é registrado a menos que o nome contenha `chest`, `barrel`,
`cupboard` ou `cabinet`; cama de dois blocos é registrada na âncora errada; espantalho registra o
bloco atingido em vez da base.

## Causa

`PlaceBlockEvent` estende `CancellableEcsEvent` e expõe `setTargetBlock`, `setRotation` e
`setConsumeItem`. Um evento cancelável com *setters* de destino é, por definição, disparado **antes**
da ação — é a janela em que a colocação ainda pode ser vetada ou alterada.

O handler identificava corretamente *o que* estava sendo colocado (via `event.getItemInHand()`),
mas continuava resolvendo **geometria do mundo** na célula alvo:

```java
ChestRegistry.isContainerAt(world, pos.x, pos.y, pos.z)   // consulta um bloco que não existe
FurnitureAnchorHelper.anchorOf(world, pos.x, pos.y, pos.z) // idem
BedWorldBootstrap.registerBedAt(world, pos.x, pos.y, pos.z)
```

O `isContainerAt` é a checagem autoritativa — pergunta ao motor se o bloco tem `ItemContainerBlock`.
Retornando sempre `false`, a condição caía no `|| isChestId(...)`, que é a heurística por nome
documentada como não confiável e que foi **o bug original** que essa checagem existe para corrigir.
Ou seja: a correção estava anulada por um problema de momento.

## Por que passou despercebido

Dois motivos se somaram:

1. Até a conversão de `WorldEventSystem` para `EntityEventSystem` (mesma rodada), o handler **nunca
   executava**. O código estava errado, mas morto.
2. Todo caminho de inspeção (`debugbeds`, `debugchests`, `housecheck`, `chestcheck`, `rescan` e o
   scan de entrada) roda uma varredura por raio antes de exibir. A varredura lê o mundo depois que
   o bloco existe, então ela registrava tudo corretamente e o registro *parecia* funcionar na
   colocação.

## Correção

O trabalho que depende do mundo foi movido para depois da colocação, via `WorldUtil.execute`, que é
o padrão já usado no projeto para adiar até a thread do mundo:

```java
final Vector3i placedAt = new Vector3i(pos);
WorldUtil.execute(() -> registerPlacedBlock(world, placedAt, placedId));
```

O método adiado começa relendo a célula e desiste se ela estiver vazia — cobre o caso de o evento
ter sido cancelado depois do handler, ou de o jogador ter quebrado o bloco no intervalo:

```java
BlockType placed = world.getBlockType(pos.x, pos.y, pos.z);
if (placed == null || placed.getId() == null || placed.getId().equalsIgnoreCase("Empty")) return;
```

`placedId` continua vindo do item, não da releitura: variantes de estado e rotação fazem o id no
mundo divergir do id colocado, e as checagens são calibradas contra o segundo.

**O preview do blueprint ficou do lado imediato**, de propósito: o scan de obstrução pula a célula
da âncora por design, então ele não precisa do bloco, e mostrar o holograma no mesmo frame do
clique é o que faz a colocação responder na hora.

## Como verificar

1. Colocar um baú cujo id **não** contenha as quatro palavras da heurística → deve aparecer em
   `/simtale debugchests` sem rodar `rescan`
2. Colocar uma cama de dois blocos → `/simtale debugbeds` deve mostrar **uma** entrada, na âncora
3. Colocar um espantalho → `/simtale debugnear` deve mostrar um posto, na base
4. Colocar e quebrar no mesmo instante → nada deve ficar registrado

## Relacionados

- Conversão `WorldEventSystem` → `EntityEventSystem` (o que fez o handler passar a executar)
- `AssetIds` (unificação das nove comparações de id)
- `testing_checklist.md`, bug #22
