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
| NPC mantém a própria cara (cabelo/rosto/etc.) enquanto fantasiada | 🔧 | Corrigido gerando um asset de fantasia por NPC em vez de usar a base genérica — ver "Investigado: a limitação de 'trocar o modelo inteiro'" abaixo. Ainda não confirmado numa partida real. |
| Backup da fantasia sobrevive a um restart do servidor | 🐛 | Limitação conhecida (não bug): `COSTUME_BACKUP_MODEL` é um `Map` em memória, não persistido. Uma NPC fantasiada durante um restart não teria backup para restaurar se `off` fosse usado depois. |
| Gatilho sazonal automático (baseado em calendário) | 🔧 | Construído (13/09): `SeasonalCostumeHelper.tick()`, ligado no `SimTaleTickSystem`, confere `WorldTimeResource.getGameDateTime()` no máximo 1x a cada 1.200 ticks (~1 min real) e reconcilia a fantasia de toda NPC ativa contra a data atual. Ainda não confirmado rodando numa partida real. |
| Chapéu de fantasia de criança encaixa direito na cabeça (sem faces transparentes/expostas) | 🔧 | **Era um bug de verdade, reportado com print (13/09) e corrigido no mesmo dia** — ver "Corrigido: chapéu de fantasia malencaixado em NPC criança" abaixo. Ainda não reconfirmado numa partida real. |
| Cobertura de NPCs Slothian / Trork | ⬜ | Só as três bases humanas (macho/fêmea/criança) têm variantes até agora. |

### Investigado: a limitação de "trocar o modelo inteiro" (13/09)

Esse é o custo real da abordagem atual, e é pior do que parecia à primeira vista. Os assets de
fantasia construídos acima (`SimTale_Human_Male_Christmas.json` etc.) têm `"Parent":
"SimTale_Human_Male"` — a base **genérica**, não a aparência específica daquela NPC. Ler o asset
base confirma que ele carrega um visual próprio fixo (cabelo `"Morning"`, `"BrownDark"`, etc.), e
ler um dos 804 arquivos `Generated/*.json` (por exemplo `SimTale_Human_Male_95.json`) confirma que é
*esse* arquivo que carrega os 9 attachments específicos daquela NPC (cabelo, rosto, olhos, calça,
camisa, sapato, boca, orelhas, sobrancelhas) — herdando da base genérica. Ou seja, hoje toda NPC
fantasiada do mesmo gênero/idade fica com a mesma cara: o visual genérico da base mais o chapéu, e
não o visual *dela* mais o chapéu. Isso é o problema "trocar o modelo inteiro é osso" em termos
concretos — confirmado lendo os arquivos de asset de verdade, não um chute.

**Uma gambiarra em runtime foi investigada e descartada.** Os campos de `ModelAsset` são
`protected`, não `private` — o próprio engine constrói uma instância assim
(`ModelAsset.DEBUG = new ModelAsset() {{ id = "Debug"; model = ...; }}`), e
`Model.createScaledModel(asset, scale, attachments)` recebe um objeto `ModelAsset` diretamente, não
só um id — então construir um `ModelAsset` sintético em memória, copiando os attachments reais de
uma NPC e adicionando um chapéu, é tecnicamente possível pra fins de *renderização*. Mas o
`PersistentModel` só salva um id em string (`ModelReference.toReference()` → `{Id, Scale,
RandomAttachments, Static}`), e `ModelReference.toModel()` resolve esse id chamando
`ModelAsset.getAssetMap().getAsset(id)` a cada reload — **caindo para `ModelAsset.DEBUG` (uma caixa
de placeholder visível) se o id não for encontrado**. Os únicos métodos de escrita do
`DefaultAssetMap` (`putAll`, `remove`) são `protected`, sem nenhuma API pública pra um mod registrar
um asset novo em runtime. Então um asset sintético não registrado renderizaria certo até o próximo
reload de chunk ou restart do servidor, e aí quebraria visivelmente. Não vale a pena construir em
cima disso.

**O conserto que realmente funciona, confirmado contra os arquivos reais:** apontar o `Parent` de
cada asset de fantasia pro id específico da NPC em `Generated/*.json`, em vez da base genérica.
Existem exatamente 804 hoje (202 homens + 202 mulheres adultos, 200 meninos + 200 meninas, contados
direto em `Generated/`), e cada um já herda tudo mais que precisa da base genérica sozinho — um
arquivo de fantasia só precisa acrescentar `Parent` + o attachment do chapéu, exatamente como os 6
arquivos atuais já são construídos. Isso significa:

*   Um script de geração único que lista todo id em `Generated/`, e para cada um escreve
    `<id>_Christmas.json` / `<id>_Halloween.json` do lado dos assets de fantasia existentes, cada um
    só `{"Parent": "<esse id>", "DefaultAttachments": [<o mesmo chapéu de hoje>]}`. ~804 × 2 ≈ 1.608
    arquivinhos (~150 bytes cada, ~240 KB no total) — mecânico, sem mudança no engine, sem código
    novo de carregamento de asset.
