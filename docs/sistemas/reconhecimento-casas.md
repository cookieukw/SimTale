# Sistema de Reconhecimento e Validação de Casas

> **TL;DR**: Mapeia e valida a residência física de NPCs e jogadores a partir de suas camas usando um algoritmo de **Flood Fill 3D**. Aplica uma checklist de mobília obrigatória (estilo Terraria), impõe limites de tamanho e evita conflitos estruturais entre vizinhos.

---

## 1. O que é e para que serve
O sistema de reconhecimento de casas converte estruturas puramente cosméticas construídas com blocos em residências válidas na memória do jogo. Ele permite ao mod associar NPCs e jogadores a espaços físicos fechados, garantindo que NPCs reconheçam o interior de suas residências, saibam quais portas usar para entrar e quais baús pertencem legitimamente a eles (bloqueando saques ou acessos a baús de vizinhos).

---

## 2. Como funciona por dentro
O sistema é gerenciado de forma centralizada em `HouseManager.java` e opera nos seguintes pilares:

```
Claim da Cama (NPC/Player)
   ↓
Varredura Flood Fill 3D (Máx: 512 blocos / Mín: 15 blocos)
   ↓
Checa limite de portas (bloqueia flood fill, mas salva como entrada)
   ↓
Validação de conflitos espaciais (BLOCK_TO_HOUSE_ID)
   ↓
Checklist de Mobília Essencial (Luz, Assento e Mesa)
   ↓
Salvamento oficial no banco de dados Caskara
```

### O Algoritmo de Flood Fill 3D
A varredura começa na coordenada da cama e se expande em 6 direções vizinhas. 
*   **Barreiras Físicas**: Blocos sólidos normais (paredes) e blocos de porta interrompem a expansão horizontal/vertical. As portas encontradas são adicionadas a um conjunto de portas de entrada (`doorBlocks`).
*   **Ordem Crítica de Varredura**: Para evitar falsos negativos, o loop analisa primeiramente se o bloco é uma Porta, Cama ou Baú, marcando-os em `visited` e interrompendo a expansão neles. Só então verifica a colisão geral `isSolid()`.
*   **Limite de Tamanho**:
    *   *Teto*: Máximo de 512 blocos de ar (`MAX_INTERIOR_BLOCKS`). Se exceder, a casa é marcada como `TOO_LARGE_OR_UNENCLOSED` (vazou para o exterior).
    *   *Piso*: Mínimo de 15 blocos de ar. Se for menor, retorna `TOO_SMALL` (cubículos rústicos inválidos).

### Prevenção de Conflitos (Reversed Spatial Map)
Para evitar que duas casas separadas por uma brecha fina ou sem paredes se mesclem, o sistema mantém um mapa em memória `BLOCK_TO_HOUSE_ID` (coordenada ➔ id_casa). Se o flood fill de uma casa nova colidir com uma coordenada já pertencente a uma casa registrada, o registro é rejeitado como `CONFLICT_WITH_EXISTING`.

### Checklist de Mobília (Estilo Terraria)
Após validar a integridade física estrutural, a casa passa pelo crivo de mobília essencial (`scanFurniture`):
1.  **Light Source** (Tochas, lanternas, velas, fogueiras)
2.  **Seating** (Cadeiras, banquetas, bancos)
3.  **Surface** (Mesas, bancadas de trabalho, escrivaninhas)

Se qualquer um desses itens obrigatórios não for detectado na varredura do interior, a casa é classificada como incompleta (`ScanOutcome.INCOMPLETE`).

### Abertura Automática de Portas (Estilo Villagers)
Gerenciada por `NPCDoorHelper.java`, chamada na rotina ativa do NPC. **Não depende de casa registrada** — ver a seção de dificuldades abaixo para o porquê.

