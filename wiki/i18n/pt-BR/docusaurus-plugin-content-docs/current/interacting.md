---
sidebar_position: 4
title: Interagindo com NPCs
---

# Interagindo com NPCs

Aponte para um NPC e aperte **F**, ou clique com o botão direito. O painel de interação será aberto.

<div style={{textAlign: 'center'}}>
  <img src="/img/interacting_panel.png" alt="Painel de Interação do NPC" />
</div>

## O que o painel mostra

| Seção | Conteúdo |
|---|---|
| Cabeçalho | Nome, trabalho, humor e **Fase da Vida** (`BABY`, `TODDLER`, `CHILD`, `TEEN`, `ADULT`) |
| Necessidades | Fome e energia, codificadas por cor conforme a gravidade |
| Traços | Traços de personalidade |
| Identidade | Trabalho e hobby, como ícones de itens |
| Gostos | Vitrine de itens favoritos e odiados, como ícones interativos |
| Família | Pais, cônjuges e filhos |
| Status | Relacionamento, amizade, afinidade ou **Vínculo Filial** (para seus filhos, mostrando níveis de carinho parental como Filho/Filha em vez de títulos românticos) |

As cores na barra de fome seguem os limites que a rotina realmente usa: verde acima de 50, amarelo abaixo de 50, laranja abaixo de 25, vermelho abaixo de 5. No nível 5, o NPC chora e abandona todas as tarefas.

### Enquadramento Dinâmico de Câmera
Ao abrir o painel de interação, a câmera ajusta dinamicamente a distância, elevação e inclinação vertical com base na caixa de colisão real (bounding box) e escala do NPC. Isso garante que crianças pequenas fiquem perfeitamente centralizadas na tela, em vez da câmera mirar no ar vazio acima de suas cabeças.

## Ações

| Botão | O que ele faz |
|---|---|
| **Conversar (Chat)** | Bate-papo. Dá um pequeno aumento na Amizade (+5) e Afinidade (+5 a +10). Se a IA Gerativa estiver ativada, o NPC escreverá ativamente uma resposta. |
| **Contar piada (Joke)** | Dá certo ou fracassa dependendo do senso de humor dela. Falha totalmente se forem inimigos. Anima parceiros tristes/irritados. NPCs com o traço `ENGRAÇADO` (`FUNNY`) dão um aumento massivo de +15 na afinidade. |
| **Flertar (Flirt)** | Exige uma boa base de relacionamento (desabilitado para crianças). Diferente de certo jogo simulador de vida, você não pode simplesmente floodar a interação de flerte 50 vezes seguidas até eles casarem com você. Falha garantida e queda de relacionamento se forem inimigos, estranhos ou se o NPC estiver irritado. Quando bem-sucedido, o NPC cora com expressão alegre, sopra um beijo e solta partículas de corações (`Hearts`) sobre a cabeça! |
| **Dar presente (Gift)** | Entrega o que você estiver segurando. (Veja abaixo a lógica de presentes) |
| **Insultar (Insult)** | Custa até -30 de confiança e afinidade, e ela se lembrará disso. (Clementine vai se lembrar disso). NPCs muito ofendidos ou agressivos revidarão te empurrando fisicamente para trás com animação de soco e repulsão (*knockback*) real! |
| **Dar bronca (Scold)** | Específico para os seus filhos. As reações variam conforme a idade: Adolescentes ficam com raiva, Adultos ficam entediados, e Bebês/Crianças pequenas ficam tristes. Dar bronca repetidamente reduz a confiança e afinidade. |
| **Carregar no Colo/Costas (Carry)** | Permite carregar bebês, crianças pequenas ou crianças no colo/costas. Solte agachando e clicando com botão direito em um bloco ou digitando `/simtale putdown`. |
| **Atribuir trabalho (Assign job)** | Define o trabalho dela com base na ferramenta que você está segurando. Protegido: crianças pequenas recusam trabalho. |
| **Ver gravidez (View pregnancy)** | Abre o painel de gestação |
| **Inventário (Inventory)** | Abre o inventário dela |

### Cumprimentos de Proximidade & Bate-papo Ambiente
- **Cumprimento ao se Aproximar**: Ao chegar perto de um NPC ($\le 4.5m$), ele se vira para você, sorri, acena com a mão e envia uma saudação no chat conforme o nível de relacionamento (Cônjuge/Amor, Amigo, Estranho ou Inimigo). Conta com proteção anti-spam por jogador (janela de 15s) para que entrar em uma praça cheia de moradores não dispare múltiplos diálogos simultâneos. Totalmente configurável via `/simtale proximity` e `simtale-config.json` (incluindo a opção de desativar as mensagens no chat mantendo os acenos amigáveis).
- **Balões de Pensamento e Emotes (Thought Bubbles)**: NPCs expressam sentimentos, rotinas e reações através de balões 3D animados flutuando ao lado da cabeça (mais de 36 ícones de emojis kawaii, como corações apaixonados, sono Zzz, pedidos de comida ou carinhas convencidas).
- **Conversas NPC-com-NPC**: Quando dois moradores se encontram durante suas rotinas diárias, eles param frente a frente e conversam turno-a-turno. Os assuntos são contextuais (fazendeiros conversam sobre a lavoura, NPCs exaustos comentam sobre cama/sono, famintos falam de comida, e inimigos trocam ofensas antes de se empurrarem). O bate-papo é audível no chat em um raio de até 4 blocos.

<p align="center">
  <img src="/img/player_interacting_children.png" width="49%" alt="Jogador interagindo com crianças" />
  <img src="/img/children_playing.png" width="49%" alt="Crianças interagindo e brincando juntas" />
</p>


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

<div style={{textAlign: 'center'}}>
  <img src="/img/baby_care.png" alt="O Item Bebê" />
</div>

O <img src="/img/Baby.png" width="24" align="absmiddle"/> **Bebê** é inicialmente um item. Após algum tempo, ele se transformará e nascerá como um NPC criança. (Só não esqueça ele dentro de um baú, a menos que você queira uma criança muito confusa nascendo no seu estoque!)

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