*   A lógica de id de fantasia no comando fica *mais simples*, não mais complexa: `costumeId =
    currentId + "_" + evento` direto, sem mais precisar decidir a base por gênero/criança —
    `currentId` já vem de `pm.getModelReference().getModelAssetId()` no código existente.
*   Precisa ser rodado de novo se o conjunto de variantes em `Generated/*.json` crescer no futuro.

**Atualizacao (13/09, mesma sessao): construido.** `scripts/generate_costume_assets.py` foi
escrito (seguindo a convencao ja existente `scripts/generate_*.py` do projeto) e rodado uma vez.
Ele encontrou **820** ids em `Generated/` (os 804 esperados de variantes humanas macho/femea/crianca,
mais 10 variantes legadas de crianca sem genero `SimTale_Human_Child_N` e 10 modelos de boneca
chibi `Doll_N` que entraram de brinde — inofensivo, ja que o comando de fantasia so busca ids que
vem do `PersistentModel` de uma NPC viva de verdade) e escreveu **1.640** arquivos (2 eventos × 820
ids) em `Server/Models/Events/Generated/`, cada um so `{"Parent": "<id da npc>", "DefaultAttachments":
[<chapeu>]}`. O script e idempotente — seguro de rodar de novo depois que novas variantes forem
adicionadas em `Generated/`, ele so preenche o que falta. O `CostumeSubCommand` em
`SimTaleCommand.java` foi atualizado pra combinar: agora calcula `costumeId = currentId + "_" +
sufixo` direto, e a antiga logica de gênero/criança (junto com os imports `InteractionManager`/
`Gender` que ela precisava) foi apagada por nao ser mais usada em nenhum outro lugar do arquivo.
Ainda nao confirmado rodando numa partida real — ver o checklist acima.

### Corrigido: chapéu de fantasia malencaixado em NPC criança (13/09)

