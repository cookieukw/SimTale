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
| `BEBÊ` (`BABY`) | Carregado como item no inventário ou colocado em berço/cuidados |
| `CRIANÇA PEQUENA` (`TODDLER`) | Anda pela casa, brinca, herda a cama dos pais para dividir o sono |
| `CRIANÇA` (`CHILD`) | Modelo com escala reduzida, explora, dorme na mesma cama dos pais ou em cama própria, pratica hobbies |
| `ADOLESCENTE` (`TEEN`) | Modelo levemente reduzido, já pode assumir empregos formais |
| `ADULTO` (`ADULT`) | Rotina adulta completa: carreiras, reivindicação de casa própria e relacionamentos independentes |

As crianças crescem com o tempo por conta própria, e o tamanho do modelo aumenta suavemente a cada fase.

## Vínculos Familiares e Co-Sleeping (Dividir Cama)

Através do sistema `FamilyBonds`, os NPCs mantêm laços hereditários com seus pais biológicos:

- **Divisão de Cama / Co-Sleeping**: Crianças pequenas (`TODDLER`) e Crianças (`CHILD`) não precisam de uma casa ou cama separada para descansar. Ao chegar o horário de dormir (`FINDING_BED`), elas buscam a cama registrada de seus pais (`FamilyBonds.findParentBed`). Se o pai ou a mãe já estiver deitado, a criança deita junto na mesma cama sem expulsar o progenitor.
- **Proteção Durante o Sono**: Crianças em fase de crescimento dentro de uma cama permanecem protegidas durante os ciclos de vida, sem travar nem serem expulsas da animação de descanso.
- **Reconhecimento Parental**: Interagir com o próprio filho abre uma interface adaptada para laços filiais, exibindo a fase da vida e o título de parentesco ("Filho", "Filha").

## Regras de Trabalho e Hobbies

O SimTale inclui proteção para menores:
- **Imunidade a Trabalho Formal**: NPCs nas fases `BABY`, `TODDLER` e `CHILD` não podem receber profissões de adultos. Tentar atribuir um emprego com uma ferramenta gerará uma recusa amigável ("Eu sou só uma criança!").
- **Hobbies e Ajuda Familiar**: Crianças ainda podem se divertir com atividades como pescar ou jardinagem quando a diversão baixar, podendo interagir perto de hortas ou rios da família sem assumir compromisso formal de trabalho.

## Cuidados

Bebês precisam de cuidados. Passar o bebê de um pai para o outro divide o fardo, e existe uma
simulação offline para que o tempo que você passa fora do servidor ainda conte. Você pode pegar e carregar crianças no colo ou nas costas quando necessário.

## Morte

Quando um NPC morre, o fluxo de morte do SimTale assume o controle: o corpo permanece no local e começa a sangrar visualmente. A Ceifadora (Grim Reaper) aparece
sozinha, caminha até o corpo, realiza o ritual de coleta de alma, deixa uma lápide e remove os registros
completamente. Interagir com a Ceifadora no meio do ritual segurando um Coração do Vazio (<img src="/img/Ingredient_Voidheart.png" width="20" style={{verticalAlign: "middle"}} /> `Ingredient_Voidheart`) cancela a
coleta e revive o NPC.

![Grim Reaper Ceremony Placeholder](/path/to/reaper_ceremony.png)

:::note Nada mata um NPC ainda
A fome, de propósito, não mata. NPCs famintos apenas choram e param de trabalhar. Envelhecimento e doenças ainda não foram implementados.
Atualmente, o fluxo de morte só pode ser ativado por comandos administrativos de teste, que existem para que a Ceifadora possa ser testada sem precisar esperar por uma causa de morte que ainda não existe no jogo.
:::
