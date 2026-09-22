# Sistema de Tiers de Relacionamento e Diálogos

> **TL;DR**: Gerencia os 5 níveis (Tiers) de amizade/proximidade entre o jogador e o NPC, mapeando como os diálogos são filtrados e carregados das tabelas de localização locais baseados no relacionamento e no humor.

---

## 1. O que é e para que serve
O sistema serve para escalonar as respostas dos NPCs com base na proximidade social com o jogador. Em vez de todos os diálogos serem genéricos, o mod calcula um nível social do jogador com o NPC (de inimigos a cônjuges) e puxa falas específicas daquele patamar de intimidade. Isso evita que um desconhecido use apelidos românticos ou que um cônjuge trate o jogador com formalidade excessiva.

---

## 2. Como funciona por dentro
O sistema baseia-se nos seguintes arquivos e lógicas:
*   `FriendshipTier.java` (no pacote `com.cookieukw.SimTale.core`): Define os 5 níveis principais baseados nos valores de afinidade de `Relationship`:
    1.  `HOSTILE` (Afinidade < -30)
    2.  `STRANGER` (Afinidade entre -30 e 49)
    3.  `ACQUAINTANCE` (Afinidade entre 50 e 199)
    4.  `FRIEND` (Afinidade entre 200 e 499)
    5.  `CLOSE_FRIEND` (Afinidade >= 500)
*   **Mapeamento de Tradução**:
    Os arquivos de tradução (`Server/Languages/pt-BR/npc-dialogues.lang` e `en-US/npc-dialogues.lang`) organizam chaves de falas contextuais baseadas em categorias e no tier ativo.
    As chaves seguem o padrão estruturado:
    `chat.[friendly/greedy/paranoid/lazy].[greeting/chat/random].tier.[1-5]`

---

## 3. Decisões de Design e Por Quê
*   **Tiers Desacoplados de Status Fixo**: O `FriendshipTier` é calculado em tempo real com base no valor numérico de `affinity`, em vez de ser um status estático armazenado.
    *   *Por quê*: Permite uma flutuação fluida. Se a afinidade cair devido a uma ação ruim do jogador, o tier é recalculado imediatamente para baixo no próximo diálogo, alterando instantaneamente a forma como o NPC cumprimenta e conversa com o jogador.
*   **Divisão de Categorias de Diálogo**: Há mais de 19 categorias estruturadas de diálogo abrangendo cumprimentos, piadas, flertes, casamento, rejeições, propostas e tarefas.
    *   *Por quê*: Manter os arquivos `.lang` mapeados de forma granular com parâmetros dinâmicos (como `{name}` e `{itemName}`) permite o uso de chaves limpas tanto no chat padrão quanto no prompt das IAs generativas como fallbacks rápidos.

---

## 4. Nível de Complexidade e Robustez
*   **Nível**: 🟢 Simples e estável
*   **Análise**: Operações simples de comparação de limites inteiros. Muito estável, sem impacto em performance ou bugs recorrentes.

---

## 5. Dificuldades Encontradas e Resolvidas
*   **Tradução Hardcoded de Fallbacks**:
    *   *Sintoma*: Diálogos do chat local traduziam nomes de bebês e falas de carinho de forma inadequada no console ou no balão de chat (misturando inglês e português).
    *   *Correção*: Implementado o uso de `Message.translation(...)` dinâmico em todos os manipuladores de eventos em `SimTaleChatHandler.java` e `NPCInteractionPage.java`, vinculando os arquivos de localização de forma nativa e resolvendo as inconsistências de linguagem.

---

## 6. Pontos em Aberto / Dívida Técnica
*   **Diálogos entre NPCs**: O sistema de diálogos por tier atualmente só é estruturado e validado para a relação `Jogador ↔ NPC`. A comunicação direta de diálogos entre dois NPCs em ticks do servidor ainda não é suportada por tiers sociais específicos.

---

## 7. Perguntas Frequentes (FAQ)

### Como os tiers afetam o casamento?
O casamento (`RelationshipStatus.MARRIED`) é um status especial independente do `FriendshipTier`, mas ele desbloqueia a categoria de diálogos românticos exclusivos de nível máximo (`chat.greeting.romantic`) e anula os testes de distanciamento e desconhecimento social.

### Um NPC inimigo pode ter o tier FRIEND?
Não. A afinidade de inimigos é travada em valores negativos, o que mapeia o tier permanentemente para `HOSTILE` ou `STRANGER` até que o jogador recupere os pontos sociais.
