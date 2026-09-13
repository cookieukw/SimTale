# Experimentos

> **TL;DR**: Registro de protótipos rápidos — construídos sob pressão de tempo, geralmente atrás de
> um comando de debug, para responder "isso é sequer possível?" antes de comprometer com um design
> definitivo. Diferente do [Checklist de Testes](../testing_checklist.md), que acompanha
> funcionalidades que já fazem parte do design real do mod, esta página acompanha coisas que ainda
> podem nem virar funcionalidade. Nada aqui deve ser considerado estável.

---

## 1. Fantasias sazonais de NPC

### Pergunta

Dá para trocar a aparência de uma NPC por uma roupa de evento (Natal, Halloween, aniversários...)
sem redesenhar todo o sistema de modelo/cosmético do mod?

### Resposta: sim

Os próprios assets do Hytale já fazem exatamente isso para outras criaturas — veja
`Server/Models/Christmas/Trork_Christmas.json` e as variantes de Natal do Kweebec Sapling no jogo
base. Um `ModelAsset` pode declarar um `Parent` (herdando a lista completa de attachments daquele
asset) e seus próprios `DefaultAttachments`, que **somam** à lista do pai em vez de substituí-la.
Isso já é suficiente para colar um chapéu num modelo de NPC existente sem mudar nada no engine e
sem nenhuma API nova de attachment — só um arquivo JSON novo por variante de fantasia.

O SimTale já tinha a peça de runtime necessária para usar isso: `SimNPCFactory.applyModel(store,
ref, modelAssetId, scale, attachments)`, o mesmo método já usado para transformar uma NPC morrendo
no modelo `Necromancer_Void` do Grim Reaper (ver [Ciclo de Vida e Crescimento](sistemas/lifecycle-crescimento.md)
e o [Checklist de Testes](../testing_checklist.md) para o caso do Reaper). Trocar para uma fantasia
é só chamar de novo com outro id de asset, e desfazer é chamar uma terceira vez com o id original.

### O que foi construído

*   Seis arquivos `ModelAsset` novos em `src/main/resources/Server/Models/Events/`, um por
    combinação base/evento: `SimTale_Human_Male_Christmas.json`, `SimTale_Human_Female_Christmas.json`,
    `SimTale_Human_Child_Christmas.json`, e os mesmos três para `_Halloween`. Cada um tem `Parent`
    apontando para a base correspondente (`SimTale_Human_Male`/`Female`/`Child`) e um único
    attachment extra: um gorro de Papai Noel (`Colored_Cotton` / `Red`, a combinação exata copiada
    de `Trork_Christmas.json`) para o Natal, e uma textura de chapéu de bruxa/palha para o Halloween.
*   Um comando de debug, `/simtale costume <christmas|halloween|off>`, adicionado ao
    `SimTaleCommand.java`. Ele acha a NPC mais próxima do jogador, guarda o id do modelo atual dela
    num mapa em memória (`COSTUME_BACKUP_MODEL`), e chama `SimNPCFactory.applyModel` com o id do
    asset de fantasia (escolhendo a base macho/fêmea/criança automaticamente via
    `InteractionManager.isNpcAChild` e o `Gender` da NPC). `off` busca o backup e restaura.

Isso foi construído rápido, sob pressão de tempo, para ter *algo* rodável antes da sessão acabar,
então se apoia inteiramente em valores já comprovados pelos próprios assets do jogo base em vez de
qualquer coisa inventada do zero — o objetivo era um esboço funcional, não uma funcionalidade
terminada.

### Checklist do protótipo

Mesma legenda usada no [Checklist de Testes](../testing_checklist.md) e no `status.md` da wiki:
✅ confirmado em jogo · 🔧 implementado, não confirmado · 🐛 problema/limitação conhecida · ⬜ não
implementado.

| Item | Status | Notas |
|---|---|---|
| Viabilidade (dá para adicionar uma fantasia sem mudar o engine) | ✅ | Confirmado lendo os próprios assets do jogo base — não é um chute. |
| Comando `/simtale costume` registra e roda sem crashar | 🔧 | Nunca rodado em jogo — construído na mesma sessão em que está sendo documentado. |
| Id do asset resolve corretamente a partir da subpasta `Events/` | 🔧 | **Maior risco do protótipo.** Todo asset de referência (`Trork_Christmas.json` etc.) fica direto em `Server/Models/<Categoria>/`; não foi conferido se `ModelAsset.getAssetMap()` indexa só pelo nome do arquivo ou se importa com o caminho da subpasta. Se falhar, o conserto provavelmente é só mover os arquivos, não uma mudança de lógica. |
| Gorro de Papai Noel renderiza numa posição sensata numa NPC humana | 🔧 | Só foi confirmado nos modelos de Trork e Kweebec Sapling do jogo base; o rig humano do SimTale pode anexar diferente. |
| Chapéu de bruxa renderiza corretamente (sem `GradientSet`/`GradientId`) | 🔧 | Um pouco menos verificado que o gorro de Natal — uma busca recursiva por um chapéu de bruxa confirmado compatível com humanos deu timeout; usa attachment só com textura, na suposição (vista em outros JSONs base do próprio SimTale) de que gradiente não é obrigatório. |
| Ida e volta `christmas` → `off` restaura a aparência original exatamente | 🔧 | Lógica parece certa (`putIfAbsent` + `remove` no mapa de backup) mas nunca foi rodada. |
| Funciona corretamente em NPCs crianças | 🔧 | `SimTale_Human_Child_*` existem e passam por `InteractionManager.isNpcAChild`, mas crianças usam uma única base não-generizada — não conferido contra toda variante de modelo infantil existente. |
| Backup da fantasia sobrevive a um restart do servidor | 🐛 | Limitação conhecida (não bug): `COSTUME_BACKUP_MODEL` é um `Map` em memória, não persistido. Uma NPC fantasiada durante um restart não teria backup para restaurar se `off` fosse usado depois. |
| Gatilho sazonal automático (baseado em calendário) | ⬜ | Não iniciado — protótipo é manual de propósito, para testar o mecanismo primeiro. |
| Cobertura de NPCs Slothian / Trork | ⬜ | Só as três bases humanas (macho/fêmea/criança) têm variantes até agora. |

### Próximos passos, se isso virar funcionalidade de verdade

1.  Confirmar os itens de risco acima numa partida real (resolução do asset primeiro).
2.  Persistir `COSTUME_BACKUP_MODEL` (ou evitar precisar dele, derivando o id do asset de "off" a
    partir dos dados de gênero/criança já existentes da NPC em vez de cachear).
3.  Trocar o comando de debug por um gatilho de calendário/data.
4.  Estender a cobertura para NPCs Slothian e Trork.
5.  Depois de confirmado, promover este item para o [Checklist de Testes](../testing_checklist.md)
    e para o `status.md` da wiki, e apagar daqui.
