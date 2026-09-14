---
sidebar_position: 1
title: Necessidades
---

# Necessidades

Todo NPC possui cinco necessidades, todas começando em 100 e caindo com o tempo.

| Necessidade | O que ela motiva a fazer |
|---|---|
| <img src="/img/need_hunger.svg" width="22" height="22" align="absmiddle" /> **Fome** | Procurar por comida; se chegar no fundo, ele para de fazer qualquer outra coisa. (O bolo pode ser uma mentira, mas ainda enche a barriga) |
| <img src="/img/need_energy.svg" width="22" height="22" align="absmiddle" /> **Energia** | Ir para a cama |
| <img src="/img/need_social.svg" width="22" height="22" align="absmiddle" /> **Social** | Procurar outros NPCs para conversar |
| <img src="/img/need_fun.svg" width="22" height="22" align="absmiddle" /> **Diversão** | Sair para praticar um hobby. (Muito trabalho e pouca diversão fazem do NPC um aldeão muito chato) |
| <img src="/img/need_hygiene.svg" width="22" height="22" align="absmiddle" /> **Higiene** | Entrar na água para tomar banho. (Remover a escada da piscina não vai prendê-los lá dentro de verdade) |

Os traços alteram a velocidade. Um NPC `PREGUIÇOSO` (`LAZY`) gasta energia duas vezes mais rápido; um `ENGRAÇADO` (`FUNNY`) perde diversão pela metade da velocidade.

## Fome em detalhes

A fome é a necessidade com as consequências mais duras, por isso ela tem limites claros:

| Fome | O que acontece |
|---|---|
| abaixo de 50 | procura comida **quando estiver ocioso** |
| abaixo de 25 | **larga o que estiver fazendo** para comer |
| abaixo de 5 | começa a perder vida |

A interrupção aos 25 existe porque um NPC ocupado acabaria passando fome ao lado de uma despensa cheia —
antes, a fome só era checada enquanto ele estivesse ocioso.

### Linha do tempo

Começando de barriga cheia:

| Marco | Fome | Tempo decorrido |
|---|---|---|
| Procura comida se ocioso | 70 | ~4,2 h |
| Interrompe o que está fazendo | 25 | ~10,4 h |
| Para de trabalhar e chora | 5 | ~13,2 h |

### Fome não mata

Um NPC que fica sem comida não morre. Ele fica miserável e inútil: ele abandona seu emprego, seu
hobby e sua vida social, e fica desse jeito até que alguém o alimente. A morte é reservada ao envelhecimento
e a doenças, que ainda não foram implementados.

Ele ainda consegue pegar comida por conta própria — estar passando fome não o impede de caminhar até um baú, e
isso não interrompe o sono.

## O que a comida restaura

A comida é classificada pelos próprios dados de item do jogo em três níveis. Carne crua, ingredientes e colheitas
são de nível 1; qualquer coisa cozida ou preparada é de nível 2 ou 3.

| Nível | Fome | Saúde |
|---|---|---|
| 1 (cru) | +25 | +6 |
| 2 | +45 | +14 |
| 3 (cozido) | +65 | +24 |

Ao escolher de um baú, o nível importa mais que o gosto: uma torta odiada ainda ganha de um suculento, porém cru, pedaço de
carne que ele ama.

## Alimentando à mão

Dê comida para um NPC cuja fome está em 50 ou menos e ela a comerá na mesma hora em vez de guardar
no bolso — restaurando fome e vida, e rendendo muito mais gratidão a você do que um presente comum.

Comida odiada ainda alimenta. Ela come reclamando, ganha menos status e sofre uma queda no humor.

## Vendo os números

Fome e energia aparecem no topo do painel de interação, divididas em cores por gravidade. As cores
seguem os mesmos limites que a rotina usa, assim o painel e o comportamento nunca discordam.
