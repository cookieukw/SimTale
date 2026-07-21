# Sistema de Construção (ConstructionHelper e Previews)

> **TL;DR**: Controla a renderização de caixas de visualização em aramado (wireframes verdes/vermelhos) para canteiros de obras de NPCs e otimiza a colisão espacial. O scan de rotação foi otimizado matematicamente de 8 para 4 pontos.

---

## 1. O que é e para que serve
O `ConstructionHelper` gerencia a projeção física e a validação de canteiros de obras. Quando o jogador (ou um NPC construtor) inicia um projeto de construção, o mod precisa pintar um contorno visual (ghost block) no mundo sinalizando as bordas da estrutura. Se houver algum bloco obstruindo o espaço, o contorno brilha em vermelho; caso contrário, brilha em verde. O sistema também restaura os blocos originais quando a visualização é cancelada.

---

## 2. Como funciona por dentro
O fluxo do helper opera na classe estática `ConstructionHelper.java` através dos seguintes passos:

1.  **Box Sizing**: O tamanho tridimensional (`BoxSize`) da construção é calculado com base nos limites máximos e mínimos do arquivo de gabarito (`Prefab`).
2.  **Obstruction Scan**: O sistema varre todos os blocos dentro da caixa delimitadora rotacionada na coordenada do mundo. Se houver qualquer bloco sólido (diferente de ar/vazio), marca o canteiro como obstruído (`isClear = false`).
3.  **Wireframe Painting**: Um loop desenha apenas as bordas da caixa (`isEdge() == true`), substituindo temporariamente os blocos de ar do Hytale por blocos decorativos de preview verdes ou vermelhos. Os blocos originais substituídos são compactados em chaves de bit em um mapa de restauração (`originalBlocks`).
4.  **Player Clearance Check**: O canteiro não pinta os blocos de preview em um raio tridimensional de 3.5 blocos ao redor do jogador (`isTooCloseToPlayer`), evitando que o preview prenda o jogador fisicamente dentro de suas paredes caso ele esteja no centro da obra.

---

## 3. Decisões de Design e Por Quê
*   **Armazenamento Compactado de Coordenadas (Coordinate Packing)**:
    *   *Decisão*: Coordenadas 3D (X, Y, Z) dos blocos substituídos são compactadas em uma única chave numérica do tipo `long` (usando 21 bits por eixo) para armazenamento no mapa de estados originais do `ConstructionSiteComponent`.
    *   *Por quê*: Reduz drasticamente a alocação de memória no heap do Java. Em vez de instanciar milhares de objetos de coordenada `Vector3i` ou strings formatadas para cada bloco de preview pintado no mundo, utiliza-se chaves numéricas primitivas de alta velocidade, diminuindo a pressão do coletor de lixo (Garbage Collector).

---

## 4. Nível de Complexidade e Robustez
*   **Nível**: 🟢 Simples e estável
*   **Análise**: Operações puramente matemáticas e de manipulação direta de blocos do Hytale. Muito estável, sem ticks paralelos ou concorrência.

---

## 5. Dificuldades Encontradas e Resolvidas

### Otimização Matemática de Scan de Rotação (8 → 4 pontos)
*   **Problema**: Para calcular a coordenada de ancoragem após rotacionar a construção nas 4 direções cardinais, o sistema varria todos os 8 cantos da caixa delimitadora 3D para descobrir as coordenadas mínimas e máximas projetadas.
*   **Otimização Numérica**: Como a rotação de estruturas em Hytale ocorre exclusivamente ao redor do eixo vertical Y (rotação horizontal), a coordenada Y de qualquer ponto permanece estática e inalterada após a rotação. Portanto, as coordenadas verticais (teto e chão da caixa) são redundantes para encontrar o deslocamento mínimo do canteiro.
*   **Correção**: O loop foi simplificado para analisar apenas os 4 cantos do plano horizontal XZ, reduzindo pela metade o número de operações trigonométricas e loops de varredura na função `computeRotationOffset()`:
    ```java
    for (int dx : new int[]{0, sizeX - 1}) {
        for (int dz : new int[]{0, sizeZ - 1}) {
            Vector3i rotated = rotate(new Vector3i(dx, 0, dz), facing);
            minRotX = Math.min(minRotX, rotated.x);
            minRotZ = Math.min(minRotZ, rotated.z);
        }
    }
    ```
    Isso garantiu o mesmo resultado geométrico exato de forma mais otimizada e elegante.

---

## 6. Pontos em Aberto / Dívida Técnica
*   **Remoção de Preview Órfão**: Se o servidor cair inesperadamente enquanto um jogador visualiza uma pré-construção, as chaves temporárias na memória podem ser limpas, deixando os blocos verdes/vermelhos órfãos no mundo de Hytale permanentemente. O bootstrap do mundo deve varrer e expurgar blocos preview soltos na inicialização.

---

## 7. Perguntas Frequentes (FAQ)

### O preview da construção obstrui a passagem de outros jogadores?
Não. Os blocos de preview (`Green_block_preview` e `Red_block_preview`) são definidos nas configurações JSON de assets do Hytale como não-colisivos (sem caixa física de colisão), permitindo que jogadores passem por dentro deles livremente.

### Como a rotação da construção é controlada?
A rotação é expressa pelo enum `Rotation4` (NORTH, EAST, SOUTH, WEST) e a transposição das coordenadas locais do modelo para coordenadas globais é feita pela matriz de rotação simplificada no método `rotate()`.
