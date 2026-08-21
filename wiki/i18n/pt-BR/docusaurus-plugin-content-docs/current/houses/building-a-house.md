---
sidebar_position: 1
title: Construindo uma casa
---

# Construindo uma casa

Uma casa não é apenas qualquer construção. O mod valida a estrutura antes de aceitá-la, e um NPC só
se muda para uma construção aprovada.

## Os requisitos

### Estrutura

O espaço deve estar **fechado**: paredes e um teto sem nenhuma abertura por onde a checagem possa vazar. Portas
contam como parede fechada.

O interior é limitado a **512 blocos**. Passando disso, a checagem desiste e rejeita a casa — esse
limite existe para que uma caverna aberta não seja confundida com uma mansão. (A menos que você esteja tentando construir as minas de Moria, 512 blocos costumam ser mais do que suficientes).

### Mobília obrigatória

| Requisito | Qualquer bloco cujo id contenha |
|---|---|
| **Fonte de luz** | `torch` (tocha), `lantern` (lanterna), `candle` (vela), `campfire` (fogueira), `glow` (brilho), `lamp` (lâmpada), `chandelier` (lustre) |
| **Assento** | `chair` (cadeira), `stool` (banquinho), `bench` (banco), `seat` (assento), `sofa` (sofá), `couch` (sofá) |
| **Superfície** | `table` (mesa), `workbench` (bancada), `desk` (escrivaninha), `counter` (balcão) |

### Opcional, mas você vai querer

| Item | Por quê |
|---|---|
| **Cama** | Sem uma, ninguém mora lá. A casa é identificada *pela sua cama*. |
| **Baú** | Sem um, os residentes não têm de onde tirar comida. |

## Checando

Aponte um **Projeto de Casa** (House Blueprint) para uma cama registrada.

![Projeto de Casa](/img/HouseBlueprint.png)

A ferramenta dirá se a estrutura passou e, se não, **o que está faltando**. Ela também
relata quantos blocos do interior foram visitados, e quantas portas e baús foram encontrados.

:::tip Chunks descarregados atrapalham
Se parte da casa estiver em um chunk descarregado (unloaded chunk), a checagem sinaliza o resultado como incompleto em vez
de rejeitá-lo. Fique perto da casa ao fazer a checagem.
:::

## A casa é identificada por sua cama

Este é o detalhe mais importante e o mais fácil de errar: **a identidade da casa vem
da cama**.

O que isso significa na prática:

- Duas camas no mesmo cômodo podem se tornar duas casas. (Oh my god, they were roommates...)
- Quebrar a cama do residente libera a casa.
- Mover a cama pode ser interpretado como uma casa diferente.

Essa é uma limitação conhecida, e transformá-la em um identificador próprio já está nos planos para o futuro.

## Portas

Uma porta ocupa quatro blocos, e duas portas lado a lado formam uma porta dupla. Os NPCs as abrem e as fecham
enquanto passam. (Hodor ficaria orgulhoso).

## A seguir

[Camas e moradores](beds-and-residents.md) — como um NPC reivindica uma cama e o que acontece quando dois
deles querem a mesma.
