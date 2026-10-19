---
sidebar_position: 3
title: Vilas
---

# Vilas

Construa casas próximas umas das outras e elas se tornarão uma vila. Você não precisa posicionar nenhum item para que isso aconteça,
e não há nenhum marcador ou item que possa ser perdido.

## Como se forma

Duas casas pertencem à mesma vila quando suas camas estão a até **64 blocos** de distância uma da outra,
e isso gera uma corrente: se a casa A está perto da B, e a B está perto da C, todas as três formam uma vila, mesmo que A e C estejam
muito distantes entre si.

Portanto, uma vila cresce à medida que você constrói — expandindo-se a partir do que já existe. Uma longa rua de casas
com 30 blocos de distância entre elas formará uma única vila, por maior que a rua fique.

O centro fica no meio das camas, e a vila se estende dali até a casa mais distante
acrescida de uma margem de 24 blocos.

## Baús da vila

Um baú colocado dentro da vila e fora de todas as casas é um **baú global**. Ninguém é dono dele e
qualquer morador pode pegar comida nele — inclusive NPCs que não têm casa. Se você colocar o baú
dentro de uma casa, ele volta a ser privado.

Os moradores também usam o baú global para o excedente. Eles preferem o baú da própria casa, mas às
vezes escolhem o da vila mesmo quando ainda há espaço em casa, o que mantém o estoque comunitário
em movimento.

## Marcadores no mapa

O mapa usa três marcadores relacionados:

| Ícone | Significado |
|---|---|
| Ícone de Spawn | Centro da vila |
| Casa verde | Casa registrada com pelo menos um morador |
| Casa azul | Casa registrada, mas ainda sem morador |
| Casa cinza | Cama registrada que não pertence a nenhuma casa — ninguém a pegou ainda |

O marcador da casa fica no **centro do interior**, não na primeira cama, então uma extensão ou asa
nova move o marcador junto com a construção. Uma casa separada só ganha marcador quando um NPC pega
a cama; até lá ela aparece como cama sem dono.

## Ela desaparece se você a destruir

Uma casa só existe por causa de sua cama. Quebre a cama e a casa deixará de existir, e a vila será recalculada
sem ela. Quebre todas as camas e não sobrará vila alguma.

:::info Diferente de outros jogos de propósito
Em outros jogos de construção de blocos, o centro da vila costuma ser algo fixo. Você pode derrubar todas as construções e o jogo ainda
tratará as ruínas como uma vila. Aqui, a vila é calculada a partir das casas que existem naquele exato
momento, então não sobra nada para trás que possa estar errado.
:::

## O que ela muda

**NPCs sem casa param de vagar sem rumo.** Anteriormente, um NPC sem-teto ficava à deriva — cada passeio começava
onde o último terminava, então ele se afastava cada vez mais e você tinha que ir procurá-lo.
Agora ele passeia apenas ao redor da vila.

O limite é flexível de propósito: um NPC ainda sai da vila para trabalhar, pescar ou buscar comida.
Ele apenas não vai mais embora à toa.

**Guardas patrulham a borda.** Um guarda sem inimigos para lutar faz um circuito caminhando pela fronteira da
vila, que é de onde os problemas geralmente vêm. Um guarda que não pertence a uma vila fica parado onde está.
