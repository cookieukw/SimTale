# Sistema de Construção (ConstructionHelper, PrefabGhostHelper e Previews)

> **TL;DR**: Mostra ao jogador o que será construído usando o **sistema de holograma de prefab nativo do Hytale** (`PrefabPreview`), sem tocar em um único bloco do mundo. Substituiu o antigo wireframe feito de blocos marcadores verdes/vermelhos. A rotação agora é compartilhada entre o preview e os NPCs construtores, o que corrigiu uma divergência silenciosa entre os dois.

---

## 1. O que é e para que serve
O `ConstructionHelper` gerencia a validação e a projeção visual de canteiros de obras. Quando o jogador inicia um projeto, o mod precisa mostrar onde e como a estrutura vai ficar, e avisar se o espaço está obstruído.

A exibição em si é responsabilidade do `PrefabGhostHelper`, que cria uma entidade com o componente `PrefabPreview` do próprio motor. O cliente desenha o holograma; o servidor nunca altera o terreno.

---

## 2. Como funciona por dentro

1.  **Box Sizing**: O tamanho tridimensional (`BoxSize`) da construção é calculado a partir dos limites do arquivo de gabarito (`Prefab`).
2.  **Obstruction Scan**: O sistema varre a caixa delimitadora rotacionada no mundo. Se houver qualquer bloco sólido, o canteiro é marcado como obstruído (`isClear = false`) e o `/build start` é recusado com uma mensagem.
3.  **Hologram**: `PrefabGhostHelper.show()` converte os `PrefabBlock` do prefab em um array `BlockChange[]`, cria a entidade de preview e guarda a referência dela em `site.previewGhost`.
4.  **Cleanup**: `clearPreview()` remove a entidade. Como nenhum bloco foi alterado, não há nada para restaurar.

---

## 3. Decisões de Design e Por Quê

*   **Holograma nativo em vez de blocos marcadores**:
    *   *Decisão*: Usar o componente `PrefabPreview` do motor em vez de pintar blocos coloridos no mundo.
    *   *Por quê*: O preview antigo era uma edição real e destrutiva do mundo. Ele sobrescrevia blocos de verdade e guardava o que havia embaixo para devolver depois — o que só funciona se a devolução sempre acontecer. Qualquer queda de servidor, unload de chunk ou restauração perdida deixava o marcador gravado no terreno para sempre. E, como um preview sólido destruiria tudo dentro dele, o máximo que dava para mostrar era um aramado da caixa delimitadora. O holograma mostra o prefab de verdade e não tem nada para restaurar.

*   **Anexar `PrefabPreview` direto, sem passar por `PersistentPrefabPreview`**:
    *   *Decisão*: Montar o `BlockChange[]` nós mesmos.
    *   *Por quê*: O helper `PersistentPrefabPreview.spawn(...)` resolve uma `prefabKey` contra o `PrefabStore` do servidor, e os prefabs do SimTale são recursos JSON próprios (`/prefabs/*.prefab.json`) que esse store desconhece. O sistema que apoia o helper (`PrefabPreviewSetup`) não faz nada além de traduzir um prefab guardado em `BlockChange[]` e entregar ao `PrefabPreview` — então pulamos essa etapa. O tracker de rede consulta apenas `PrefabPreview`, então o holograma é transmitido do mesmo jeito.

*   **Uma única matemática de rotação (`ConstructionHelper.mapperFor`)**:
    *   *Decisão*: O holograma e os NPCs construtores usam o mesmo `OffsetMapper`.
    *   *Por quê*: Ver a seção 5.

*   **Armazenamento Compactado de Coordenadas**: mantido apenas para a limpeza de previews legados (blocos marcadores que ainda possam existir na mesma sessão). Nada mais escreve nesses mapas.

---

## 4. Nível de Complexidade e Robustez
*   **Nível**: 🟢 Simples e estável
*   **Análise**: Matemática pura mais um ciclo de vida de entidade. O único ponto de atenção é que criar e remover entidades é uma escrita estrutural na `Store`: o `PrefabGhostHelper` verifica `Store.isProcessing()` e adia via `world.execute` quando necessário, evitando o `IllegalStateException: Store is currently processing!`.

---

## 5. Dificuldades Encontradas e Resolvidas

