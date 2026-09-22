---
sidebar_position: 6
title: Implementation status
---

# Status de implementação

Um placar vivo do que existe, do que já foi confirmado de verdade numa partida real, e do que ainda
precisa de uma sessão de jogo antes de alguém confiar nisso. Condensado do log interno de testes do
projeto — o log em si é um diário de depuração linha a linha e não fica na wiki, mas todo status
abaixo remonta a ele ou a uma revisão do próprio código.

**Leia o status, não só o ícone.** ✅ quer dizer que alguém viu acontecer em jogo. 🔧 quer dizer que
o código é considerado correto (muitas vezes porque uma causa raiz específica foi achada e
corrigida), mas ninguém confirmou desde então. 🐛 marca um problema conhecido, ainda em aberto. ⬜
quer dizer que a funcionalidade não tem comportamento nenhum implementado ainda — o item/asset pode
existir, a lógica não.

| | |
|---|---|
| ✅ | Confirmado funcionando numa partida real |
| 🔧 | Implementado, considerado correto, **ainda não confirmado em jogo** |
| 🐛 | Problema conhecido, ainda em aberto |
| ⬜ | Não implementado |

---

## Fundação

| Área | Status | Notas |
|---|---|---|
| Deploy atômico do mod | ✅ | Resolveu o crash `ZipException: invalid LOC header` causado pelo jogo recarregando a quente um jar sendo escrito. |
| Persistência entre reinícios | ✅ | NPCs mantêm nome, necessidades, casa e relacionamentos. |
| Varredura de móveis ao entrar no mundo | ✅ | Camas e baús que já estavam na casa se registram sozinhos, sem precisar recolocar. |
| Crescimento populacional | ✅ | Sem spawn automático em segundo plano; crescer é uma ação deliberada do jogador via `ImmigrationContract`. |

## Casas, camas e portas

| Área | Status | Notas |
|---|---|---|
| Checagem de validade da casa, registro de cama/baú | ✅ | `/simtale housecheck`, `debugbeds`, `debugchests` — todos confirmados. |
| Atribuição de cama e cama compartilhada para uma segunda NPC | ✅ | Nenhuma NPC roubou cama de outra em jogo observado. |
| Portas: abre só quando realmente vai passar | ✅ | |
| Portas: um registro por porta, não por bloco | ✅ | |
| Portas: parede com várias portas não abre todas de uma vez | ✅ | |
| Portas: pathfinding realmente usa a porta | ✅ | |
| Portas: disputa de abrir/fechar quando duas NPCs usam a mesma porta | 🐛 | Bem melhor depois de trocar por uma checagem geométrica de "pra que lado a NPC está indo", mas o conflito de fechar-enquanto-abre ainda reproduz. |
| Casa identificada pela cama | 🐛 | Mover a cama pode criar um segundo registro de casa. Não é urgente, ainda não corrigido. |

## Sono

| Área | Status | Notas |
|---|---|---|
| Dorme por horário (não só por cansaço) | ✅ | Duas regressões reais achadas e corrigidas nesta rodada — ver o log de desenvolvimento para o detalhe. |
| Dorme a noite inteira | ✅ | |
| Guarda trabalha de noite, dorme de dia | ⬜ | Não implementado — o Guarda hoje segue o mesmo horário normal de todo mundo. |
| Comportamento base de sono (andar até a cama, animação, `Frozen`, acordar) | ✅ | |

## Fome e alimentação

| Área | Status | Notas |
|---|---|---|
| Baús do mundo/loot ignorados, só baú de casa é usado | ✅ | |
| Fazendeiro deposita no baú de casa pela mesma regra | ✅ | |
| Comida favorita/odiada afeta o humor | ✅ | |
| Receber comida na mão com fome (come na hora vs. guarda como presente) | ✅ | |
| Escolha por tier (comida preparada vence carne crua mesmo se a crua for favorita) | 🔧 | Implementado, nunca confirmado em jogo. |
| Desempate por gosto quando o tier é igual | 🔧 | Implementado, nunca confirmado. |
| Interrompe uma tarefa quando a fome cai criticamente | 🔧 | Implementado, nunca confirmado. |
| Desiste de um baú inalcançável em vez de ficar em loop | 🔧 | Implementado, nunca confirmado — essa exata categoria de bug já gerou milhares de buscas por segundo com uma cama inalcançável. |
| Comer cura vida por tier | 🔧 | Implementado, nunca confirmado. |

## Trabalhos