*   **Detecção Direcional por Sondas**: Em vez de uma varredura 3×3×3 ao redor do NPC (que abria portas laterais ou atrás do personagem), o sistema projeta sondas lineares à frente (`PROBE_DISTANCES = {0.0, 1.0, 1.8, 2.4}`) ao longo do vetor de caminhada em direção ao destino. A checagem consulta `world.getBlockType` e valida a flag nativa `BlockType.isDoor()`, com coordenadas canônicas normalizadas via `DoorInteraction.getDoorAtPosition()`.
*   **Filtro de Intenção e Cone de Visão**: Para impedir a abertura acidental de portas quando o NPC apenas caminha paralelamente a uma parede ou passa perto sem intenção de entrar, é calculado o produto escalar (`FACING_DOT_THRESHOLD = 0.5`, cone de ~60 graus) entre o vetor de caminhada e a porta, além da checagem de travessia do plano da porta (`CROSSING_PROBE_DISTANCE = 1.5`) em relação ao destino final da rota.
*   **Abertura e Portas Duplas**: O estado é calculado por `DoorBlockUtils.getInteractionState(estadoAtual, estadoDesejado)`. O lado da abertura segue a regra nativa do jogo (`DoorBlockUtils.isInFrontOfDoor`): quem está na frente faz a porta abrir para fora, evitando que a folha gire sobre o personagem. A API nativa `DoorInteraction` resolve a transição de estado inclusive para portas duplas integradas.
*   **Fechamento**: Cooldown de 40 ticks (2 segundos) no mapa `OPENED_DOORS`. Ao expirar, a porta fecha automaticamente, a menos que ainda haja algum NPC a menos de 2,5 blocos de raio — caso em que o prazo é renovado para não fechar na passagem.

---

## 3. Decisões de Design e Por Quê
*   **HouseBlockPos leve**:
    *   *Decisão*: Criação da classe `HouseBlockPos` contendo apenas primitivos `x`, `y`, `z` e hashCode/equals simplificado.
    *   *Por quê*: O Hytale utiliza coordenadas da biblioteca JOML (`Vector3i`). JOML é otimizada para álgebra linear e renderização, contendo campos internos que podem confundir o serializador padrão do Caskara, além de consumir mais memória no cache de persistência. A classe limpa garante JSONs de salvamento compactos e compatibilidade retroativa total.

---

## 4. Nível de Complexidade e Robustez
*   **Nível**: 🟡 Moderado/Amarelo
*   **Análise**: Lógica geométrica refinada. A indexação espacial reversa garante verificações rápidas em complexidade $O(1)$ sem lag no tick. A robustez depende da lista de exceções de blocos sólidos (para evitar que decorações como tapetes ou pinturas ajam como paredes e quebrem o flood fill).

---

## 5. Dificuldades Encontradas e Resolvidas

### Bug Crítico de Cama e Baús Invisíveis ao Scan
*   **Sintoma**: Camas adicionais e baús de armazenamento nunca eram detectados nas varreduras das casas, permitindo que NPCs abrissem qualquer baú (livre acesso).
*   **Causa Raiz**: O método `isSolid()` retornava `true` para camas e baús, disparando o comando `continue` antes que o código chegasse nos ifs específicos de checagem de camas/baús.
*   **Correção**: O loop de vizinhos foi reordenado para rodar os testes específicos de Porta, Cama e Baú em primeiro lugar, coletando-os nos respectivos conjuntos de dados antes de aplicar a checagem genérica de blocos sólidos.

### Nenhuma porta abria — para NPC nenhum
O antigo `HouseDoorManager` tinha **dois** defeitos independentes, e qualquer um deles sozinho já anulava o sistema inteiro.

**Defeito 1 — o estado "aberto" era idêntico ao "fechado".** O novo estado era montado por manipulação de texto:

```java
if (state.toLowerCase().contains("closed"))            // testava em minúsculas
    openState = state.replace("closed", "open")        // mas trocava na string original
                     .replace("CLOSED", "OPEN");
```

