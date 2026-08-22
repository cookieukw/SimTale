---
sidebar_position: 4
title: Interagindo com NPCs
---

# Interagindo com NPCs

Aponte para um NPC e aperte **F**, ou clique com o botão direito. O painel de interação será aberto.

![Painel de Interação do NPC](/img/interacting_panel.png)

## O que o painel mostra

| Seção | Conteúdo |
|---|---|
| Cabeçalho | Nome, trabalho, humor |
| Necessidades | Fome e energia, codificadas por cor conforme a gravidade |
| Traços | Traços de personalidade |
| Identidade | Trabalho e hobby, como ícones de itens |
| Gostos | Tudo o que ela gosta e odeia, como ícones |
| Família | Pais e filhos |
| Status | Porcentagens de relacionamento, amizade e afinidade |

As cores na barra de fome seguem os limites que a rotina realmente usa: verde acima de 50, amarelo abaixo de 50, laranja abaixo de 25, vermelho abaixo de 5. No nível 5, o NPC chora e abandona todas as tarefas.

## Ações

| Botão | O que ele faz |
|---|---|
| **Conversar (Chat)** | Bate-papo. Dá um pequeno aumento na Amizade (+5) e Afinidade (+5 a +10). Se a IA Gerativa estiver ativada, o NPC escreverá ativamente uma resposta. |
| **Contar piada (Joke)** | Dá certo ou fracassa dependendo do senso de humor dela. Falha totalmente se forem inimigos. Anima parceiros tristes/irritados. NPCs com o traço `ENGRAÇADO` (`FUNNY`) dão um aumento massivo de +15 na afinidade. |
| **Flertar (Flirt)** | Exige uma boa base de relacionamento. Diferente de certo jogo simulador de vida, você não pode simplesmente floodar a interação de flerte 50 vezes seguidas até eles casarem com você. Falha garantida e queda de relacionamento se forem inimigos, estranhos ou se o NPC estiver irritado. Facilmente aceito por parceiros ou NPCs com o traço `TÍMIDO` (`SHY`). |
| **Dar presente (Gift)** | Entrega o que você estiver segurando. (Veja abaixo a lógica de presentes) |
| **Insultar (Insult)** | Custa até -30 de confiança e afinidade, e ela se lembrará disso. (Clementine vai se lembrar disso). Parceiros vão reagir muito mal. |
| **Dar bronca (Scold)** | Específico para os seus filhos. As reações variam conforme a idade: Adolescentes ficam com raiva, Adultos ficam entediados, e Bebês/Crianças pequenas ficam tristes. Dar bronca repetidamente reduz a confiança e afinidade. |
| **Atribuir trabalho (Assign job)** | Define o trabalho dela com base na ferramenta que você está segurando (ex: segurar uma enxada atribui Fazendeiro). |
| **Ver gravidez (View pregnancy)** | Abre o painel de gestação |
| **Inventário (Inventory)** | Abre o inventário dela |

## Presentes

O que ela acha de um presente depende, nesta ordem:

1. **Está na lista de favoritos?** Grande ganho (+30 afinidade).
2. **Está na lista de odiados?** Grande perda (-25 afinidade).
3. **Tem relação com o hobby?** Ganho sólido (+22 afinidade) — abaixo de um favorito explícito, acima de qualquer coisa genérica.
4. **É lixo?** (terra, areia, pedra, teia de aranha, ossos, veneno, sucata) Perda (-20 afinidade).
5. **Personalidade**: O traço `GANANCIOSO` (`GREEDY`) valoriza mais (+25 afinidade); o `PARANOICO` (`PARANOID`) reage mal (-10 afinidade).
6. **Qualquer outra coisa**: pequeno ganho por educação (+15 afinidade).

### Comida é um caso especial

Se a fome dela estiver em 70 ou menos e o presente for comestível, ela o comerá na mesma hora em vez de guardá-lo. Isso restaura a fome e a saúde, cancela o estado de passar fome, e altera sua diversão com base no gosto pessoal dela.

Acima de 70 de fome, a comida volta a ser tratada como um presente comum.

### O Item Bebê

O **Bebê** é inicialmente um item. Após algum tempo, ele se transformará e nascerá como um NPC criança. (Só não esqueça ele dentro de um baú, a menos que você queira uma criança muito confusa nascendo no seu estoque!)

## Casamento

Para pedir um NPC em casamento, você deve dar de presente uma **Aliança de Casamento** (Wedding Ring). (Um Anel para a todos governar... não, espera, franquia errada).
O pedido só será aceito se o relacionamento de vocês tiver pelo menos **80 de Romance** e **70 de Amizade**. Se aceito, o NPC se tornará seu cônjuge.

![Aliança de Casamento](/img/WeddingRing.png)

## Conversando com IA

O mod pode rotear as conversas através de uma Inteligência Artificial generativa para que as respostas sejam escritas em tempo real, em vez de serem escolhidas de uma lista. Isso deve ser ativado nas configurações do servidor — veja
[IA Gerativa](/admin/generative-ai).

:::note Apenas pelo painel
Atualmente, as respostas por IA funcionam apenas através do painel de interação. Digitar no chat normal do jogo sempre trará as respostas baseadas em roteiros predefinidos, mesmo com a IA ativada.
:::
