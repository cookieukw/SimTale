---
sidebar_position: 5
title: Família e crescimento
---

# Família e crescimento

## Gravidez

Um NPC casado pode engravidar. A gravidez se divide em três trimestres, e a velocidade de movimento cai
gradualmente conforme avança.

| Trimestre | Sintomas |
|---|---|
| 1º | Ligeiro aumento na perda de fome e energia |
| 2º | Aumento moderado na perda, lentidão moderada |
| 3º | Perda intensa, lentidão severa |

Abra o painel de **Ver gravidez** (View Pregnancy) na tela de interação para acompanhar o progresso: dia atual,
porcentagem, e tempo estimado restante em minutos reais.

A gravidez para o jogador também existe e segue um caminho próprio.

## Nascimento

No fim da gestação, o bebê nasce como um **item** que vai para o inventário. Você carrega o
bebê por aí, e pode entregá-lo para o outro pai/mãe.

![Item Bebê](/img/Baby.png)

## Fases de crescimento

| Fase | Notas |
|---|---|
| `BEBÊ` (`BABY`) | Carregado no inventário |
| `CRIANÇA PEQUENA` (`TODDLER`) | |
| `CRIANÇA` (`CHILD`) | Modelo de corpo reduzido. |
| `ADOLESCENTE` (`TEEN`) | Modelo levemente reduzido. |
| `ADULTO` (`ADULT`) | Rotina completa: trabalho, casa, relacionamentos |

As crianças crescem com o tempo por conta própria, e o tamanho do modelo aumenta a cada fase.

## Cuidados

Bebês precisam de cuidados. Passar o bebê de um pai para o outro divide o fardo, e existe uma
simulação offline para que o tempo que você passa fora do servidor ainda conte.

## Morte

Quando um NPC morre, o fluxo de morte do SimTale assume o controle: o corpo permanece no local e começa a sangrar visualmente. A Ceifadora (Grim Reaper) aparece
sozinha, caminha até o corpo, realiza o ritual de coleta de alma, deixa uma lápide e remove os registros
completamente. Interagir com a Ceifadora no meio do ritual segurando um Coração do Vazio (`Ingredient_Voidheart`) cancela a
coleta e revive o NPC.

![Grim Reaper Ceremony Placeholder](/path/to/reaper_ceremony.png)

:::note Nada mata um NPC ainda
A fome, de propósito, não mata. NPCs famintos apenas choram e param de trabalhar. Envelhecimento e doenças ainda não foram implementados.
Atualmente, o fluxo de morte só pode ser ativado por comandos administrativos de teste, que existem para que a Ceifadora possa ser testada sem precisar esperar por uma causa de morte que ainda não existe no jogo.
:::
