# Sistema de Interações com Jogadores (InteractionManager e IA Generativa)

> **TL;DR**: Controla piadas, cantadas, insultos, presentes e atribuições de trabalho usando uma cadeia de regras declarativas (Chain of Responsibility funcional) e classificação de itens por enums. Integra chamadas de IA generativa de forma assíncrona.

---

## 1. O que é e para que serve
O `InteractionManager` gerencia todos os cliques e ações sociais realizados pelo jogador ao interagir com NPCs. Ele decide se uma cantada será bem-sucedida, se uma piada fará o NPC rir, calcula a afinidade ganha por presentes e valida propostas de casamento ou troca de profissão. Além disso, quando o servidor está configurado para tal, ele orquestra as conversas através de Inteligência Artificial generativa, gerando diálogos dinâmicos e adaptados ao momento de jogo.

---

## 2. Como funciona por dentro
O fluxo de interações do `InteractionManager.java` foi refatorado para eliminar blocos aninhados de `if/else`, utilizando uma **Cadeia de Regras Funcional**:

```
Jogador Clica na UI de Interação
   ↓
Identifica o tipo de ação (CHAT, JOKE, FLIRT, INSULT, GIFT, PROFESSION)
   ↓
Gera Contexto (Ex: GiftContext, ProfessionContext) contendo os dados do momento
   ↓
Varre a lista estática de regras ordenadas (Ex: GIFT_RULES, PROFESSION_RULES)
   ↓
Retorna o primeiro resultado (InteractionOutcome) cujo predicado seja verdadeiro
   ↓
Aplica efeitos (afinidade, humor, confiança) via applyOutcome()
```

### O Enum de Classificação de Presentes
A categorização de itens presenteados usa o enum `GiftCategory` de forma data-driven:
*   `TRASH`: Mapeia palavras-chave universais e em português que caracterizam refugo (como `dirt`, `poison`, `spiderweb`, `lixo`). Reduz drasticamente afinidade e deixa o NPC irritado ou triste.
*   `BASIC`: Blocos comuns de construção e alimentos crus (como `stone`, `wood`, `egg`, `raw`). Dá um bônus muito modesto e aciona uma resposta polida neutra, sem afetar o humor.
*   `NORMAL` (Fallback): Presentes normais de valor médio/alto.

### Fluxo de Conversação com IA Generativa Assíncrona
Quando o jogador clica para conversar com o NPC:
1.  **Context Assembly**: O `NpcContextBuilder` compila todas as informações do NPC (necessidades, traços, cônjuge, memórias, gostos, humor e afinidade) em um prompt de sistema formatado.
2.  **Async Request**: O mod faz um disparo HTTP assíncrono para o LLM configurado (para evitar travar a thread principal do servidor Hytale enquanto espera a resposta da IA).
3.  **UI Feedback**: A UI exibe um indicador visual de "pensando...".
4.  **Response Delivery**: Quando a resposta chega na callback assíncrona, a mensagem do NPC é exibida em formato de balão no chat do jogador, e a afinidade/romance correspondente é atualizada.

### Feedback Tátil e Expressões Visuais (`SimTaleJuiceHelper`)
As interações sociais diretas e ambientais disparam reações físicas e visuais imediatas:
*   **Flerte Bem-sucedido**: O NPC cora com expressão alegre (`Cheerful`), sopra um beijo (`Blow_Kiss`), gera partículas de corações (`Hearts`) sobre a cabeça e entra no humor `EXCITED`.
*   **Flerte Rejeitado**: O NPC franze a testa (`Frown`) e expressa desgosto.
*   **Insultos Severos**: Se o insulto gerar hostilidade ou o NPC tiver traço agressivo, ele desfere um empurrão físico violento (`Punch`), mostra expressão de fúria (`Rage`) e arremessa o jogador para trás com impulso real de *knockback*.
*   **Cumprimento por Proximidade**: Aproximar-se do morador ($\le 4.5m$) faz com que ele se vire, sorria (`Smile`), acene com a mão (`Wave`) e envie uma saudação contextual no chat.

---

## 3. Decisões de Design e Por Quê
*   **Refatoração para Rules Engine**:
    *   *Decisão*: Substituição das antigas cadeias de `if/else` por listas declarativas de regras (`List<GiftRule>`, `List<ProfessionRule>`).
    *   *Por quê*: Aumenta absurdamente a manutenibilidade do código. As ordens de prioridade das regras ficam explícitas, os testes unitários tornam-se triviais de isolar e a inclusão de um novo comportamento vira apenas adicionar mais um item na lista de regras estáticas, sem tocar no fluxo de controle principal.

---

## 4. Nível de Complexidade e Robustez
*   **Nível**: 🟡 Moderado
*   **Análise**: Embora a lógica de regras seja muito limpa e estável, o fluxo de chamadas assíncronas de IA introduz nuances de concorrência. Se a conexão com a API de LLM falhar ou demorar muito, o sistema precisa gerenciar timeouts elegantes sem deixar a UI do jogador travada.

---

## 5. Dificuldades Encontradas e Resolvidas

### Bloqueio da thread do servidor durante chamadas de IA
*   **Problema**: A thread de ticks do Hytale travava completamente por 2 ou 3 segundos quando o jogador tentava conversar, gerando lag massivo no servidor.
*   **Causa Raiz**: A requisição HTTP para a API do modelo de linguagem estava sendo feita de forma síncrona na mesma thread do loop do jogo.
*   **Correção**: Toda a comunicação HTTP do `NpcAiManager` foi movida para uma piscina de threads assíncronas com callbacks, garantindo que o servidor continue rodando a 20 ticks por segundo enquanto a IA processa o texto em segundo plano.

---

## 6. Pontos em Aberto / Dívida Técnica
*   **Tratamento de Timeout da IA**: Atualmente, se a API externa cair, a mensagem fica aguardando indefinidamente ou falha sem uma mensagem de erro customizada local na UI do chat.

---

## 7. Perguntas Frequentes (FAQ)

### Como os traços de personalidade afetam a aceitação de profissões?
Os traços têm prioridade na cadeia de regras de profissão. Por exemplo, o predicado que verifica o traço `LAZY` (preguiçoso) impede a contratação para trabalhos pesados (`MINER`, `LUMBERJACK`) com 60% de chance, retornando uma resposta ranzinza.

### Como o casamento é aceito na lógica de presentes?
Ao dar a aliança (`simtale:wedding_ring`), a lógica intercepta e verifica se o romance está acima de 80 e se ambos são solteiros. Se sim, o casamento é aceito e registrado. Caso contrário, a proposta é recusada no fallback da regra.
