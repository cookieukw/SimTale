# SimTale — verificação dos sistemas novos (pesca, lenhador, emoções, cadeiras, camas de criança, presentes, profissões) + proposta de armas por categoria

Verificação feita lendo o código-fonte atual (não joguei em servidor — não tenho como rodar o
Hytale aqui). Então "funciona corretamente" abaixo quer dizer "a lógica está bem encadeada e eu
não achei nada que quebre o fluxo", não "eu vi rodar". Onde há algo que só um teste em jogo
resolve, eu marco explicitamente.

---

## 1. Pesca (`FishingPostRegistry` + `NPCWorkHelper`)

Confirmado: o bloco é o `Tool_Fishing_Trap`, reconhecido por `FishingPostRegistry.isFishingPostId`,
e ele **precisa estar perto de água** — `findNearestWater` procura água (`fluid_water*`) num raio
de 8 blocos na horizontal e 3 na vertical **no momento em que o bloco é colocado**. Se não achar
água nesse raio, o posto não é registrado como utilizável (fica sem água associada) — então colocar
o bloco longe de qualquer rio de fato não funciona, como você descreveu.

A lógica de trabalho (`NPCWorkHelper`) segue exatamente o mesmo desenho do fazendeiro: reivindicar o
posto (um NPC por posto, via `claimNearest`/`CLAIMED_BY`) → andar até ele → ficar parado 40 ticks
tocando a animação de pesca (`playFish`, que na verdade reaproveita a animação "Fish"/olhar ao redor)
→ adicionar o peixe sorteado ao inventário → satisfação de trabalho → liberar o posto. Não achei
nenhuma diferença estrutural em relação à fazenda que já está validada. **Não achei bug.**

Um detalhe que vale confirmar em jogo, não no código: a busca de água roda só no `registerAt`
(quando o bloco é colocado). Se você colocar o bloco longe da água e *depois* cavar um canal até ele,
ele não vai perceber sozinho — só voltaria a funcionar se quebrar e recolocar o bloco.

---

## 2. Fazenda e Lenhador

Fazenda: sem mudanças desde a última revisão, já validada antes.

Lenhador (`LumberPostRegistry` + `NPCWorkHelper`): o bloco é o `Bench_Lumbermill`, que precisa estar
perto de um tronco de árvore (`isTreeTrunk`, raio 10 horizontal / 6 vertical). Tem um cuidado a mais
que a pesca não precisa: `removeByTree(treeX, treeY, treeZ)` — quando a árvore associada a um posto é
derrubada, o posto correspondente é removido do registro (senão o NPC ficaria "trabalhando" numa
árvore que não existe mais). O item coletado é literalmente o id do bloco do tronco cortado, sem
tabela de conversão — mais simples que a fazenda (que precisa de `CROP_TO_FOOD`/`CROP_TO_SEED`).
**Não achei bug** aqui também; a lógica de posto único por árvore e a limpeza ao derrubar a árvore
estão cobertas.

---

## 3. Emoções e animações dos NPCs

Está tudo puxado por `SimTaleJuiceHelper` (empurrão, flerte aceito/recusado, saudação) e
`MoodAnimationSystem` (expressão facial ligada ao humor atual). Conferi os pontos de disparo, não só
o helper isolado:

- **Empurrão (`playShove`)**: dispara em dois lugares — (1) `NPCSocialHelper`, quando uma conversa
  espontânea entre dois NPCs "azeda" (`applyChatOutcome` retorna desfavorável); (2) `InteractionManager`,
  quando você usa a interação "Mean" ou um "Scold" que derruba a amizade contra o próprio NPC. Nos dois
  casos ele toca a animação de soco + rosto de raiva e aplica um empurrão físico de verdade
  (`KnockbackComponent` + `Velocity`), calculando a direção pela posição relativa das duas entidades.
- **Animações de trabalho**: cada profissão tem a sua em `NPCWorkHelper` — andar, "Smith" (usado como
  animação genérica de "parado trabalhando" por várias profissões, inclusive o Guarda em combate, ver
  seção 6), lavrar, cortar, e a de pesca. Todas conferidas como realmente chamadas nos estados
  correspondentes da máquina de estados.
- **Animações de interação**: saudação (`playGreeting`, aceno + sorriso), flerte aceito (rosto alegre +
  beijo + partículas de coração + mensagem ao jogador) e flerte recusado (bico/raiva). Também conferidas
  nos pontos de chamada certos dentro de `InteractionManager`.
- **Expressão facial contínua (`MoodAnimationSystem`)**: troca o rosto conforme o humor
  (feliz/triste/com raiva/com raiva forte/assustado/animado), e tem um cuidado que vale destacar: como
  as animações de rosto são "one-shot" (tocam uma vez e voltam ao neutro), o sistema **replica a
  mesma expressão a cada 8 segundos** enquanto o humor não muda — sem isso, um NPC feliz por 10 minutos
  ficaria de cara neutra depois do primeiro segundo.