| Área | Status | Notas |
|---|---|---|
| Fazendeiro (plantar, colher, depositar, replantio automático, reposição de semente) | ✅ | Sessão longa de depuração, totalmente resolvida e confirmada em jogo. |
| Expedição de Caçador / Minerador (NPC some e volta com um recurso) | ✅ | Redesenhado para não perseguir um animal/minério de verdade, por segurança. |
| Pescador / Lenhador | ✅ | Revisado contra o desenho já validado do fazendeiro; nenhum bug estrutural encontrado. |
| Guarda: detecta hostis, patrulha o perímetro da vila quando ocioso | ✅ | |
| Guarda: categoria de arma (corpo a corpo vs. à distância) muda distância e animação de combate | 🔧 | Implementado nesta sessão, compila e builda limpo; ainda não confirmado em jogo. Ver [Combate do Guarda](#combate-do-guarda) abaixo. |
| Falsos positivos na atribuição de profissão (bordo → Explorador, truta arco-íris → Caçador, tubarão-martelo → Construtor) | ✅ | Corrigido usando palavras-chave mais específicas em vez de substring solta. Verificado cruzando com a lista real de itens do jogo. |

### Combate do Guarda

O "combate" do Guarda ainda é fundamentalmente decorativo: ele anda até o alcance, toca uma animação
de substituição, espera 3 segundos fixos, e remove o hostil — independente da arma que está
segurando. A categoria de arma (adicionada nesta sessão) muda *distância e animação* corretamente,
mas o resultado continua sendo o mesmo temporizador. Transformar isso em dano de verdade puxado da
arma e, para Guardas à distância, um projétil de verdade em vez de um temporizador, está registrado
como um trabalho futuro separado, ainda não iniciado.

## Socialização, perambulação e hobbies

| Área | Status | Notas |
|---|---|---|
| Conversa espontânea entre NPCs | 🐛 | Nunca observada em jogo. O gatilho (`social` baixo + proximidade) precisa ser investigado — pode simplesmente não estar disparando. |
| Amizade NPC↔NPC sobrevive a um restart | 🔧 | Implementado, nunca confirmado. |
| Contágio de humor entre NPCs conversando | 🔧 | Implementado, nunca confirmado. |
| Discussões (traço agressivo / inimigos) prejudicam humor e amizade | 🔧 | Implementado, nunca confirmado. |
| Sono tem prioridade sobre uma conversa, sem travar quem ficou pra trás | 🔧 | Implementado, nunca confirmado. |
| Perambulação ancorada (nunca deriva pra longe de casa indefinidamente) | ✅ | |
| Viagem de hobby (pesca/mineração/jardinagem/leitura) e o ganho de `fun` | 🔧 | Usuário descreve como "parece funcionar" — não confirmado com rigor suficiente pra fechar. |
| Fallback de hobby quando o cenário não existe por perto (ex.: hobby de pesca no deserto) | 🔧 | Implementado, nunca confirmado. |
| Hobby em comum aumenta o ganho de amizade na conversa | ✅ | Verificado no código (`NPCSocialHelper.bondNpcs`), ponto de chamada real confirmado. |
| Presente que combina com o hobby (ex.: vara de pesca → NPC que pesca) | ✅ | |

## Relacionamentos, casamento e família

| Área | Status | Notas |
|---|---|---|
| Painel de interação social, casamento, gravidez, parto | ✅ | O parto tinha um bug real (bebê persistido mas nunca sincronizado no inventário da mãe até um restart) — corrigido e confirmado. |
| Aliança de casamento como presente/pedido | ✅ | A identificação do id precisou ser normalizada (existiam quatro formas diferentes de capitalização/prefixo pro mesmo arquivo); corrigido. |
| Passar o bebê entre os pais | ✅ | |
| Caminho de gravidez de jogadora (`PlayerPregnancyTickSystem`) | ❓ | Caminho de código separado da gravidez de NPC, ainda não exercitado nenhuma vez. |

## Crescimento

| Área | Status | Notas |
|---|---|---|
| `/simtale setstage` (forçar um estágio de crescimento) | ✅ | |
| Pai/mãe e filho se reconhecem ao nascer (`FamilyBonds`) | ✅ | |
| Modelos cosméticos de criança | ✅ | 34 caminhos de modelo quebrados corrigidos (faltava um prefixo no caminho). |
| Colocar um bebê segurado no chão | ✅ | A causa raiz eram dois bugs empilhados: uma interação de item sobrescrevendo o clique, e um listener de evento registrado com o método errado (`.register` em vez de `.registerGlobal`) — o único no projeto usando a chamada errada. `/simtale forceplacebaby` existe como alternativa garantida de qualquer forma. |
| Transições de estágio automáticas ao longo do tempo (`GrowthTickSystem` realmente rodando) | ❓ | Nunca acompanhado por um ciclo completo. |
| Ciclo completo bebê → adulto observado uma vez, do início ao fim | ❓ | Ainda não feito. |

## Morte e o Ceifador

| Área | Status | Notas |
|---|---|---|
| Fome nunca mata (por design — envelhecimento/doença são as causas de morte pretendidas, ainda não implementadas) | ✅ | |
| Aviso de morte não repete pra sempre | ✅ | |
| Ceifador nasce sozinho perto de um corpo e completa o ritual | ✅ | Redesenhado nesta rodada: antes exigia um spawn manual de administrador e podia travar permanentemente se nenhum estivesse disponível no tick exato. |
| Implorar pela vida com o item certo cancela a coleta | 🔧 | Novo, nunca testado em jogo. |
| Registro de cemitério (NPC morta arquivada em vez de apagada) | 🔧 | O registro existe e é gravado; nada ainda lê ele de volta além do painel de ressurreição abaixo, que por si só não foi testado de ponta a ponta. |
| Casa/cama liberada após a morte | ❓ | Não testado. |
| O que acontece com cônjuge/filhos sobreviventes | ❓ | Provavelmente nada ainda — registrado como melhoria futura, não como bug. |

### Cemitério e ressurreição

Inteiramente novo nesta rodada e **ainda não exercitado nenhuma vez em jogo real** — cada item
abaixo está em aberto: listar o painel do cemitério, ressuscitar (incluindo o remapeamento de UUID
que isso exige, já que a engine não deixa uma entidade nova reusar um UUID antigo), casamento/
filhos/amizades sobrevivendo ao remapeamento, ressuscitar alguém cujo cônjuge está descarregado no
momento, recuperar a posse antiga de cama/casa/baú, e impedir ressuscitar o mesmo registro duas
vezes.

## Construção

| Área | Status | Notas |
|---|---|---|
| Colocar um item de planta (holograma de preview, girar, `/build start`/`clear`) | 🔧 | Redesenhado duas vezes durante a investigação (um bug de empacotamento de prefab, depois um bug de registro de evento idêntico ao do bebê). Precisa de um reteste completo. |
| Estrutura nasce virada para o lado certo | ✅ | |
| Holograma nativo da engine (em vez de blocos marcadores) | ❓ | Não confirmado. |
| NPC realmente andando até o canteiro e construindo | ❓ | Não confirmado. |
| Cancelar no meio não deixa nada órfão | ❓ | Não confirmado. |

## Humor, chat e aparência

| Área | Status | Notas |
|---|---|---|
| Plumbob reflete o humor, `isMiserable` | ✅ | |
| Expressão facial reemite periodicamente em vez de travar após uma mudança | 🔧 | Ficou quebrado por um tempo por causa de um ajuste de calibragem não relacionado; corrigido, precisa de uma nova rodada de confirmação. |
| Calibragem de decaimento/tédio do humor | ✅ | |
| Chat com IA (jogador ↔ NPC), ciente de personalidade/humor/profissão | ✅ | |
| Contexto faltando no chat (localização, vida, jogadores próximos, horário, relacionamentos) | ⬜ | Registrado como item de roadmap, não como bug. |
| Fallback gracioso quando o backend de IA está inacessível | ❓ | Não confirmado. |
| Visual do painel de interação (barras de necessidade, ícones de profissão/hobby, listas completas de gosto/desgosto) | ✅ | |
| Modelo de boca das NPCs femininas, variedade cosmética | ✅ | |

## Vilas

| Área | Status | Notas |
|---|---|---|
| Vila se forma por encadeamento de casas próximas | ✅ | |
| `/simtale village` bate com o que foi realmente construído | ✅ | |
| NPC sem casa ainda sai da vila para trabalhar/comer (coleira não é rígida demais) | ✅ | |
| Vila desaparece quando suas casas são destruídas | ❓ | Não confirmado — hoje nada apaga um registro de casa, exceto a varredura de duplicatas, que é uma lacuna separada. |
| Guarda patrulha o perímetro / fica parado sem vila | ✅ | Confirmado como parte do trabalho de Guarda desta sessão. |

## Itens, receitas e modelos

Vários itens craftáveis existem como objeto (modelo, textura, receita, nome traduzido) mas **sem
nenhum comportamento ligado ao uso**:

| Item | Status |
|---|---|
| Aliança de casamento, Bebê, Teste de gravidez, Contrato de Imigração | ✅ tem comportamento |
| Planta da Casa, Registro do Estalajadeiro, Diário do Inspetor | 🔧 implementado depois, precisa de reteste |
| Luneta do Almoxarife, Sino da Vila, Bolo de Aniversário | ⬜ ainda sem comportamento |

A aparência na mão dos itens mais novos (corrigida ao seguir a convenção de nome do nó raiz que o
próprio jogo usa) está ✅ confirmada visualmente; o comportamento por trás da maioria deles é
separado e está listado acima.

## Problemas conhecidos em aberto

- Brigar com o filho, colo de criança, restrição de trabalho infantil a profissões seguras, o tema
  visual de madeira da UI, e o registro de baús persistido (sobreviver a um restart mesmo para baús
  longe do spawn) estão todos **implementados mas sem nenhuma confirmação em jogo** — todo item de
  cada uma dessas áreas continua em aberto.
- Uma NPC sem água num raio de 15 blocos e com higiene caindo tem uma correção documentada de
  intervalo mínimo entre buscas que ainda não foi reconfirmada.
- Um relato único de "NPC travada na cama ao meio-dia, se recuperou sozinha depois de um tempo" é
  compatível com uma janela de sono agendada terminando normalmente, não um bug — sinalizado para
  quem ver de novo conferir o `/simtale npcstate` antes de assumir regressão.

---

*Fonte: esta página é uma versão condensada e externa do log de testes linha a linha do próprio
projeto. Quando o código e esta página discordarem, confie no código — e depois venha corrigir esta
página.*
