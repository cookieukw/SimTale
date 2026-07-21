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
Para que os NPCs consigam navegar livremente até suas camas ou baús de comida sem ficarem travados por portas fechadas, o sistema possui um mecanismo gerenciado por `HouseDoorManager.java` que é chamado na rotina ativa do NPC:
*   **Abertura (Otimizada)**: Para evitar o gargalo de performance de varrer blocos físicos ao redor de múltiplos NPCs (evitando loops com `world.getBlockType`), o NPC consulta a lista de portas pré-cadastradas de sua própria residência em `HouseData.doors`. Ele calcula apenas a distância simples 3D e, se estiver a menos de 2.0 blocos de alguma porta registrada, checa se ela está fechada (ID contendo `_closed`). Em caso positivo, abre a porta no mundo (trocando o ID do bloco para `_open`, preservando a rotação indexada) e a registra no mapa global `OPENED_DOORS_COOLDOWN` com limite de 40 ticks (2 segundos).
*   **Fechamento**: A cada tick global do mundo, o cooldown das portas abertas é decrementado. Ao expirar o tempo, a porta é fechada (ID retornado para `_closed`) desde que não haja nenhum outro NPC ativo a menos de 2 blocos de distância da porta.

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

---

## 6. O Comando `/simtale housecheck`
Jogadores e desenvolvedores podem verificar a compatibilidade de qualquer casa digitando `/simtale housecheck` no console do servidor.
*   O sistema localiza a cama cadastrada mais próxima (limite de 16 blocos de distância).
*   Gera e imprime na tela o relatório estruturado: se a casa é válida, se está incompleta e exibe a lista localizada com o que falta (por exemplo: "Precisa de: um assento, uma fonte de luz").

---

## 7. Perguntas Frequentes (FAQ)

### NPCs podem abrir baús de casas de outros moradores?
Não. O método `HouseManager.canOpenChest(npcId, chestPos)` intercepta as buscas de comida da IA autônoma e checa se a coordenada do baú está registrada em alguma residência. Se estiver, o acesso só é liberado caso o UUID do NPC conste no conjunto `owners` daquela residência específica no Caskara.

### Como um casal compartilha a mesma residência?
Se o flood fill de uma cama A encontrar uma cama B na mesma região de ar fechada sem registros prévios, o scan classifica o resultado como `NEW_HOUSE_MULTI_OWNER` e registra ambos os candidatos a donos como proprietários da mesma `HouseData`.

### Como os NPCs localizam baús de comida sem causar lag de chunk scan?
O mod implementa um **`ChestRegistry`** (semelhante ao `BedRegistry`) que monitora eventos de colocação e quebra de blocos no mundo (`PlaceBlockEvent` / `BreakBlockEvent`). Quando um jogador coloca ou destrói um baú, barril ou armário, a coordenada é salva em um conjunto leve em memória (`ChestRegistry.CHESTS`).
*   **Busca Otimizada**: Na tarefa de fome, o `NPCHungerHelper` apenas calcula a distância 3D das coordenadas pré-cadastradas na lista do `ChestRegistry` (dentro de 10 blocos). Ele verifica se o baú está liberado usando o método `HouseManager.canOpenChest(...)` (que valida se a coordenada pertence a alguma casa de outro NPC), movendo-se diretamente para o baú válido mais próximo sem efetuar nenhum scan ou carregamento de blocos de chunks.