**Não achei bug** em nenhum desses pontos — os disparos estão ligados aos eventos certos e a lógica de
repetição do rosto tem justamente o cuidado que evita o "ficar robótico" que você quer evitar.

---

## 4. Cadeiras — energia **e humor**, não só energia

Aqui o código **já faz mais do que você lembra**. `NPCSeatingHelper.handleSitting` recupera dois
valores por tick enquanto o NPC está sentado:

- Energia: `+0.08/tick` (cerca de 1.6/segundo)
- **Fun (diversão): `+0.04/tick`** — ou seja, sim, já recupera uma necessidade de humor, só que é a
  "Fun", não uma condição separada.

E tem mais: ao sentar, `npc.setEmotion(Mood.HAPPY, 0.6f, "resting", ...)` já ajusta o **humor
(`Mood`, o estado emocional exibido no rosto)** para feliz, com intensidade 0.6 — então o "humor" no
sentido de estado emocional também é tocado, não só a barra de necessidade.

Então, pelo que o código diz hoje: cadeira recupera energia, recupera Fun, e ainda dá uma injeção de
bom humor ao sentar. Se na sua última partida você viu só a energia subir, pode ser uma versão
anterior do código, ou os números pequenos (0.04/tick de Fun) passaram despercebidos — vale um teste
mais atento observando a barra de Fun especificamente, não só Energia.

Sobre o gatilho: qualquer NPC ocioso com energia abaixo de 70 (ou 15% de chance aleatória mesmo com
energia alta) procura uma cadeira livre num raio de 32 blocos; qualquer cadeira, banco, sofá ou banqueta
serve (excluindo bancadas de trabalho como workbench/alchemybench/cookingbench/o próprio posto de
lenhador, que também contêm "bench" no id). Levanta quando a energia passa de 85 ou após 30 segundos
sentado. **Não achei bug** na lógica.

---

## 5. Crianças dividindo cama com os pais

Confirmado, e a implementação é mais robusta do que "usar a mesma cama": quando uma criança entra em
`FINDING_BED` e ainda não tem cama própria, o código primeiro tenta `FamilyBonds.findParentBed`, que
procura a cama dos pais em **quatro fontes em cascata** (registro de crescimento ativo em memória,
registro persistido no banco, varrendo os NPCs ativos por quem tem essa criança na lista de filhos, e
por fim os dados de família já salvos na própria criança) — então funciona mesmo se um dos pais não
estiver carregado no momento. Se acha a cama dos pais, a criança nem entra no fluxo normal de "procurar
cama livre": ela grava a mesma posição direto como `bedLocation` e segue para `MOVING_TO_BED`.

Como reforço, `HouseManager.isBedTakenByAnotherNpc` (usado no caminho normal, caso esse atalho não se
aplique por algum motivo) tem uma exceção explícita: se quem já ocupa a cama é pai/mãe de quem está
tentando reivindicar, não conta como "ocupada" — ou seja, mesmo pelo caminho genérico, criança nunca é
barrada por causa da cama do pai/mãe. As duas pontas (o atalho direto e o fallback) resolvem o mesmo
problema, então mesmo que uma delas falhe a outra cobre. **Não achei bug.**

---

## 6. Sistema de presentes — como funciona hoje

Você pediu para eu explicar, então aqui vai o fluxo completo (`InteractionManager.handleGift`):

1. **Sem item na mão** → mensagem de erro, nada acontece.
2. **Item é "Baby"** → sempre rejeitado aqui de propósito: presentear um bebê tem um caminho
   totalmente separado (troca de custódia entre cônjuges), e se caísse no fluxo de presente normal o
   item seria consumido sem transferir a custódia — um bebê "sumiria".
3. **NPC é criança**: só aceita comida (acelera o crescimento em 1 dia de jogo — reduz o `birthTick`
   em 24000 ticks e força o recálculo do estágio de crescimento na hora); qualquer outra coisa é
   recusada com uma mensagem sugerindo comida.
4. **É uma aliança de casamento** (reconhece o item por qualquer variação de nome/prefixo) → vira
   **pedido de casamento**, não presente comum: só aceita se romance ≥ 80 e amizade ≥ 70, e o NPC não
   pode já ser casado.
5. **NPC está com fome** (abaixo do limiar de 70) **e o item é comida** → alimenta o NPC de verdade
   (mesma tabela de tier/comida favorita/odiada usada pelos baús), com cura de vida se aplicável, em
   vez de contar só como presente social.
