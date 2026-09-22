# FAQ e Perguntas Rápidas (SimTale)

> **TL;DR**: Uma central rápida de perguntas e respostas objetivas sobre os sistemas internos do SimTale. Abra este arquivo quando precisar responder a dúvidas pontuais sem reler o código.

---

### 1. Como funciona a rotina de sono e a desduplicação de camas?
**Resposta**: Quando a energia do NPC cai abaixo do limite crítico (30 ou 60 para preguiçosos), ele varre o `BedRegistry` em busca de uma cama cadastrada fisicamente no mundo (via eventos de colocação de blocos). O sistema desduplica as camas checando as que já estão salvas no componente `bedLocation` de outros NPCs na memória, garantindo que nenhum NPC tente dormir em uma cama já ocupada.

### 2. Por que os NPCs não andam ou têm rotinas quando são bebês?
**Resposta**: Na fase de bebê (`GrowthStage.BABY`), a entidade é puramente estática e representada por um modelo leve de manta (swaddle) de 3 caixas. A IA de rotina ignora bebês para economizar processamento e simplificar o gameplay, transferindo todo o fluxo de cuidados para o inventário dos pais e a rotina do `BabyCareTickSystem`.

### 3. Como funciona a genética de nomes e aparência dos filhos?
**Resposta**: Ao nascer, o bebê herda uma mistura de nomes e sobrenomes de seus pais e puxa traços de textura (pele e cabelo) baseados nos gradientes de aparência de ambos, simulando hereditariedade.

### 4. Como a IA generativa do NPC sabe do que ele gosta ou com quem ele é casado?
**Resposta**: O `NpcContextBuilder` compila todas as informações do `SimNPCComponent` (incluindo o cônjuge, memórias de eventos recentes, nível de afinidade e a lista de comidas/itens amados e odiados expostos por `NPCPreferences`) em diretivas textuais rígidas no prompt de sistema enviado ao LLM de forma assíncrona.

### 5. O que acontece se eu der lixo para um NPC? E um item básico?
**Resposta**:
*   **Lixo** (terra, ossos, teias, veneno): Perda severa de afinidade (-15) e o NPC entra em humor irritado (`ANGRY`) ou triste (`SAD`) com 100% de chance.
*   **Item Básico** (pedras, sementes, alimentos crus comuns): Ganho muito pequeno de afinidade (+3) e aciona uma resposta de agradecimento polida neutra, sem alterar o humor atual do NPC.
*   **Item Favorito**: Ganho alto (+30) e humor de felicidade (`HAPPY`) com 100% de certeza.

### 6. Como o sistema de turnos de co-parentalidade funciona se eu ficar offline?
**Resposta**: O `BabyCareManager` registra a última vez que o bebê foi cuidado. Ao fazer login, o `PlayerJoinHandler` executa uma simulação offline (`simulateOfflineTime`). Se o turno de cuidados do jogador tiver expirado enquanto ele estava fora, o cônjuge NPC assume a custódia do bebê automaticamente e executa os cálculos e ticks de cuidado em segundo plano, enviando um aviso ao jogador.

### 7. O que é o throttling de movimentação e por que ele economiza CPU?
**Resposta**: É um limitador de processamento em `RoutineAISystem.java`. Em vez de requisitar caminhos de navegação (pathfinding) em todos os frames de tick (20 vezes por segundo), o NPC só atualiza sua rota física (`leashPoint`) se a coordenada do destino tiver se movido mais do que 0.5 blocos em relação ao último cálculo, poupando a thread principal de servidor.

### 8. Qual a diferença entre afinidade, amizade, romance e confiança?
**Resposta**:
*   **Afinidade**: O valor cru total (-100 a 1000) que determina o tier de proximidade social.
*   **Amizade**: Cresce com conversas e piadas gerais.
*   **Romance**: Apenas sobe com ações explícitas de flerte. Destrava casamento.
*   **Confiança**: Aumenta ao contar segredos e cai drasticamente com insultos ou traições.

