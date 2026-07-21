# Asset 3D: Baby Swaddle Model

> **TL;DR**: O modelo 3D do bebê (`Baby.json` e `.blockymodel`) é um modelo minimalista contendo apenas 3 caixas estruturais (corpo) e 1 cabeça de bebê extraída de Hytale, utilizando um atlas de textura unificado para otimização.

---

## 1. O que é e para que serve
O `Baby Swaddle` é o modelo tridimensional que representa o bebê recém-nascido no SimTale. Como os bebês em Hytale não andam sozinhos e passam a maior parte do tempo no colo dos pais (como itens no inventário) ou deitados em berços, o modelo precisava ser extremamente leve e otimizado, sem articulações de pernas ou braços complexos, parecendo um bebê enrolado em uma manta de maternidade (swaddle).

---

## 2. Como funciona por dentro
O modelo foi projetado no Blockbench e exportado no formato JSON do Hytale:

```
Baby Swaddle Model Structure
├── Box 1: Corpo/Manta base
├── Box 2: Volume da manta superior
├── Box 3: Detalhe do laço/dobra
└── Cabeça do bebê (Malha 3D extraída da cabeça nativa de Hytale)
```

*   **Otimização do Atlas**: O modelo usa um único mapa de textura (atlas de 64x64 ou 128x128 pixels). Todos os detalhes da manta e do rosto do bebê compartilham a mesma textura, minimizando o número de chamadas de renderização (draw calls) da GPU.
*   **Anexação no Ponto de Leash**: Quando colocado no mundo, ele renderiza como um pequeno cesto de bebê. Quando segurado pelo jogador, o modelo é desenhado no braço da personagem usando um slot de attachment correspondente.

---

## 3. Decisões de Design e Por Quê
*   **Geometria de 3 Caixas para o Corpo**:
    *   *Decisão*: Modelar a manta usando apenas 3 cuboides no Blockbench ao invés de curvas complexas.
    *   *Por quê*: Hytale adota uma estética cúbica (voxel art). Manter a manta com apenas 3 caixas respeita perfeitamente a identidade visual original do jogo e garante que o modelo consuma pouquíssima memória de renderização, permitindo múltiplos bebês no mesmo local sem quedas de FPS no cliente.

---

## 4. Nível de Complexidade e Robustez
*   **Nível**: 🟢 Simples e estável
*   **Análise**: Modelo estático sem rigging ou animações de ossos complexas. Risco de bugs nulo.

---

## 5. Dificuldades Encontradas e Resolvidas

### Textura esticada nas dobras da manta (UV mapping issues)
*   **Problema**: A textura do swaddle ficava esticada e borrada nos cantos diagonais do modelo 3D.
*   **Causa Raiz**: O mapeamento UV das caixas estava configurado de forma automática no Blockbench, esticando pixels retangulares sobre superfícies quadradas.
*   **Correção**: O mapeamento UV de cada uma das 3 caixas foi refeito manualmente no atlas unificado, travando a proporção de aspecto (1:1) dos pixels e garantindo um visual limpo e nítido de pixel art.

---

## 6. Pontos em Aberto / Dívida Técnica
*   **Expansão de Manta Colorida**: Atualmente, há apenas um modelo de textura de manta de cor neutra. Planeja-se suportar variações de cores (azul, rosa, amarelo) carregadas dinamicamente com base no gênero do filho.

---

## 7. Perguntas Frequentes (FAQ)

### O bebê pisca os olhos no modelo 3D?
Não. Para manter o modelo o mais leve possível, a cabeça do bebê é estática e não possui animações faciais complexas nas texturas.

### O modelo do bebê é escalado dinamicamente?
No estágio de bebê (`GrowthStage.BABY`), a escala visual do modelo é travada em `0.4f`. Quando o bebê evolui para Toddler, o modelo muda de malha para a estrutura bípede clássica do Hytale com escala `0.5f`.