6. **Caso geral** → passa por uma lista ordenada de regras (`GIFT_RULES`), na primeira que bater:
   - item é favorito da NPC → reação "ama" (maior ganho)
   - item é odiado → reação "odeia" (perda de amizade/confiança/afinidade)
   - item combina com o hobby dela (ex.: sementes para quem gosta de jardinagem) → reação boa,
     abaixo de favorito mas acima de genérico
   - item é "lixo" (categoria classificada em `GiftCategory`) → reação ruim
   - NPC tem traço Ganancioso → gosta mais de presentes em geral
   - NPC tem traço Paranoico → desconfia até de presentes
   - item é "básico" → reação neutra fraca, positiva
   - nada disso bateu → reação "normal" (a mesma coisa, só sem categoria especial)

   O ganho final ainda é multiplicado (×0.5 se a relação é de inimizade, ×1.5 se são casados) — dar um
   presente pra quem já é seu cônjuge rende mais do que pra um desconhecido.

Não achei bug nesse fluxo — a ordem das regras faz sentido (favorito/odiado pessoal vence hobby, que
vence lixo/básico) e o caso do bebê está isolado corretamente do caminho genérico.

---

## 7. Profissões — funciona, mas achei 3 falsos positivos reais

O fluxo em si (`InteractionManager.handleProfession`) está correto de ponta a ponta: pega o item na
mão do jogador, resolve a profissão pelo id do item (`Profession.fromItemId`), aplica as regras de
recusa (relação ruim, traço Preguiçoso + trabalho pesado, traço Agressivo + trabalho pacífico, humor
irritado, profissão que a NPC não gosta), e se passar, atribui — dar uma espada realmente vira Guarda,
como você descreveu.

O problema está em **como** `fromItemId` identifica o item: ele testa se o id do item (em minúsculo)
**contém** a palavra-chave da profissão em qualquer posição — não é "é uma espada", é "tem a
sequência de letras 's-w-o-r-d' em algum lugar do nome". Cruzando isso com a lista real de itens do
jogo (`item_ids.txt`), achei três casos onde isso já dá falso positivo **hoje, com itens do próprio
jogo, sem precisar de mod nenhum**:

| Presenteie a NPC com... | Vira... | Por quê |
|---|---|---|
| Qualquer item de bordo (`Wood_Maple_*`, `Plant_Sapling_Maple`, `Plant_Seeds_Maple`, `Plant_Leaves_Maple`) | **Explorador** | "**map**le" contém "map" |
| Truta Arco-Íris (`Fish_Trout_Rainbow_Item`) | **Caçador** | "rain**bow**" contém "bow" |
| Tubarão-martelo (`Fish_Shark_Hammerhead_Item`) | **Construtor** | "**hammer**head" contém "hammer" |

Ou seja, dar um peixe de pesca pra uma NPC pode virar ela Caçadora sem querer. Isso é uma consequência
direta do "contains" e é fácil de corrigir sem reescrever o sistema todo — bastaria comparar por
palavra inteira (ex.: separar o id por `_` e comparar token a token) em vez de substring solta. Não
cheguei a aplicar a correção porque ela se encaixa melhor junto da proposta da próxima seção — dá pra
resolver os dois problemas (falso positivo e integração com mods) na mesma mudança, ao invés de corrigir
isso agora e mexer de novo depois.

---

## 8. Proposta: categorias de arma para o Guarda (e integração com mods)

Fui ver como o próprio Hytale resolve isso nativamente antes de inventar algo do zero, abrindo o jar
do servidor (`libs/HytaleServer.jar`). Resumo do que achei:

- **`ItemWeapon`** (config nativa de item-arma) guarda dano e modificadores de status, mas **não tem
  um campo de categoria melee/ranged** — não dá pra perguntar a ele "essa arma é de longo alcance?"
- **`ItemCategory`** parecia promissor pelo nome, mas na prática é só o agrupamento visual do
  inventário (nome, ícone, filhos, subcategorias) — serve pra organizar a UI, não pra dizer como o
  item deve ser usado em combate.
- O que realmente existe nativamente é um **sistema de tags em interações de combate**:
  `CombatInteractionValidator` e `CombatSupport` reconhecem as tags `Attack`, `Attack=Melee`,
  `Attack=Ranged` e `Attack=Block` em cima da cadeia de interação de um item/entidade, e há todo um
  subsistema nativo de projétil (`LaunchProjectileInteraction`, `ProjectileConfig` etc.) que é como o
  próprio jogo identifica "isso atira uma flecha/bala". Isso é genuinely mod-agnostic — qualquer mod
  que adicione uma arma de fogo ou um arco novo, se quiser que ela realmente atire, **precisa** usar
  esse mesmo mecanismo de projétil do motor, então ele é um sinal confiável.

