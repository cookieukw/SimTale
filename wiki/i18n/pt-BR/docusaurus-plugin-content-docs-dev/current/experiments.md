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
| NPC mantém a própria cara (cabelo/rosto/etc.) enquanto fantasiada | 🔧 | Corrigido gerando um asset de fantasia por NPC em vez de usar a base genérica — ver "Investigado: a limitação de 'trocar o modelo inteiro'" abaixo. Ainda não confirmado numa partida real. |
| Backup da fantasia sobrevive a um restart do servidor | 🐛 | Limitação conhecida, mais uma lacuna de design do que um bug pra "corrigir": `COSTUME_BACKUP_MODEL` é um `Map` em memória, não é persistido. Uma NPC fantasiada que ficasse assim durante um restart não teria backup pra restaurar se `off` fosse usado depois. |
| Gatilho sazonal automático (baseado em calendário, não comando manual) | ⬜ | Não iniciado — este protótipo é manual de propósito, pra testar o mecanismo primeiro. |
| Cobertura de NPCs Slothian / Trork | ⬜ | Só as três bases humanas (macho/fêmea/criança) têm variantes de fantasia até agora. |

### Pesquisa: gatilho automático por calendário (13/09, verificado no código-fonte)

O passo 3 dos próximos passos abaixo ("trocar o comando de debug por um gatilho de
calendário/data") já tem tudo que precisa no próprio engine — isso está anotado aqui pra ninguém
precisar redescobrir do zero depois. **Verificado lendo `WorldTimeResource.java` direto no
código-fonte do engine, não só confiando numa busca na web.**

O engine expõe `com.hypixel.hytale.server.core.modules.time.WorldTimeResource`, e por baixo dos
panos é um calendário gregoriano de verdade: `getGameDateTime()` retorna um `java.time.LocalDateTime`
comum, avançando a partir de `ZERO_YEAR` (`0001-01-01T00:00:00Z`) usando a matemática de calendário
da própria JVM (ano bissexto, meses de tamanho real, tudo — não é um calendário de fantasia
inventado). `DAYS_PER_YEAR` é um `365` fixo (`ChronoUnit.YEARS.getDuration().toDays()`, não é
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

- **`getMoonPhase()` não é uma fase nomeada.** Retorna um `int` simples, de `0` até
  `getTotalMoonPhases() - 1` (esse total é configurável por mundo). Não existe enum tipo
  "crescente"/"cheia" em lugar nenhum dessa classe — mapear o índice pra um nome legível é por
  nossa conta, se algum dia quisermos isso.
- **`isYearWithinRange(min, max)` parece exatamente o helper que a gente ia querer para "hoje está
  dentro desse intervalo de datas", mas o corpo dele está comentado atrás de um `// TODO: Implement`
  e sempre retorna `false`.** Não chamar esperando que funcione — comparar
  `getDayOfYear()`/`getMonthValue()`/`getDayOfMonth()` manualmente, como no trecho acima.

Isso cobre só a parte de *ler* a data para decidir quando disparar; não resolve sozinho o "não
reaplicar toda hora" (precisa de um cache pequeno tipo "último dia checado" por NPC ou por mundo)
nem a limitação de troca de modelo descrita abaixo.

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

### Próximos passos, se isso virar uma funcionalidade de verdade

1. Confirmar os itens de risco acima numa partida real (resolução do asset primeiro — é o que
   trava tudo o resto, incluindo os ids de fantasia por-NPC recém-gerados).
2. Persistir `COSTUME_BACKUP_MODEL` (ou evitar precisar dele, por exemplo derivando o id do asset
   de "off" a partir dos dados de gênero/criança já existentes da NPC em vez de cachear).
3. Trocar o comando de debug por um gatilho de calendário/data — a API e o padrão de acesso já
   estão confirmados, ver "Pesquisa: gatilho automático por calendário" acima.
4. Estender a cobertura pra NPCs Slothian e Trork.
5. Depois de confirmado, mover esta seção pra [Status de implementação](status) e apagar daqui.

---

*Fonte: esta página acompanha protótipos rápidos e deliberadamente crus. Diferente do
[Status de implementação](status), um item aqui sem ✅ é o padrão esperado, não um sinal de alerta —
é isso que faz dele um experimento.*