### 9. Por que a escala visual do bebê é 0.4 e do adulto é 1.0?
**Resposta**: O Hytale renderiza entidades baseadas em malhas. Para simular o crescimento de forma orgânica sem criar 5 modelos 3D diferentes de corpos e esqueletos, o `GrowthManager` aplica fatores multiplicadores de escala tridimensional na entidade correspondente à fase de vida atual.

### 10. Onde ficam salvos os dados do mod e o que acontece se eu apagá-los?
**Resposta**: Os dados são serializados em JSON pelo framework Caskara e salvos no diretório `/data/caskara/` no servidor local. Apagá-los remove todas as informações persistidas de relacionamento, família, casamentos, canteiros de obras e histórico dos NPCs, resetando o mod ao estado virgem.

### 11. O que é o canteiro de obras e como ele evita prender o jogador?
**Resposta**: É a caixa de preview aramada de uma construção. Ele calcula uma zona de clearance (afastamento) de 3.5 blocos ao redor do jogador ativo e não renderiza blocos de preview nesse raio. Isso garante que o preview (mesmo sem colisão física de movimento) nunca fique piscando na tela obstruindo a visão do jogador que está dentro do perímetro da fundação da obra.

### 12. Por que foi feita a otimização de scan de cantos de 8 para 4 pontos?
**Resposta**: A rotação de construções no Hytale ocorre apenas no plano horizontal (eixo Y). Portanto, a coordenada Y (altura) das extremidades superior e inferior de uma caixa delimitadora é invariável após a rotação. Varrer apenas os 4 cantos do plano horizontal XZ ao invés de todos os 8 cantos tridimensionais gera o mesmo resultado matemático exato com metade das operações em loops.

### 13. O que é a fila de regras funcionais em InteractionManager e qual a vantagem sobre if/else?
**Resposta**: É a aplicação do padrão Chain of Responsibility. Em vez de usar blocos `if/else` complexos e de difícil manutenção, criamos coleções estáticas de regras ordenadas (`GIFT_RULES`, `PROFESSION_RULES`, etc.). O sistema filtra e executa a primeira regra aplicável da lista. Isso torna o código modular, legível e desacopla a priorização lógica do fluxo estrutural principal.

### 14. O plumbob do NPC muda de cor dinamicamente? Quais são as cores?
**Resposta**: Sim. Ele lê o humor ativo do NPC: verde para `HAPPY` ou neutro saudável, azul para triste (`SAD`) e vermelho para bravo (`ANGRY`) ou necessidades biológicas críticas (como fome severa).

### 15. Como o sistema de relacionamento gerencia casamentos?
**Resposta**: Quando a afinidade e o romance estão altos (afinidade >= 500, romance >= 80) e o jogador presenteia a aliança (`simtale:wedding_ring`), o casamento é aceito. O NPC cônjuge passa a morar com o jogador (compartilha a coordenada da cama de casal), altera suas falas de boas-vindas para românticas e ajuda ativamente a carregar e cuidar do bebê.

### 16. Como funciona a checklist de mobília (estilo Terraria) para validar a casa?
**Resposta**: Após a validação estrutural (paredes e tetos fechados), o mod varre os blocos internos de ar buscando mobílias específicas. Para ser habitável, a casa deve conter pelo menos uma **fonte de luz** (tochas, lanternas, velas), um **assento** (cadeiras, banquetas) e uma **superfície** (mesas, bancadas de trabalho). Também exige-se um volume livre interno de no mínimo 15 blocos.

### 17. O que acontece se uma cama for reivindicada em uma casa considerada incompleta ou inválida?
**Resposta**: O `RoutineAISystem` rejeita o registro da cama se a casa falhar em requisitos estruturais (como brechas na parede ou conflitos de espaço com vizinhos). O NPC libera a cama, volta a procurar outro abrigo válido e dispara logs de aviso informando que a cama candidata foi rejeitada.

### 18. Como posso testar se uma casa é considerada válida in-game?
**Resposta**: Os jogadores e administradores podem usar o comando de console `/simtale housecheck`. O sistema localiza a cama registrada mais próxima do executor da mensagem (raio de 16 blocos) e retorna no chat do jogo um relatório localizado em tempo real listando as pendências estruturais ou o que falta de mobília.

