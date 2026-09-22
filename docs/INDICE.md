# Índice da Documentação Técnica (SimTale)

Bem-vindo à documentação oficial do mod **SimTale** em formato de monografia técnica (TCC). Use os links abaixo para navegar pelos capítulos e sistemas do projeto.

---

## 📖 Visão Geral e Arquitetura
1.  **[Visão Geral do Projeto](00-visao-geral.md)**: O que é o SimTale, stack tecnológica, escopo de gameplay e objetivo final.
2.  **[Arquitetura do Servidor](01-arquitetura-geral.md)**: O ciclo do Entity Component System (ECS), loops de ticks do servidor e a infraestrutura de dados.

---

## ⚙️ Sistemas Internos (Capítulos de Engenharia)
3.  **[Comportamento de NPCs](sistemas/npc-comportamento.md)**: Personalidades, atributos, traços de caráter e preferências de itens/comidas.
4.  **[Tiers de Amizade e Diálogos](sistemas/friendship-tier-dialogo.md)**: Como a afinidade destrava níveis sociais e como os diálogos localizados são mapeados e traduzidos.
5.  **[IA de Rotina e Sono](sistemas/routine-ai.md)**: Busca de caminhos (pathfinding), throttling de navegação, e o ciclo de sono com registro físico de camas.
6.  **[Plumbob Diamante Visual](sistemas/plumbob-system.md)**: Indicação visual do humor e necessidades acima das entidades.
7.  **[Ciclo de Vida e Crescimento](sistemas/lifecycle-crescimento.md)**: Gravidez, sintomas físicos de gestação, transição de custódia e co-parentalidade com turnos automatizados.
8.  **[InteractionManager e IA Generativa](sistemas/interacoes-jogador-npc.md)**: Interações sociais baseadas em cadeias funcionais de regras (Rules Engine) e chamadas assíncronas para LLMs.
9.  **[ConstructionHelper e Previews](sistemas/construction-helper.md)**: Renderização de ghosts de construções, compressão de coordenadas em longs e a prova matemática de otimização de cantos (8 ➔ 4).
10. **[Persistência Caskara](sistemas/persistencia-db.md)**: Como os dados estruturados em JSON são gravados e restaurados de forma transparente.
11. **[Reconhecimento de Casas](sistemas/reconhecimento-casas.md)**: Algoritmo de flood fill 3D, verificação de mobílias (Terraria-style) e segurança de baús.
12. **[Registro de Mobília](sistemas/registro-mobilia.md)**: Âncoras de móveis multibloco pelo dado de *filler*, os registros de camas e baús, e por que classificação por nome falha.
13. **[Fome, Sono e Morte](sistemas/fome-e-sono.md)**: Sono pelo relógio do mundo, turno noturno dos guardas, tiers de comida pelo dado do item e morte por inanição acumulada.

---

## 🎨 Assets e Scripts Auxiliares
14. **[Modelo do Bebê Swaddle](assets/baby-swaddle.md)**: Otimização de caixas 3D e atlas de textura no Blockbench.
15. **[Redimensionamento de Nodes (Python)](assets/cosmeticos-node-scales.md)**: Como deformamos acessórios e roupas de adultos para proporções de crianças.

---

## 📜 Histórico e Referências Rápidas
16. **[FAQ e Perguntas Rápidas](perguntas-rapidas.md)**: O principal guia de respostas curtas sobre o funcionamento geral do mod.
17. **[Bugs Históricos Resolvidos](historico/bugs-resolvidos.md)**: Changelog com problemas graves de performance, duplicação e navegação corrigidos no projeto.
18. **[Decisões de Design (ADRs)](historico/decisoes-arquiteturais.md)**: Justificativa de escolhas arquiteturais fundamentais do projeto.
19. **[Glossário de Termos](glossario.md)**: Lista de definições rápidas para conceitos do mod (ex: Leash Point, Plumbob, etc.).
20. **[Checklist de Testes](../testing_checklist.md)**: Roteiro de teste em jogo, sistema por sistema, com o que já foi validado e o que ainda falta.
21. **[Experimentos](experimentos.md)**: Protótipos rápidos ainda não confirmados em jogo — hoje, o sistema de fantasias sazonais de NPC.
