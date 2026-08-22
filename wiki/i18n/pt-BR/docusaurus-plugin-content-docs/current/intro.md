---
sidebar_position: 1
title: O que é o SimTale
slug: /
---

# SimTale

SimTale é um mod de simulação social para Hytale que implementa NPCs autônomos com rastreamento de estado individual para necessidades, personalidades, rotinas e relacionamentos.

O comportamento dos NPCs é guiado por seus estados internos e consultas ao ambiente, em vez de scripts fixos ou árvores de diálogo.

O mod inclui **800 variantes visuais distintas** de NPCs:
- 400 Adultos (200 Homens, 200 Mulheres)
- 400 Crianças (200 Meninos, 200 Meninas)

Durante a instanciação, cada NPC recebe propriedades aleatórias de personalidade, traços, hobbies e preferências de itens.

<div style={{display: 'flex', flexWrap: 'wrap', gap: '10px', justifyContent: 'center'}}>
  <img src="/img/variant_1.png" width="48%" alt="Variante NPC 1" />
  <img src="/img/variant_2.png" width="48%" alt="Variante NPC 2" />
  <img src="/img/variant_3.png" width="48%" alt="Variante NPC 3" />
  <img src="/img/variant_4.png" width="48%" alt="Variante NPC 4" />
</div>

## O que um NPC faz sozinho

- **Dorme à noite.** Quando a noite cai, ele larga o que estiver fazendo e vai para a própria cama. NPCs com o traço "Preguiçoso" vão dormir mais cedo (quando a energia cai abaixo de 60). Guardas fazem o turno oposto: acordados à noite, dormem de dia.
- **Come quando tem fome.** Procura por comida nos baús da casa onde mora (dentro de um raio de 24 blocos), e escolhe a melhor: comida cozida tem prioridade sobre carne crua, e ele evita o que odeia.
- **Vive em uma casa.** Reivindica uma cama e trata aquele lugar como seu. 
- **Pertence a uma vila.** Casas construídas perto umas das outras formam uma vila, calculada a partir das próprias construções. NPCs sem casa ficam perto do centro da vila em vez de vagar sem rumo.
- **Trabalha.** Fazendeiros colhem plantações (Cenoura, Trigo, Tomate, Milho), replantam sementes e guardam a colheita. Caçadores e Mineradores saem em expedições e voltam com espólios.
- **Conversa.** Procura outros NPCs em um raio de 20 blocos após ficar muito tempo sozinho, e o humor é contagiante.
- **Tem um hobby.** Alguém que gosta de pescar caminha até a água; um leitor vai para casa.
- **Envelhece.** Casa-se, engravida, tem filhos, e essas crianças crescem de bebê a adulto.
- **Passa fome.** Se a fome cair abaixo de 5, ele chora, para de trabalhar totalmente e abandona todas as tarefas até que alguém o alimente. Ele não morre de fome — a morte é reservada para envelhecimento e doenças.
- **Morre.** Quando um NPC chega ao fim da vida, ele entra em estado de morte. A Ceifadora (Grim Reaper) aparece para conduzir a cerimônia e coletar sua alma.

![Grim Reaper Ceremony Placeholder](/path/to/reaper_ceremony.png)

## Por onde começar

1. [Instalação](installation.md)
2. [Começando](getting-started.md) — chame seu primeiro NPC e dê a ele uma casa
3. [Construindo uma casa](houses/building-a-house.md)

## As outras trilhas

Esta seção é para **jogadores**. Se você gerencia um servidor ou quer trabalhar no código:

- **[Servidor](/admin/intro)** — comandos, IA gerativa, balanceamento e solução de problemas
- **[Desenvolvedor](/dev/intro)** — arquitetura, sistemas e como expandir o mod

:::note Documentação em andamento
O mod está em fase de testes e não tem um lançamento público. O comportamento descrito aqui pode mudar entre versões, e algumas partes ainda não foram validadas no jogo — onde for o caso, a página informará.
:::
