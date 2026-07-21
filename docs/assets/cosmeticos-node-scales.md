# Pipeline de Assets: Cosméticos e Redimensionamento de Nodes (NODE_SCALES)

> **TL;DR**: Explica o pipeline automatizado via script Python (`generate_child_variants.py`) que lê modelos de cosméticos adultos do Hytale (cabelos, roupas, calçados) e aplica fatores de escala em seus ossos (`NODE_SCALES`) para gerar roupas proporcionais a crianças.

---

## 1. O que é e para que serve
No Hytale, roupas, cabelos e acessórios são anexados como sub-modelos (`Attachments`) nos ossos (nodes) do esqueleto do jogador ou NPC. Como o SimTale introduz crianças e adolescentes (que usam esqueletos redimensionados menores), os cosméticos adultos padrão ficariam flutuando desalinhados ou gigantescos. O script de redimensionamento automatiza a deformação dessas malhas 3D para gerar variantes infantis perfeitamente encaixadas.

---

## 2. Como funciona por dentro
O pipeline de redimensionamento é controlado pelo script Python `generate_child_variants.py`:

```
Leitura dos Catálogos de Roupas/Cabelos do Hytale (Pants.json, haircuts.json, etc.)
   ↓
Identifica as rotas dos arquivos .blockymodel associados
   ↓
Aplica a matriz de distorção NODE_SCALES nos eixos X, Y e Z
   ↓
Salva os arquivos deformados com sufixo "_Child.blockymodel" na pasta do mod
```

### O Dicionário `NODE_SCALES`
Os fatores de escala aplicados aos nodes do esqueleto do Hytale para simular a anatomia de uma criança são:
*   `Pelvis` / `Belly` / `Chest`: `(0.70, 0.65, 0.70)` (Tronco encurtado e mais fino)
*   `Head` (Cabeça): `(1.20, 1.20, 1.20)` (Cabeça ligeiramente maior, característica típica de proporção infantil)
*   `R-Arm` / `L-Arm` / `Forearm`: `(0.68, 0.65, 0.68)` (Braços mais curtos)
*   `Thigh` / `Calf` / `Foot` (Pernas): `(0.70, 0.62, 0.70)` (Pernas mais curtas)

O script navega recursivamente pela árvore de nós (`nodes`) do arquivo de modelo original e multiplica as posições e o esticamento (`stretch`) das caixas pelos fatores correspondentes ao osqueleto herdado.

---

## 3. Decisões de Design e Por Quê
*   **Deformação via Script Python Off-game**:
    *   *Decisão*: Realizar a escala dos arquivos `.blockymodel` no disco antes de empacotar o mod, ao invés de tentar redimensioná-los dinamicamente via Java na memória do servidor.
    *   *Por quê*: A renderização de sub-modelos (attachments) de Hytale no lado cliente exige arquivos JSON estáticos de modelo. Processar milhares de vértices na memória Java do servidor a cada spawn consumiria muito processamento e Hytale não oferece APIs em Java para distorção arbitrária de geometria de attachments dinamicamente em tempo de execução.

---

## 4. Nível de Complexidade e Robustez
*   **Nível**: 🟡 Moderado
*   **Análise**: O script é muito eficiente e seguro, mas exige que a estrutura de nodes e pastas originais de assets do Hytale não sofra alterações drásticas no SDK oficial, sob pena de falha ao encontrar caminhos de arquivos.

---

## 5. Dificuldades Encontradas e Resolvidas

### Rostos e Olhos sumindo ou ficando distorcidos (Node Inheritances)
*   **Problema**: Acessórios faciais (como olhos e bocas) ficavam esmagados ou deformados de forma assustadora na cabeça do NPC criança.
*   **Causa Raiz**: Os sub-nós da cabeça (como `R-Eye-Attachment` ou `Neck`) herdavam as escalas de redução do corpo, mas como a cabeça é escalada para maior (`1.2f`), a dupla herança causava distorção geométrica.
*   **Correção**: Foi criada uma lista de exceções (`FACE_ATTACHMENT_NAMES`) no script Python que trava a escala própria de partes faciais em `(1.0, 1.0, 1.0)`, anulando a multiplicação de nós herdados para esses sub-nós críticos.

---

## 6. Pontos em Aberto / Dívida Técnica
*   **Geração Estática**: Atualmente, se um novo cosmético é adicionado ao catálogo oficial de Hytale, o script precisa ser re-executado manualmente para gerar a versão infantil correspondente.

---

## 7. Perguntas Frequentes (FAQ)

### As animações bípede funcionam normalmente nos modelos escalados?
Sim. Como a escala altera apenas as dimensões de esticamento das caixas e a posição dos eixos nos arquivos de malha 3D, a estrutura lógica de ossos e nomes permanece idêntica à do esqueleto de Hytale, herdando as animações padrão de andar, correr e pular sem quebras.

### Por que a cabeça das crianças é configurada com escala de 1.20?
Anatomicamente, crianças humanas têm cabeças maiores em relação à proporção de seus corpos se comparadas a adultos. A escala de 1.20 ajuda a dar o aspecto visual estilizado de infância (Chibi/Doll) no design cúbico de Hytale.
