---
sidebar_position: 2
title: Camas e moradores
---

# Camas e moradores

## Reivindicando

Um NPC sem cama procura uma cama vazia por perto. Quando encontra uma, ele vai até lá, reivindica a cama, e
a partir de então aquela casa passa a ser o seu lar.

A reivindicação é exclusiva: uma cama, um morador. Casais são a exceção — eles dividem a mesma casa.

## Camas ocupam seis blocos

Uma cama não é um único bloco. Ela ocupa **seis**, e apenas um deles é a âncora (anchor).

Isso importa por um motivo que você pode notar no jogo: a pose de dormir é calculada a partir da âncora. Quando
o mod montava um NPC em um dos outros cinco blocos, a pessoa dormia torta, flutuando ao lado da
cama, ou atravessada nela como uma cruz.

Hoje, o mod resolve qualquer um dos seis blocos de volta para a âncora antes de colocar alguém na cama.

## Quem está dormindo onde

Use o **Livro do Estalajadeiro** (Innkeeper's Ledger).

Ele abre uma tela listando cada cama registrada com suas coordenadas e seu dono.

A tela também mostra a contagem total, assim o aviso "nenhuma cama registrada" pode ser diferenciado de "a lista não carregou".

## Quebrando uma cama

Quebrar a cama de um NPC que está dormindo faz com que ele acorde perfeitamente e libere a casa. Ela passará a procurar
outra cama.

## Cronograma de sono

| Quem | Dorme |
|---|---|
| Todo o resto da vila | à noite, a noite toda |
| Guardas | durante o dia |

Um NPC com a energia no máximo ainda vai para a cama ao anoitecer — quem manda é o relógio, não o cansaço. Ela
fica dormindo até de manhã, para não pular da cama no exato momento em que a energia chegar em 100%.

A exaustão ainda é um gatilho separado: um NPC que fica sem energia durante o dia tira um cochilo
e acorda quando estiver descansado.

:::tip Pular a noite os acorda
Executar o comando `time set day` acorda NPCs adormecidos imediatamente, porque acordar segue o relógio do mundo
em vez de um cronômetro fixo.
:::
