# Registro de Mobília (Camas, Baús e Âncoras)

> **TL;DR**: Móveis do Hytale ocupam vários blocos e só um deles é a âncora. O SimTale resolve
> qualquer bloco até a âncora usando o dado de *filler* do próprio motor, e mantém dois registros
> em memória — `BedRegistry` e `ChestRegistry` — alimentados por dois caminhos: o evento de colocar
> o bloco e uma varredura ao entrar no mundo.

---

## 1. O problema: um móvel, vários blocos

Uma cama ocupa **seis** blocos e uma porta **quatro**. Apenas um deles é a âncora; os demais são
blocos de preenchimento (*filler*) que guardam o deslocamento até ela.

Isso importa porque `BlockMountAPI.mountOnBlock` calcula onde o corpo deita a partir da **âncora**.
Montar num filler desloca a pose de dormir exatamente pela distância daquele filler até a âncora —
foi o que fazia a NPC dormir atravessada ou flutuando ao lado da cama.

### Como a âncora é resolvida

`FurnitureAnchorHelper.anchorOf(world, x, y, z)` lê o filler pela `BlockSection` do chunk:

```java
int filler = secao.getFiller(x, y, z);
int dx = FillerBlockUtil.unpackX(filler);
```

O **sinal** do deslocamento não é documentado, então as duas direções são testadas e validada
aquela que aponta para um bloco com o mesmo id e `filler == 0`. É o mesmo dado que o comando
`/inspectfiller` do jogo lê.

> Heurísticas de vizinhança ("o vizinho está em +X ou +Z") foram tentadas antes e erram, porque a
> âncora nem sempre satisfaz esse padrão.

---

## 2. Os dois registros

| Registro | Guarda | Chave |
|---|---|---|
| `BedRegistry.BEDS` | `BedPos` (posição + `yaw` do eixo em que se deita) | âncora |
| `ChestRegistry.CHESTS` | `HouseBlockPos` | âncora |

Ambos são **estáticos e globais**, sem escopo de mundo. Na prática isso significa que dois mundos
na mesma sessão compartilham registro. Com a varredura do join isso deixou de dar sintoma, mas
continua sendo uma dívida anotada no roadmap.

---

## 3. Quem alimenta os registros

### `BedPlaceBlockEventSystem` — ao colocar o bloco

Registra cama, baú, plantação e terra arada. O registro de cama passa por
`BedWorldBootstrap.registerBedAt`, que é o **mesmo** código usado pela varredura — assim, cama
colocada na mão e cama achada pela varredura viram exatamente o mesmo registro.

### `PlayerJoinHandler` — ao entrar no mundo

Dispara `BedWorldBootstrap.bootstrapLoadedRadius(world, pos, 32)` com 2 s de atraso, para os chunks
ao redor terem blocos reais para ler. Cobre a mobília que já existia antes do servidor subir.

A linha `[SimTale] Scan found N new beds and M new chests` sai em nível **info**, sem precisar de
`/simtale debug on` — é o sinal de que a varredura funcionou.

### `BedEntityRegistrySystem` — camas que são entidade

Cobre apenas camas representadas como entidade (`PersistentModel`), não como bloco.

---

## 4. Classificação: dado do motor, não nome

Baú **não** é identificado por nome. `ChestRegistry.isContainerAt` pergunta ao motor se o bloco tem
`ItemContainerBlock`:

```java
BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, x, y, z) != null
```

É o **mesmo componente** que as NPCs leem para procurar comida, então registro e consumo não podem
mais discordar.

O `ChestRegistry.isChestId` (nome contendo `chest`/`barrel`/`cupboard`/`cabinet`) continua existindo
**apenas como fallback** e está marcado como não confiável: ele silenciosamente ignorava qualquer
bloco de armazenamento que o Hytale não nomeasse com uma dessas palavras.

> `BedRegistry.isBedId` ainda é heurística de nome (`contém "bed"`, exceto `bedrock`). É uma dívida
> conhecida: se aparecer uma cama com outro nome, ela some do sistema.

---

## 5. Escopo de casa

`HouseManager.canOpenChest` recusa baú que **não pertence a nenhuma casa**. É isso que mantém as
NPCs longe dos baús de loot do gerador de mundo, já que a varredura registra qualquer contêiner no
raio.

Efeito colateral aceito: baú solto do jogador em campo aberto também é ignorado até fazer parte de
uma casa reconhecida.

A varredura de casa (`HouseManager.isChest`) usa a mesma checagem de contêiner. Quando ela usava só
o nome, os baús não entravam no `interior` da casa, o `BLOCK_TO_HOUSE_ID` ficava sem entrada e o
`canOpenChest` recusava todos — NPC com fome parada ao lado de um baú cheio.

---

## 6. Ferramentas

| Comando | Para quê |
|---|---|
| `/simtale debugbeds` | Lista as camas registradas, com dono e botões de teleporte/desvincular. Roda a varredura antes de abrir. |
| `/simtale debugchests` | Lista os baús, com vínculo de casa e **conteúdo** (quantos itens, quantos são comida). |
| `/simtale housecheck` | Valida a casa mais próxima. Também roda a varredura como efeito colateral. |
| `/simtale chestcheck` | Descreve o baú mais próximo. Prefira o `debugchests`, que mostra tudo. |

---

## 7. Lição registrada

Durante muito tempo o registro parecia funcionar em mundos antigos e falhar em mundos novos. A causa
era dupla: o evento de colocar **não registrava cama**, e a única varredura existente só rodava
dentro do `/simtale housecheck`, como efeito colateral. Em mundo antigo alguém já tinha rodado o
comando, e como os registros são estáticos, o estado sobrevivia na memória da JVM.

**O padrão a evitar**: um comando de diagnóstico que também corrige o estado esconde a falha que
deveria expor.
