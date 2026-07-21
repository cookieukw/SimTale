# Visão Geral do SimTale

> **TL;DR**: SimTale é um mod de simulação de vida e relacionamento social para Hytale. Ele introduz necessidades biológicas, traços de personalidade, rotinas autônomas, relacionamentos em múltiplos eixos e sistemas de ciclo de vida (gravidez, co-parentalidade e crescimento) inspirados no clássico The Sims, integrando inteligência artificial generativa assíncrona.

---

## 1. Introdução e Objetivo do Projeto
O SimTale foi concebido para transformar a experiência de jogo em Hytale, convertendo NPCs (personagens não-jogáveis) estáticos em indivíduos dinâmicos, com rotinas lógicas, necessidades biológicas, ciclos de vida e relacionamentos complexos com o jogador e outros NPCs.

O objetivo do mod é enriquecer o gameplay trazendo mecânicas inspiradas em simuladores de vida (como *The Sims*), permitindo que os jogadores construam laços, casem-se, constituam famílias, organizem rotinas de moradia e trabalho, e observem seus filhos crescerem e se tornarem adultos independentes que auxiliam na economia local.

## 2. Stack Tecnológica
O projeto é desenvolvido sobre a seguinte base técnica:
*   **Linguagem Core**: Java (OpenJDK 25) para a lógica de jogo, ticks, comportamento ECS e interações.
*   **Engine Core (Hytale SDK)**: Implementação construída sob a arquitetura de ECS (Entity Component System) nativa de Hytale, utilizando ticks de sistemas integrados de servidor.
*   **Persistência**: **Caskara**, um framework de persistência ORM local de Hytale, utilizado para serializar e salvar dados estruturados de NPCs (`SimNPCData`), jogadores (`SimPlayerComponent`) e filhos (`GrowthComponent`).
*   **Bibliotecas e Interfaces**:
    *   `RuneCore`: Biblioteca de utilitários auxiliares (como `EffectHelper` e `StatHelper`) utilizada para aplicar efeitos de status ao jogador e NPCs.
    *   `JOML (Java OpenGL Math Library)`: Utilizada para cálculo vetorial em 3D, essencial para posicionamento, construções e IA de movimentação.
    *   `Blockbench`: Editor 3D usado na modelagem e animação de assets proprietários do mod (ex: o bebê swaddle).

## 3. Arquitetura Funcional Geral
O mod é dividido em três camadas principais:
1.  **Camada ECS (Entity Component System)**: Sistemas periódicos que controlam a IA autônoma, decaimento de necessidades, gravidez e crescimento a cada tick de servidor.
2.  **Camada de Interação e UI**: Interfaces visuais customizadas (como `NPCInteractionPage` e `PlayerPregnancyPage`) que permitem interações manuais (conversar, presentear, casar, designar trabalho) em tempo real.
3.  **Camada de IA Generativa**: Processamento assíncrono que conecta as ações do jogo a um provedor HTTP de Large Language Models (LLM), gerando respostas de diálogos ricas e personalizadas com base na personalidade, relacionamento e histórico do NPC.

---

## 4. Perguntas Frequentes (FAQ)

### Por que o SimTale não foi desenvolvido com scripts do lado cliente?
Hytale executa a lógica de simulação de entidades, inventário e persistência rigidamente no servidor para evitar trapaças e garantir consistência do mundo. O cliente apenas renderiza a interface visual definida em arquivos `.ui` e processa comandos enviados pelo servidor.

### Qual o papel do framework Caskara no projeto?
O Caskara é responsável por salvar e carregar os estados dos NPCs offline. Sem ele, todos os dados de relacionamentos, níveis de necessidade e status de gravidez seriam perdidos sempre que o servidor fosse reiniciado ou um NPC entrasse em descarregamento de chunk.
