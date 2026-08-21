---
sidebar_position: 6
title: Ferramentas e itens
---

# Ferramentas e itens

SimTale adiciona um conjunto de ferramentas craftáveis. Elas existem para que a vila possa ser compreendida de dentro do jogo, sem a necessidade de digitar comandos de debug.

## O que elas fazem

| Item | Apontar para | O que acontece |
|---|---|---|
| Teste de Gravidez (Pregnancy Test) | um morador | Diz se ela está esperando um bebê e de quanto tempo |
| Projeto de Casa (House Blueprint) | uma cama | Informa se o cômodo conta como uma casa e mostra seus contornos |
| Livro do Estalajadeiro (Innkeeper's Ledger) | qualquer coisa | Lista todas as camas registradas e quem dorme nelas. (Os textos sagrados!) |
| Lupa do Intendente (Quartermaster's Glass) | qualquer coisa | Lista todos os baús da vila e o que há dentro deles. (Enhance... Enhance... Enhance) |
| Diário do Inspetor (Inspector's Journal) | um morador | Mostra suas necessidades, humor, trabalho e o que está fazendo agora |
| Contrato de Imigração (Immigration Contract) | qualquer coisa | Convida um novo residente para se estabelecer na vila |
| Sino da Cidade (Town Bell) | qualquer coisa | Toca o sino da cidade, alertando os moradores próximos |

Mais um item pode ser craftado, mas ainda não tem função: o Bolo de Aniversário (Birthday Cake).

## Receitas de Criação (Crafting)

Todos os itens podem ser craftados em suas respectivas bancadas:

| Item | Bancada | Ingredientes |
|---|---|---|
| **Teste de Gravidez** | Mesa de Alquimia | 1x Flor Branca, 1x Tábuas de Madeira Macia, 1x Essência de Vida (Couve-flor) |
| **Projeto de Casa** | Fieldcraft | 1x Mapa, 1x Tinteiro, 1x Pergaminho |
| **Livro do Estalajadeiro** | Fieldcraft | 1x Pilha Pequena de Livros, 1x Tábuas de Madeira Macia, 1x Couro Leve |
| **Lupa do Intendente** | Bancada de Trabalho | 1x Cristal Branco, 1x Barra de Cobre |
| **Diário do Inspetor** | Fieldcraft | 1x Pilha Pequena de Livros, 1x Tinteiro |
| **Contrato de Imigração** | Fieldcraft | 1x Pergaminho, 1x Tinteiro, 1x Couro Leve |
| **Sino da Cidade** | Bancada de Trabalho | 3x Barra de Ouro, 2x Tábuas de Madeira Macia |

### Ícones dos Itens

![Projeto de Casa](/img/HouseBlueprint.png) ![Innkeeper Ledger Placeholder](/path/to/innkeeper_ledger.png) ![Quartermaster Glass Placeholder](/path/to/quartermaster_glass.png) ![Town Bell Placeholder](/path/to/town_bell.png)

## Projeto de Casa (House Blueprint)

Clique com o botão direito em uma **cama registrada** — a cama é o que faz um cômodo ser uma casa, então em qualquer outro lugar a ferramenta não tem como saber de qual cômodo você está falando.

Você recebe um veredito (válida, ou a lista do que está faltando), um resumo do tamanho interior, portas e baús, e o chão do cômodo se ilumina por cerca de doze segundos: verde se a casa for válida, vermelho se não for. O contorno é apenas no chão — preencher todo o interior substituiria o cômodo por um bloco colorido gigante e esconderia o que você está tentando ver.

:::note Ele de propósito não registra nada
Uma ferramenta de verificação não deve alterar o que ela verifica. Se o projeto registrasse a casa, você criaria residências por acidente enquanto as inspeciona. Casas continuam sendo criadas quando um NPC reivindica a cama.
:::

A única coisa que ele faz com perfeição: ele sabe exatamente a qual cama você se refere. Checar apenas por proximidade deixa de ser bom o suficiente no momento em que duas casas dividem uma parede.

## As três lentes

O Livro (Ledger), a Lupa (Glass) e o Diário (Journal) são visões apenas de leitura dos dados que o mod já guarda. Os dois primeiros abrem as telas de visão geral de camas e baús.

:::warning Por que não há botão de Teleporte neles
Os registros de camas e baús contêm cada entrada existente no mundo. Um item craftável com um botão de teleporte ao lado de cada entrada não seria uma ferramenta de vila, seria a viagem rápida mais quebrada do jogo. O mesmo raciocínio, embora menos dramático, vale para os botões de Desvincular e Remover: esses itens são lentes, nunca alavancas.
:::

O Diário não faz simplesmente um despejo de estado interno de debug. Um dump bruto mostraria o estado do papel (role state), slots de animação, flags de movimento e tempos de recarga de busca — o que você quer ver quando a IA está com problemas, mas que é puro ruído quando você só quer saber se alguém está com fome. O Diário relata as cinco necessidades, humor, trabalho, onde ela mora e o que está fazendo em palavras claras, em vez do nome interno da tarefa.