### O preview rotacionava, a casa não
*   **Problema**: `ConstructionHelper` posicionava o preview com `anchor + rotate(local) - offset`, enquanto o `ConstructionSystem` colocava os blocos reais em `anchor + local`, ignorando `site.facing` por completo. O jogador podia usar `/build rotate` à vontade: os NPCs construíam sempre virado para o norte.
*   **Por que ninguém viu antes**: o preview era um aramado genérico de caixa. Uma caixa retangular rotacionada 180° ocupa o mesmo espaço, então a divergência era praticamente invisível. Com o holograma mostrando o prefab de verdade, ela ficaria escancarada.
*   **Correção**: a conversão de coordenadas locais para deslocamento virou um único `ConstructionHelper.OffsetMapper`, usado tanto pelo holograma quanto pelos construtores. Não há mais duas fórmulas para divergirem.

### `isEmptyBlock` retornava o oposto do nome
*   **Problema**: o método retornava `true` quando o bloco **não** estava vazio, então toda chamada lia como a negação do que fazia.
*   **Correção**: renomeado para `isOccupied`. O uso existente estava correto, mas por pouco.

### Otimização Matemática de Scan de Rotação (8 → 4 pontos)
*   **Problema**: para calcular a ancoragem após a rotação, o sistema varria os 8 cantos da caixa 3D.
*   **Otimização**: como a rotação ocorre só em torno do eixo Y, a coordenada Y nunca muda — os cantos de teto e chão são redundantes.
*   **Correção**: o loop analisa apenas os 4 cantos do plano XZ em `computeRotationOffset()`:
    ```java
    for (int dx : new int[]{0, sizeX - 1}) {
        for (int dz : new int[]{0, sizeZ - 1}) {
            Vector3i rotated = rotate(new Vector3i(dx, 0, dz), facing);
            minRotX = Math.min(minRotX, rotated.x);
            minRotZ = Math.min(minRotZ, rotated.z);
        }
    }
    ```

### `Unknown key! simtale:Green_block_preview`
*   **Problema**: os ids dos blocos marcadores estavam prefixados com `simtale:`. Ids de bloco no Hytale não têm namespace, então `setBlock` lançava `IllegalArgumentException` na thread do mundo e derrubava o mundo inteiro.
*   **Correção imediata**: remoção do prefixo. **Correção definitiva**: a migração para o holograma, que eliminou a chamada `setBlock` do preview por completo.

---

## 6. Pontos em Aberto / Dívida Técnica
*   **~~Remoção de Preview Órfão~~**: *resolvido pela migração.* O preview não grava mais nada no mundo, então uma queda de servidor não deixa resíduo — a entidade de holograma simplesmente não é persistida.
*   **Tint de obstrução é limitado**: `PrefabPreview` expõe `biomeTint`/`waterTint`, mas é o tint de **bioma** — ele só recolore blocos que amostram tint (grama, folhas). Não dá para pintar uma casa de pedra inteira de vermelho. Por isso o estado de obstrução é comunicado ao jogador por texto no `BuildCommand`, e o tint é só um reforço.
*   **Layers durante a obra**: `PrefabGhostHelper.setVisibleLayers()` já existe e permite revelar o holograma camada por camada. Ainda não está ligado ao `ConstructionSystem` — seria natural manter o holograma visível durante a construção e ir baixando as camadas conforme os NPCs constroem.
*   **Rotação interna dos blocos**: a rotação de cada bloco (`PrefabBlock.rotation`) é passada crua, sem somar o `facing` do canteiro. Holograma e construção concordam entre si, mas portas e escadas de uma casa rotacionada podem apontar para o lado errado.
*   **Assets legados**: `Green_block_preview` / `Red_block_preview` (JSON + PNG) continuam no repositório mas não são mais referenciados por nenhum código.

---

## 7. Perguntas Frequentes (FAQ)

### O preview da construção obstrui a passagem?
Não, e agora por construção: o holograma não existe no mundo do servidor. É uma renderização no cliente, sem bloco e sem colisão. O antigo preview de blocos precisava depender de assets configurados como não-colisivos e ainda assim abria um raio de 3.5 blocos ao redor do jogador para não prendê-lo — nada disso é mais necessário.

### Como a rotação da construção é controlada?
A rotação é expressa pelo enum `Rotation4` (NORTH, EAST, SOUTH, WEST). A transposição de coordenadas locais para globais é feita por `ConstructionHelper.mapperFor(prefab, facing)`, usado igualmente pelo holograma e pelos NPCs construtores.
