---
sidebar_position: 4
title: Relacionamentos
---

# Relacionamentos

Cada NPC mantém um relacionamento separado com cada jogador e com outros NPCs.

## Os números

| Valor | Significado |
|---|---|
| **Amizade** (Friendship) | Proximidade geral |
| **Romance** | Interesse romântico |
| **Confiança** (Trust) | Disposição para aceitar pedidos |
| **Afinidade** (Affinity) | Reação de curto prazo às suas últimas ações |

## Escada de status

`DESCONHECIDO` (`UNKNOWN`) → `ESTRANHO` (`STRANGER`) → `CONHECIDO` (`ACQUAINTANCE`) → `AMIGO` (`FRIEND`) → `BOM AMIGO` (`GOOD_FRIEND`) → `MELHOR AMIGO` (`BEST_FRIEND`)

Ramo romântico: `NAMORANDO` (`DATING`) → `NOIVOS` (`ENGAGED`) → `CASADOS` (`MARRIED`).
Ramo negativo: `RIVAL` e `INIMIGO` (`ENEMY`).

O status muda o que ela diz a você. A mesma saudação tem palavras diferentes para um estranho e para
um cônjuge.

## Como aumentar

| Ação | Efeito |
|---|---|
| Conversar | Ganho pequeno, mas confiável |
| Contar uma piada | Depende do senso de humor dela |
| Flertar | Romance, se ela for receptiva |
| Dar um presente favorito | Grande ganho |
| Alimentá-la quando estiver com fome | Grande ganho — maior que o de um presente comum |
| Insultar | Perda, e ela se lembra disso |

## Ela se lembra

Os NPCs guardam a memória dos eventos. Insultar alguém tem um efeito que dura além do momento: por um tempo
depois disso, ela te cumprimentará de forma diferente.

## NPC para NPC

Os NPCs conversam entre si por conta própria quando a necessidade social deles cai. Uma conversa aumenta a necessidade social de
ambos os lados e constrói amizade entre eles, e essa amizade sobrevive a uma reinicialização do servidor.

O humor se espalha através dessas conversas. Um NPC `AGRESSIVO` (`AGGRESSIVE`), ou dois que já são inimigos, transformam
a conversa em uma discussão: ambos saem de mau humor e gostando menos um do outro.

:::note Ninguém é arrastado para fora da cama
Um NPC que está dormindo ou trabalhando nunca é escolhido como parceiro de conversa. E se a energia acabar no
meio da conversa, ela abandona o bate-papo e vai para a cama — o parceiro que ficou para trás não congela.
:::

## Casamento

Dê uma aliança de casamento (`simtale:wedding_ring`) para um NPC com alto nível de romance e amizade e ela
aceitará. NPCs casados dividem a mesma casa.

Se os números não forem altos o suficiente, ela te rejeitará.