O porém: esse sistema de tags/interação parece pensado para o papel de combate das criaturas nativas
do jogo (mobs hostis), não para NPCs de mod como os do SimTale, e ler a cadeia de interação de um item
arbitrário para descobrir se ele carrega um `LaunchProjectileInteraction` é uma integração bem mais
profunda com o motor do que o SimTale faz hoje em qualquer outro lugar — funcionaria, mas é caro de
construir e mais frágil a mudanças de versão do jogo (é uma API interna, não uma API pensada para
mods lerem).

**Proposta pragmática** (o que eu sugeriria implementar, em fases):

1. Criar um `WeaponCategory` (enum: `MELEE`, `RANGED`, e possivelmente `UNARMED` para "sem arma
   reconhecida") no lugar de decidir tudo dentro de `Profession`.
2. Um resolvedor `WeaponCategory.of(String itemId)` com duas camadas:
   - **Lista curada pequena** de palavras-chave por categoria (a mesma ideia de hoje, só que corrigida
     para bater por palavra inteira, não substring — resolve os 3 falsos positivos da seção 7 de graça)
     cobrindo os itens vanilla (espada/machado curto → melee; arco/besta → ranged).
   - **Arquivo de configuração externo** (um `.json` simples, do tipo `weapon_categories.json`, do
     jeito que outros mods já customizam coisas no Hytale) onde qualquer mod — ou você mesmo — pode
     registrar `"meu_mod:pistola_de_fogo": "RANGED"` sem precisar mexer no código do SimTale. Essa é a
     parte que resolve de verdade "qualquer mod pode adicionar arma nova sem o SimTale ter que
     conhecer o item": em vez de o SimTale ter que reconhecer a arma sozinho (impossível de manter
     conforme mods aparecem), o mod novo (ou você, na configuração) é quem declara a categoria.
3. `NPCGuardHelper` passa a se comportar diferente por categoria: hoje ele sempre anda até
   `MELEE_RANGE_SQ` (2.5 blocos) e "ataca" parado com a animação genérica "Smith". Com categoria,
   um Guarda com arma `RANGED` deveria **parar a uma distância maior** (um raio configurável, tipo 6-8
   blocos) e tocar uma animação de "mirar/atirar" em vez de fechar distância — sem isso, dar um arco
   pra um Guarda não muda o comportamento dele, só o item na mão dele, o que não cumpriria o objetivo
   que você descreveu.
4. Deixar a integração profunda com o sistema nativo de tags/projétil (usar de verdade o
   `LaunchProjectileInteraction` pra fazer o Guarda atirar um projétil físico, em vez de só "deletar o
   hostil depois de X segundos" como acontece hoje mesmo no corpo a corpo) como uma fase 2 — é uma
   melhoria de fundo separada do problema de categorização em si (o combate do Guarda hoje é
   inteiramente decorativo, nem a espada é usada de verdade; ver observação abaixo).

**Observação importante que vale seu conhecimento antes de decidir prioridade**: hoje, `NPCGuardHelper`
não usa a arma do Guarda em NENHUM sentido — corpo a corpo ou à distância. O "combate" atual é: andar
até 2.5 blocos do hostil, tocar a animação "Smith" (a mesma reaproveitada por outras profissões pra
"parado trabalhando", já que não existe uma animação própria de golpe de espada disponível), esperar 3
segundos, e remover a entidade hostil do mundo. Isso significa que a categorização de arma, por si só,
não muda o resultado do combate — ela muda a *distância* e a *animação*, mas o "dano" continua sendo
um temporizador fixo de 3 segundos que sempre vence. Se quiser, posso implementar já a fase 1 completa
(categoria + config externa + correção dos 3 falsos positivos + Guarda mudando distância/animação por
categoria) sem mexer no motor de dano, e deixar a fase 2 (dano de verdade ligado ao poder da arma, uso
do projétil nativo) para depois — ou fazer as duas juntas, se preferir. Me avisa qual escopo você quer
antes de eu escrever o código, porque a fase 2 é bem mais invasiva e some no meio da lista de
prioridades que você tinha pedido pra verificar hoje.

---

## Resumo do que precisa de ação sua

- 🟢 Pesca, fazenda, lenhador, emoções/animações, cadeiras, criança dividindo cama, presentes: **nada
  para corrigir**, só testar em jogo quando puder (principalmente as cadeiras, pra confirmar que você
  vê a barra de Fun subindo, já que a energia deve estar mascarando ela visualmente).
- 🟡 Profissões: os 3 falsos positivos (bordo→Explorador, truta arco-íris→Caçador,
  tubarão-martelo→Construtor) são reais e valem correção — mas eu recomendo resolver junto da
  categorização de armas, não isoladamente, pra não mexer duas vezes no mesmo trecho.
- 🔵 Categorização de armas: escopo em aberto — preciso que você escolha entre só a fase 1 (categoria +
  config + Guarda respeitando distância/animação) ou fase 1 + fase 2 (combate de verdade usando a arma)
  antes de eu implementar.
