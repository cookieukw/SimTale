---
sidebar_position: 2
title: Personalidade e gostos
---

# Personalidade e gostos

Cada NPC é gerado (rolado) no momento do spawn e nunca muda. É isso que faz com que dois moradores com o mesmo emprego
se comportem de forma diferente.

## Traços

| Traço | Efeito |
|---|---|
| `AGRESSIVO` (`AGGRESSIVE`) | Conversas podem virar discussões |
| `CARENTE` (`NEEDY`) | Leva insultos muito a sério, perdendo muita afinidade |
| `TÍMIDO` (`SHY`) | Diálogos e reações únicas a flertes românticos |
| `PREGUIÇOSO` (`LAZY`) | Perde energia duas vezes mais rápido |
| `GANANCIOSO` (`GREEDY`) | Dá mais valor a presentes |
| `PARANOICO` (`PARANOID`) | Reage mal a presentes |
| `ENGRAÇADO` (`FUNNY`) | Perde diversão pela metade da velocidade |
| `LEAL` (`LOYAL`) | Amizades não decaem com o tempo (Planejado) |

## Gostos

Cada NPC sorteia:

- 2–3 **comidas favoritas** e 2–3 **comidas odiadas**
- 2–3 **itens favoritos** e 2–3 **itens odiados**

Isso dá até seis coisas de que ele não gosta. O painel de interação mostra a **lista inteira** como ícones, não
apenas uma amostra — quando o painel mostrava apenas o primeiro item, fazia os NPCs parecerem não odiar algo que, na verdade, odiavam
muito.

Dar um favorito é sempre bom. Dar algo odiado custa afinidade e piora o humor.

:::tip Gostos coincidem
A variedade de comidas é pequena, então é comum que dois NPCs odeiem a mesma coisa. Se um presente der errado com
alguém de quem você não esperava, abra o painel e verifique a lista real antes de achar que é um bug.
:::

## Estação e clima favoritos

Por enquanto, é apenas visual — mostrado no painel, usado para dar um tom nos diálogos.

## Humor

O humor é representado pelo cristal (plumbob) flutuando sobre a cabeça: `NEUTRO` (`NEUTRAL`), `FELIZ` (`HAPPY`), `BRAVO` (`ANGRY`), `TRISTE` (`SAD`), `ASSUSTADO` (`SCARED`), `COM SONO` (`SLEEPY`), `ANIMADO` (`EXCITED`), `ENTEDIADO` (`BORED`).

Ele reage ao que acontece: comer o prato favorito, ser insultado, uma boa conversa, praticar um
hobby. O humor é **contagioso** — um NPC feliz conversando com um triste pode animá-lo.

Qualquer necessidade caindo abaixo de 10 o deixará miserável, independentemente de qualquer outra coisa.