Reportado com print: a cabeça de uma NPC criança fantasiada aparecia errada — a lateral da cabeça
parecia transparente, e a parte de trás parecia "malencaixada" (nas palavras do usuário: "provavelmente
foi a UV" — e o chute acertou em cheio).

**Causa raiz, confirmada lendo os arquivos de verdade:** todo cosmético de cabeça deste projeto
(cortes de cabelo etc.) que é feito pensando num esqueleto adulto precisa passar por uma
transformação de escala antes de poder ser usado num esqueleto de criança — ver
`docs/assets/cosmeticos-node-scales.md` e a tabela `NODE_SCALES` de
`scripts/generate_child_variants.py`. Resumindo: qualquer node com o nome exatamente `"Head"` (um
rótulo que significa "encaixa na cabeça", não literalmente um osso do esqueleto) recebe uma escala
uniforme de 1.2×, e tudo que está aninhado dentro dele herda essa mesma escala (confirmado
empiricamente comparando um par real de corte de cabelo adulto/criança, `CutePart.blockymodel` vs
`CutePart_Child.blockymodel`). Os modelos de chapéu de Natal/Halloween adicionados mais cedo nesta
sessão (`Cosmetics/Head/SantaHat.blockymodel`, `StrawHat.blockymodel`) foram a **única exceção** —
o script que gera os assets de fantasia (`scripts/generate_costume_assets.py`) usava o mesmo chapéu
sem escala, em proporção adulta, tanto pras variantes de adulto quanto pras de criança.

Ler o `SantaHat.blockymodel` por completo confirma por que isso aparece como um bug visível e não
só "um pouco fora de tamanho": várias das caixas mais externas aninhadas não têm entradas de
`textureLayout` pra faces que normalmente ficam sempre escondidas encaixadas dentro da próxima
caixa (ex: `bottom`, e um `back`) — algo totalmente razoável de pular quando o chapéu adulto está
encaixado direitinho. Assim que esse mesmo chapéu sem escala é forçado numa cabeça de criança
menor, o encaixe deixa de bater, essas faces que nunca tiveram textura ficam visíveis, e renderizam
exatamente como a geometria transparente/malposicionada do relato.

**Correção:** `scripts/generate_child_event_hats.py` (arquivo novo) reaproveita exatamente a mesma
lógica de `NODE_SCALES`/`FACE_ATTACHMENT_NAMES`/escala de `generate_child_variants.py`, aplicada
em `SantaHat.blockymodel` e `StrawHat.blockymodel`, gerando
`NPC/Player_Child/Cosmetics/Head/SantaHat_Child.blockymodel` e `StrawHat_Child.blockymodel`.
Verificado depois de rodar: os fatores de escala em cada node batem com o mesmo padrão de
composição de 1.2× visto na comparação dos cortes de cabelo. Os dois assets genéricos de fantasia
de criança (`SimTale_Human_Child_Christmas.json` / `_Halloween.json`) e todos os 820 arquivos de
fantasia gerados individualmente pra criança em `Events/Generated/` foram repatchados pra apontar
pro modelo `_Child` em vez do adulto (só o `Model` muda — `Texture`/`GradientSet`/`GradientId`
continuam os mesmos, exatamente como todo outro cosmético de criança do projeto já funciona: o
`textureLayout` do `.blockymodel` escalado continua mapeando pros mesmos pixels da textura).
O próprio `generate_costume_assets.py` agora tem um `EVENTS_CHILD` separado, escolhido sempre que
o `npc_id` começa com `"SimTale_Human_Child"`, então rodar de novo no futuro não vai reintroduzir
o bug.

**Ainda não confirmado visualmente em jogo** — a correção foi verificada lendo/comparando JSON e
geometria (os fatores de escala batem com o padrão esperado), mas ninguém viu uma NPC criança
fantasiada numa sessão de jogo de verdade depois da correção ainda. É a próxima coisa a conferir.

### Pesquisa: gatilho automático por calendário (13/09, verificado no código-fonte)

O passo 3 dos próximos passos abaixo ("trocar o comando de debug por um gatilho de
calendário/data") já tem tudo que precisa no próprio engine — anotado aqui para não precisar
redescobrir do zero depois. **Verificado lendo `WorldTimeResource.java` direto no código-fonte do
engine** (`hytale-shared-source`), não só confiando numa busca na web.

O engine expõe `com.hypixel.hytale.server.core.modules.time.WorldTimeResource`, e por baixo dos
panos é um calendário gregoriano de verdade: `getGameDateTime()` retorna um `java.time.LocalDateTime`
comum, avançando a partir de `ZERO_YEAR` (`0001-01-01T00:00:00Z`) usando a matemática de calendário
da própria JVM (ano bissexto, meses de tamanho real, tudo — não é um calendário de fantasia
inventado). `DAYS_PER_YEAR` é um `365` fixo (`ChronoUnit.YEARS.getDuration().toDays()`, não
configurável por mundo). Também disponível: `getGameTime()` (`Instant`), `getCurrentHour()`,
`getDayProgress()` (0.0–1.0) e `getMoonPhase()`.

**O SimTale já lê esse recurso** — não precisa inventar um jeito novo de acessar. Ver
`NPCSleepHelper.java` e `InteractionManager.java`:

```java
WorldTimeResource time = world.getEntityStore().getStore()
        .getResource(WorldTimeResource.getResourceType());
LocalDateTime date = time.getGameDateTime();
```

A partir daí, um gatilho de fantasia é só:

```java
if (date.getMonthValue() == 12 && date.getDayOfMonth() == 25) {
    // aplica a fantasia de Natal
}
```

Duas pegadinhas encontradas ao verificar, importantes de saber antes de construir em cima disso:

*   **`getMoonPhase()` não é uma fase nomeada.** Retorna um `int` simples, de `0` até
    `getTotalMoonPhases() - 1` (esse total é configurável por mundo). Não existe enum tipo
    "crescente"/"cheia" em lugar nenhum dessa classe — mapear o índice para um nome legível é por
    nossa conta.
*   **`isYearWithinRange(min, max)` parece exatamente o helper que a gente ia querer para "hoje
    está dentro desse intervalo de datas", mas o corpo dele está comentado atrás de um
    `// TODO: Implement` e sempre retorna `false`.** Não chamar esperando que funcione — comparar
    `getDayOfYear()`/`getMonthValue()`/`getDayOfMonth()` manualmente, como no trecho acima.

Isso cobre só a parte de *ler* a data para decidir quando disparar; não resolve sozinho o "não
reaplicar toda hora" (precisa de um cache pequeno tipo "último dia checado" por NPC ou por mundo)
nem a limitação de troca de modelo descrita acima.

### Próximos passos, se isso virar funcionalidade de verdade

1.  Confirmar os itens de risco acima numa partida real (resolução do asset primeiro, incluindo
    os ids de fantasia por-NPC recém-gerados).
2.  Persistir `COSTUME_BACKUP_MODEL` (ou evitar precisar dele, derivando o id do asset de "off" a
    partir dos dados de gênero/criança já existentes da NPC em vez de cachear).
3.  Ajustar as janelas de data em `SeasonalCostumeHelper.resolveEvent()` se dezembro inteiro /
    25-31 de outubro não forem a janela desejada — é um `if` de duas linhas, de propósito
    simples de editar.
4.  Estender a cobertura para NPCs Slothian e Trork.
5.  Depois de confirmado, promover este item para o [Checklist de Testes](../testing_checklist.md)
    e para o `status.md` da wiki, e apagar daqui.
