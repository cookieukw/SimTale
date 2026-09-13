---
sidebar_position: 5
title: Perguntas Frequentes (FAQ)
---

# Perguntas Frequentes (FAQ)

### Por que meu NPC não dorme?

Provavelmente não há nenhuma cama registrada. Use o **Livro do Estalajadeiro** (Innkeeper's Ledger) — se a lista estiver vazia, a cama
nunca foi registrada. Colocar uma nova cama a registra imediatamente.

Verifique também se ela realmente reivindicou uma casa: um NPC sem casa não tem uma cama para onde voltar.

### Por que ela não come, com um baú cheio de comida bem ali?

O baú precisa pertencer a uma **casa reconhecida**. Baús ao ar livre e baús gerados pelo
próprio mundo são ignorados de propósito.

Use a **Lupa do Intendente** (Quartermaster's Glass): se a linha disser "no house" (sem casa), esse é o problema. Transforme o espaço ao redor
em uma casa válida.

### Ela ficou com raiva de um presente que achei que ela gostaria

Abra o painel e leia a lista de coisas **odiadas**. Cada NPC odeia até seis coisas diferentes, e a quantidade de opções de itens
é pequena o suficiente para que colisões de gosto entre NPCs sejam comuns.

### Por que tudo é tão lento?

É proposital. Um NPC leva cerca de quatro horas no jogo para começar a procurar comida e treze para chegar
ao fundo do poço. O mod foi feito para rodar em segundo plano enquanto você joga, não para ser supervisionado a todo instante.

### Os guardas dormem alguma vez?

Sim — durante o **dia**. Eles fazem a vigia noturna, então a rotina deles é invertida. A
energia deles cai como a de todos os outros e é recuperada na cama, apenas em um horário oposto.

### Os NPCs conseguem abrir portas?

Sim. Eles abrem ao passar e fecham atrás deles.

### Os NPCs sobrevivem à reinicialização do servidor?

Sim. Nomes, necessidades, relacionamentos, casas e empregos são todos mantidos salvos.

### Posso mudar a rapidez com que ficam com fome?

Ainda não é possível fazer isso por um arquivo de configuração (config) — os valores são constantes no código. Veja
[Balanceamento](/admin/balancing).

### Como faço para os NPCs praticarem hobbies?

Você não precisa dar ordens. Apenas coloque blocos que contenham `leisure_fishing` (pesca), `leisure_mining` (mineração) ou `leisure_gardening` (jardinagem) no mapa. Se o NPC tiver aquele hobby e sua diversão estiver baixa, ele irá interagir com o bloco sozinho.

### Como eu crio uma vila? Preciso de um bloco central?

Não. Uma vila se forma automaticamente quando você constrói casas próximas umas das outras (cerca de 40 blocos entre as camas). A vila se expande naturalmente conforme você constrói mais casas na mesma região.

### Como os NPCs têm filhos e como cuidar deles?

NPCs casados que moram juntos podem ter filhos. Os bebês precisam de cuidados, e você (ou os pais) podem carregá-los nos ombros ou nas costas enquanto realizam outras tarefas. Não há limite de carregar apenas uma por vez: é possível empilhar até dez crianças ao mesmo tempo, uma em cima da outra.

### Crianças podem dividir a cama com os pais (co-sleeping)?

Sim! Crianças pequenas (`TODDLER`) e crianças (`CHILD`) procuram automaticamente a cama de seus pais (`FamilyBonds.findParentBed`). Se o pai ou mãe já estiver dormindo nela, a criança deita junto no mesmo leito sem precisar de uma cama avulsa e sem expulsar ninguém.

### Crianças podem trabalhar ou ajudar nas tarefas?

Crianças não podem receber empregos formais de adultos (`MINER`, `FARMER`, etc.) e recusarão ferramentas de trabalho. No entanto, elas podem praticar hobbies livremente (como pescar ou jardinagem) para recuperar diversão, brincar perto da horta da família e simular ajuda doméstica.

### Como faço para colocar a criança de volta no chão?

A forma mais confiável é **agachar e pular ao mesmo tempo** — ela desce na hora, sem precisar mirar em nada. Você também pode agachar e clicar com o botão direito **em um bloco** (é obrigatório mirar em um bloco de verdade; clicar no ar não funciona), ou simplesmente digitar `/simtale putdown` no chat, que sempre funciona não importa para onde você esteja olhando.

### Um NPC morreu. Tem como trazê-lo de volta?

Sim. Quando a Ceifadora (Grim Reaper) aparecer para coletar a alma, interaja com ela durante o ritual segurando um <img src="/img/Ingredient_Voidheart.png" width="20" style={{verticalAlign: "middle"}} /> Coração do Vazio (`Ingredient_Voidheart`) para cancelar a coleta e reviver o NPC.
