---
sidebar_position: 3
title: Começando
---

# Começando

O caminho mais curto entre "Instalei o mod" e "Tenho uma vila viva".

## 1. Traga um NPC

Você precisa convidar um residente para iniciar sua vila. Faça (craft) um **Contrato de Imigração** (Immigration Contract) em uma bancada Fieldcraft usando:
- 1x Mapa/Pergaminho (`Deco_Scroll`)
- 1x Tinteiro (`Deco_Inkwell`)
- 1x Couro Leve (`Ingredient_Leather_Light`)

Use o contrato para invocar um novo residente. 

O NPC gerado recebe um dos **800 modelos visuais distintos** e é instanciado com propriedades aleatórias para nome, matriz de personalidade, traços comportamentais, ocupação, hobbies e preferências alimentares.

## 2. Construa uma casa

Um NPC sem casa vaga sem rumo e nunca dorme direito. O mínimo que conta como uma casa:

- paredes e um teto fechando o espaço
- uma **porta**
- uma **fonte de luz**
- um **assento**
- uma **mesa**
- uma **cama**

Para verificar sua construção, aponte um **Projeto de Casa** (House Blueprint) para a cama.

![House Blueprint Placeholder](/path/to/house_blueprint.png)

A ferramenta dirá se a estrutura é válida e o que está faltando. Detalhes em
[Construindo uma casa](houses/building-a-house.md).

## 3. Deixe que ela reivindique a cama

Assim que a casa estiver pronta, o NPC caminhará até a cama e registrará aquele lugar como seu. A partir de então ela mora lá: volta para dormir, come dos baús daquela casa e abre a porta ao entrar.

Use o **Livro do Estalajadeiro** (Innkeeper's Ledger) para verificar quem mora onde.

![Innkeeper Ledger Placeholder](/path/to/innkeeper_ledger.png)

## 4. Coloque comida

Coloque um baú **dentro da casa** e deixe comida nele. NPCs vão procurar por um baú dentro de um raio de 24 blocos quando estiverem com fome.

:::caution O baú deve pertencer a uma casa
NPCs usam apenas baús que pertencem a uma casa reconhecida. Um baú largado no meio do nada é ignorado — e é isso também que os mantém longe dos baús de tesouro espalhados pelo mundo.
:::

Veja o que eles realmente conseguem alcançar usando a **Lupa do Intendente** (Quartermaster's Glass).

![Quartermaster Glass Placeholder](/path/to/quartermaster_glass.png)

Ela lista cada baú registrado, a que casa ele pertence e quanta comida tem dentro.

## 5. Fale com ela

Aponte para o NPC e aperte **F**, ou clique com o botão direito. Isso abre o painel de interação, com fome, energia, humor, traços, gostos e as ações disponíveis.

![NPC Interaction UI Placeholder](/path/to/interaction_ui.png)

Os resultados das interações são calculados com base no status do relacionamento, humor e traços:
- **Flertar (Flirt):** Aceito por parceiros ou NPCs tímidos; rejeitado por inimigos e NPCs irritados.
- **Piada (Joke):** Falha totalmente com inimigos, melhora o humor de parceiros tristes/irritados.
- **Presente (Gift):** Comida dada quando a fome está abaixo de 70 será comida imediatamente, restaurando saúde e alterando a diversão. Dar um item de **Bebê** para uma criança acelera seu crescimento em um dia inteiro do jogo.

Veja [Interagindo com NPCs](interacting.md).

## 6. Deixe o tempo passar

O mod é deliberadamente lento. Começando de 100 de fome, um NPC leva cerca de sete horas do jogo para ficar genuinamente com fome. Aos 5 de fome, eles começam a morrer de fome, chorando e largando todas as tarefas até serem alimentados. Quinze horas do jogo sem comida não os matará, mas eles se recusarão a trabalhar.

---

## Problemas iniciais comuns

| Sintoma | Causa provável |
|---|---|
| O NPC não dorme | A cama não está registrada, ou não há uma casa válida. Verifique com o **Livro do Estalajadeiro**. |
| O NPC não come | O baú não pertence a uma casa. Verifique com a **Lupa do Intendente**. |
| Nenhum NPC aparece sozinho | NPCs não nascem mais automaticamente. Você deve craftar e usar um Contrato de Imigração. |
| A casa é rejeitada | Faltam móveis ou o espaço não está fechado. O **Projeto de Casa** diz qual o problema. |
