---
sidebar_position: 7
title: Experiments
---

# Experimentos

Esta página é diferente de [Status de implementação](status): aquela página acompanha
funcionalidades que já fazem parte do design de verdade do mod. Esta acompanha **protótipos** —
coisas feitas rápido, geralmente atrás de um comando de debug, pra responder uma pergunta do tipo
"isso é sequer possível?" antes de comprometer com um design de verdade. Nada aqui deve ser
considerado estável, e nada aqui necessariamente já foi visto rodando numa partida real. Quando um
protótipo vira uma funcionalidade de verdade, com seu próprio ajuste fino e UI, ele sai desta página
e vai para [Status de implementação](status).

## Fantasias sazonais de NPC

**Pergunta:** dá pra trocar a aparência de uma NPC por uma roupa de evento (Natal, Halloween,
aniversários, ...) sem redesenhar todo o sistema de modelo/cosmético?

**Resposta: sim.** Os próprios assets do Hytale já fazem exatamente isso para outras criaturas —
veja `Server/Models/Christmas/Trork_Christmas.json` e as variantes de Natal do Kweebec Sapling no
jogo base. Um `ModelAsset` pode declarar um `Parent` (herdando a lista completa de attachments
daquele asset) e seus próprios `DefaultAttachments`, que **somam** à lista do pai em vez de
substituí-la. Isso já é suficiente pra colar um chapéu num modelo de NPC existente sem mudar nada no
engine e sem nenhuma API nova de attachment — só um arquivo JSON novo por variante de fantasia.

O SimTale já tinha a peça de runtime necessária pra usar isso: `SimNPCFactory.applyModel(store,
ref, modelAssetId, scale, attachments)`, o mesmo método já usado pra transformar uma NPC morrendo no
modelo `Necromancer_Void` do Grim Reaper. Trocar pra uma fantasia é só chamar de novo com outro id de
asset, e desfazer é chamar uma terceira vez com o id original.

### O que foi construído

- Seis arquivos `ModelAsset` novos em `src/main/resources/Server/Models/Events/`, um por combinação
  base/evento: `SimTale_Human_Male_Christmas.json`, `SimTale_Human_Female_Christmas.json`,
  `SimTale_Human_Child_Christmas.json`, e os mesmos três para `_Halloween`. Cada um tem `Parent`
  apontando pra base correspondente (`SimTale_Human_Male`/`Female`/`Child`) e um único attachment
  extra: um gorro de Papai Noel (`Colored_Cotton` / `Red`, a combinação exata copiada de
  `Trork_Christmas.json`) pro Natal, e uma textura de chapéu de bruxa/palha pro Halloween.
- Um comando de debug, `/simtale costume <christmas|halloween|off>`, adicionado ao
  `SimTaleCommand.java`. Ele acha a NPC mais próxima do jogador, guarda o id do modelo atual dela num
  mapa em memória (`COSTUME_BACKUP_MODEL`), e chama `SimNPCFactory.applyModel` com o id do asset de
  fantasia (escolhendo a base macho/fêmea/criança automaticamente via
  `InteractionManager.isNpcAChild` e o `Gender` da NPC). `off` busca o backup e restaura.

Isso foi construído rápido, sob pressão de tempo, pra ter *algo* rodável antes da sessão acabar,
então se apoia inteiramente em valores já comprovados pelos próprios assets do jogo base em vez de
qualquer coisa inventada do zero — o objetivo era um esboço funcional, não uma funcionalidade
terminada.

### O que está confirmado vs. o que ainda precisa de uma sessão de jogo

| | |
|---|---|
| ✅ | Confirmado funcionando numa partida real |
| 🔧 | Implementado, considerado correto, **ainda não confirmado em jogo** |
| 🐛 | Problema/limitação conhecida, ainda em aberto |
| ⬜ | Não implementado |

| Item | Status | Notas |
|---|---|---|
| Viabilidade (dá pra adicionar uma fantasia sem mudar o engine) | ✅ | Confirmado lendo os próprios assets do jogo base — não é um chute. |
| Comando `/simtale costume` registra e roda sem crashar | 🔧 | Nunca rodado em jogo — construído na mesma sessão em que está sendo documentado. |
| Id do asset resolve corretamente a partir da subpasta `Events/` | 🔧 | Todo asset de referência (`Trork_Christmas.json` etc.) fica direto em `Server/Models/<Categoria>/`; se `ModelAsset.getAssetMap()` indexa só pelo nome do arquivo ou se importa com o caminho da subpasta não foi conferido contra o código do loader. **Esse é o maior risco de todo o protótipo** — se falhar, o conserto provavelmente é só renomear/mover os arquivos, não uma mudança de lógica. |
| Gorro de Papai Noel renderiza numa posição sensata numa NPC humana | 🔧 | Só foi confirmado nos modelos de Trork e Kweebec Sapling do jogo base. O rig humano do SimTale pode anexar diferente. |
| Chapéu de bruxa renderiza corretamente (sem `GradientSet`/`GradientId`) | 🔧 | Um pouco menos verificado que o gorro de Natal — uma busca recursiva por um chapéu de bruxa confirmado compatível com humanos deu timeout, então isso usa um attachment só com textura, na suposição (vista em outros JSONs base do próprio SimTale) de que gradiente não é obrigatório. |
| Ida e volta `christmas` → `off` restaura a aparência original exatamente | 🔧 | A lógica parece certa (`putIfAbsent` + `remove` no mapa de backup) mas nunca foi rodada. |
| Funciona corretamente em NPCs crianças | 🔧 | As variantes `SimTale_Human_Child_*` existem e passam por `InteractionManager.isNpcAChild`, mas crianças usam uma única base não-generizada — não foi conferido contra toda variante de modelo infantil existente. |
| Backup da fantasia sobrevive a um restart do servidor | 🐛 | Limitação conhecida, mais uma lacuna de design do que um bug pra "corrigir": `COSTUME_BACKUP_MODEL` é um `Map` em memória, não é persistido. Uma NPC fantasiada que ficasse assim durante um restart não teria backup pra restaurar se `off` fosse usado depois. |
| Gatilho sazonal automático (baseado em calendário, não comando manual) | ⬜ | Não iniciado — este protótipo é manual de propósito, pra testar o mecanismo primeiro. |
| Cobertura de NPCs Slothian / Trork | ⬜ | Só as três bases humanas (macho/fêmea/criança) têm variantes de fantasia até agora. |

### Próximos passos, se isso virar uma funcionalidade de verdade

1. Confirmar os itens de risco acima numa partida real (resolução do asset primeiro — é o que
   trava tudo o resto).
2. Persistir `COSTUME_BACKUP_MODEL` (ou evitar precisar dele, por exemplo derivando o id do asset
   de "off" a partir dos dados de gênero/criança já existentes da NPC em vez de cachear).
3. Trocar o comando de debug por um gatilho de calendário/data.
4. Estender a cobertura pra NPCs Slothian e Trork.
5. Depois de confirmado, mover esta seção pra [Status de implementação](status) e apagar daqui.

---

*Fonte: esta página acompanha protótipos rápidos e deliberadamente crus. Diferente do
[Status de implementação](status), um item aqui sem ✅ é o padrão esperado, não um sinal de alerta —
é isso que faz dele um experimento.*