Os estados reais de porta no Hytale são `CloseDoorIn`, `CloseDoorOut`, `OpenDoorIn`, `OpenDoorOut` e `DoorBlocked` — não existe "closed". O `if` passava **por acidente**: `"CloseDoorIn"` em minúsculas vira `"closedoorin"`, que contém `"closed"`. Mas a string original não contém nem `"closed"` nem `"CLOSED"`, então nenhum dos dois `replace` trocava coisa alguma. A porta era "aberta" para exatamente o estado que já tinha. O fechamento automático tinha o mesmo defeito espelhado.

**Defeito 2 — só considerava as portas da casa registrada do próprio NPC.** A busca partia de `OWNER_TO_HOUSE_ID.get(npc.entityId)`. NPC sem casa, NPC visitando outra casa, portão de vila ou porta de oficina nunca eram sequer avaliados.

**Correção**: `NPCDoorHelper` usa sondas direcionais à frente do NPC na direção do destino com filtro de ângulo/cone, e delega toda a decisão e manipulação de estado para a API do próprio motor (`DoorBlockUtils` + `DoorInteraction.getDoorAtPosition`), em vez de reimplementar lógica de porta com texto ou varreduras cúbicas cegas. A detecção usa a flag nativa `BlockType.isDoor()` — o `getId().contains("door")` anterior pegava indevidamente trapdoors e decorações.

## 6. Os Comandos de Debug e Verificação

### O Comando `/simtale housecheck`
Jogadores e desenvolvedores podem verificar a compatibilidade de qualquer casa digitando `/simtale housecheck` no console do servidor.
*   O sistema localiza a cama cadastrada mais próxima (limite de 16 blocos de distância).
*   Gera e imprime na tela o relatório estruturado: se a casa é válida, se está incompleta e exibe a lista localizada com o que falta (por exemplo: "Precisa de: um assento, uma fonte de luz").

### O Comando `/simtale chestcheck`
Permite validar em tempo real o registro e posse de baús digitando `/simtale chestcheck` no console do servidor.
*   Localiza o baú registrado mais próximo do jogador (raio de 16 blocos).
*   Informa as coordenadas exatas do baú, se ele está devidamente cadastrado no `ChestRegistry`, a qual ID de residência ele pertence, e a lista de UUIDs dos NPCs proprietários com permissão para abri-lo (ou se é um baú público livre para todos).

---

## 7. Perguntas Frequentes (FAQ)

### NPCs podem abrir baús de casas de outros moradores?
Não. O método `HouseManager.canOpenChest(npcId, chestPos)` intercepta as buscas de comida da IA autônoma e checa se a coordenada do baú está registrada em alguma residência. Se estiver, o acesso só é liberado caso o UUID do NPC conste no conjunto `owners` daquela residência específica no Caskara.

### Como um casal compartilha a mesma residência?
Se o flood fill de uma cama A encontrar uma cama B na mesma região de ar fechada sem registros prévios, o scan classifica o resultado como `NEW_HOUSE_MULTI_OWNER` e registra ambos os candidatos a donos como proprietários da mesma `HouseData`.

### Como os NPCs localizam baús de comida sem causar lag de chunk scan?
O mod implementa um **`ChestRegistry`** (semelhante ao `BedRegistry`) que monitora eventos de colocação e quebra de blocos no mundo (`PlaceBlockEvent` / `BreakBlockEvent`). Quando um jogador coloca ou destrói um baú, barril ou armário, a coordenada é salva em um conjunto leve em memória (`ChestRegistry.CHESTS`).
*   **Busca Otimizada**: Na tarefa de fome, o `NPCHungerHelper` apenas calcula a distância 3D das coordenadas pré-cadastradas na lista do `ChestRegistry` (dentro de 10 blocos). Ele verifica se o baú está liberado usando o método `HouseManager.canOpenChest(...)` (que valida se a coordenada pertence a alguma casa de outro NPC), movendo-se diretamente para o baú válido mais próximo sem efetuar nenhum scan ou carregamento de blocos de chunks.



