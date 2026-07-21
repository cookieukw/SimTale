# Sistema de Persistência e Banco de Dados (Caskara)

> **TL;DR**: Controla a persistência em disco do estado dos NPCs, camas e dados de jogadores utilizando o framework Caskara. Evita perdas de dados salvando automaticamente no descarregamento de chunks ou encerramento do servidor.

---

## 1. O que é e para que serve
O sistema de persistência garante que toda a progressão do SimTale (relacionamentos, humor, necessidades, casamentos, canteiros de obras e a existência de filhos e bebês) seja salva de forma permanente. Como o Hytale gerencia o carregamento e descarregamento dinâmico de regiões do mapa (chunks), os dados das entidades na memória RAM seriam destruídos quando o jogador se afastasse. A persistência salva esses dados no armazenamento local do servidor e os recarrega sob demanda.

---

## 2. Como funciona por dentro
O fluxo de gravação e leitura baseia-se no framework **Caskara** (uma biblioteca utilitária de banco de dados do ecossistema de mods do Hytale) e é gerenciado em `SimNPCPersistence.java` e `SimPlayerPersistence.java`:

```
Servidor Descarrega Chunk ou Desliga
   ↓
SimTaleEventHandler intercepta o descarregamento da entidade NPC
   ↓
Extrai os dados de SimNPCComponent para SimNPCData (estrutura POJO)
   ↓
Chama Caskara.save(npcUuid.toString(), npcData)
   ↓
Caskara grava o arquivo JSON correspondente na pasta de dados do mod
```

*   **`SimNPCData`**: Objeto estruturado serializável contendo a cópia das variáveis de humor, necessidades, família, memórias e preferências.
*   **Gestão de Relacionamentos**: O mapa de relacionamentos do NPC é serializado diretamente como um dicionário chave-valor indexado por UUID de jogador, registrando os pontos de afinidade, romance e amizade independentemente.

---

## 3. Decisões de Design e Por Quê
*   **Separação entre Componente e POJO de Dados**:
    *   *Decisão*: Manter `SimNPCComponent` (classe ativa do Hytale que herda as mecânicas ECS) separado de `SimNPCData` (classe pura de dados/POJO usada na persistência).
    *   *Por quê*: O Hytale não consegue serializar diretamente componentes do ECS que contêm referências a entidades vivas do servidor (`Ref<EntityStore>`), threads ou buffers gráficos. Separar em uma representação limpa de dados resolve problemas de travamento ao serializar formatos complexos.

---

## 4. Nível de Complexidade e Robustez
*   **Nível**: 🟢 Simples e estável
*   **Análise**: Operações delegadas ao framework Caskara. Muito estável, com baixa probabilidade de falhas de gravação, exceto por conflitos de concorrência se threads tentarem ler e escrever no mesmo arquivo simultaneamente.

---

## 5. Dificuldades Encontradas e Resolvidas

### Desserialização corrompendo novos campos de preferências
*   **Problema**: Após atualizar a classe de preferências (`NPCPreferences`) com novos campos de itens favoritos, o Caskara falhava ao carregar NPCs criados anteriormente, gerando exceções de leitura de JSON.
*   **Correção**: Implementada validação de nulos e fallbacks no construtor de carregamento de `SimNPCPersistence`. Se um campo de preferências não for encontrado no JSON antigo desserializado, o sistema inicializa uma lista/set vazia ou regenera as preferências padrões, garantindo retrocompatibilidade (backward compatibility).

---

## 6. Pontos em Aberto / Dívida Técnica
*   **Salvamento Síncrono no Tick**: O salvamento automático de alguns dados pontuais ocorre de forma síncrona na thread do servidor em resposta a eventos rápidos (como a compra de aliança). Embora seguro para poucos jogadores, o salvamento síncrono pode causar pequenos engasgos em servidores com dezenas de jogadores simultâneos. Deve ser movido para um salvamento em lote (batching) assíncrono.

---

## 7. Perguntas Frequentes (FAQ)

### Onde os arquivos do Caskara ficam salvos fisicamente?
Os arquivos ficam armazenados no diretório do servidor local sob a pasta de dados do mod (geralmente sob `/data/caskara/` ou similar configurado pelo Hytale), salvos em formato de arquivos estruturados em JSON legíveis.

### O que acontece se eu apagar os arquivos JSON do Caskara com o servidor desligado?
Todos os NPCs do SimTale serão resetados. Suas memórias, casamentos, filhos e afinidades com os jogadores voltarão ao estado padrão de desconhecido na próxima vez que o servidor for ligado e as entidades spawnarem.
