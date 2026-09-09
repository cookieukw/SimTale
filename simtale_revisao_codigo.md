# SimTale — revisão de código (bugs, redundâncias e erros)

Escopo: os 150 arquivos `.java` (~27.500 linhas) em `src/main/java`, no estado atual do `git`
(HEAD `79c89d6`, 1090 commits, último em 07/09). Cruzei os achados com o `AUDITORIA.md` para não
repetir o que já foi corrigido — e vale dizer: a maior parte do que aquele documento levantou
(os `assert` como validação, os `System.out.println`, o `catch (Throwable ignored)` vazio, o
`romance > 20` que rebaixava `BEST_FRIEND`, a recompilação de regex por mensagem, o
`Thread.sleep` no chat) **está mesmo corrigida no código atual** — o projeto evoluiu bastante
desde aquela auditoria.

Não li as 27,5 mil linhas uma a uma: fiz varredura por padrão (grep) no projeto inteiro e leitura
completa dos arquivos mais centrais e mais mexidos recentemente (`SimTaleChatHandler`,
`RoutineAISystem`, `NPCSocialHelper`, `Relationship`, `SimTaleCommand`, os registries). Pode haver
bug de lógica fina em arquivo que não abri — o que segue é o que a varredura realmente encontrou
e eu confirmei lendo o código, não uma garantia de cobertura total.

---

## 🔴 Bugs confirmados

### 1. Mensagem de chat com pontuação no fim quebra a detecção de intenção

`SimTaleChatHandler.java` tem um `normalize()` (linha 57) que deixa a mensagem minúscula e tira
pontuação — mas ele só é usado para achar o NPC pelo nome (`findTargetNpc`). A mensagem que
realmente chega em `ChatIntent.detect()` (chamada na linha 204, com o texto passado cru desde a
linha 120) **nunca passa por ele**.

Isso importa porque metade dos detectores de intenção comparam palavra por palavra com igualdade
exata (`hasWord`, linha 656): `mWord.equals(target)`. Uma mensagem de uma palavra só com
pontuação no fim — `"Oi!"`, `"Valeu!"`, `"Tchau!"`, `"Chato!"` — vira o token `"oi!"`, que nunca é
igual a `"oi"`. Resultado: `GREETING`, `CANCEL`, `GRATITUDE` e parte de `COMPLIMENT`/`INSULT_CHAT`
falham exatamente nas formas mais naturais de digitar (com "!" no final), e a NPC responde
`SMALLTALK` genérico em vez da reação certa. `isCommand` (linha 610) já trata esse caso pros
comandos de trabalho (checa explicitamente `kw + "!"`/`kw + "?"`/`kw + ","`), então dá pra ver que
o problema é só nos outros verbos de match (`hasWord`, `hasStem`, `hasPhrase` de palavra única) não
terem recebido o mesmo tratamento.

**Correção sugerida**: aplicar `normalize(message)` (ou pelo menos remover pontuação final) antes
de `handleNpcCommand`/`ChatIntent.detect`, do mesmo jeito que já é feito para achar o NPC pelo
nome.

---

## 🟠 Redundância arquitetural — o mesmo bug de fundo copiado em ~20 arquivos

O `testing_checklist.md` do próprio projeto já registrava isso como pendência de baixa prioridade
só para `BedRegistry`/`ChestRegistry` ("continuam `static` e globais, sem escopo de mundo"). Rodei
uma varredura completa e o padrão se repete em praticamente todo registry do mod — não é uma
exceção, é a norma:

`BedRegistry`, `ChestRegistry`, `HouseManager`, `FarmlandRegistry`, `CropRegistry`,
`LeisureRegistry`, `FishingPostRegistry`, `LumberPostRegistry`, `FarmPostRegistry`,
`ChairRegistry`, `NPCWorkHelper`, `PlumbobSystem`, `SimTaleMarkerProvider`, `BabyCareManager`,
`LifecycleManager`, `ConstructionPreviewManager`, `ChildCarryHelper`, `NeedsHelper`,
`PrefabManager` — todos guardam o estado em `Map`/`List`/`Set` `static`, compartilhados por
**todos os mundos carregados no mesmo processo**, sem nenhuma chave de mundo.

Num servidor com um mundo só (o caso mais comum) isso nunca aparece. Mas é o mesmo defeito de
fundo que já causou o bug documentado no seu checklist (registro vazando de um mundo pro outro,
"até vazam de um mundo para outro" — palavras do próprio doc). Se algum dia o servidor rodar dois
mundos ao mesmo tempo (ou até só carregar/descarregar um mundo em sequência sem reiniciar o
processo), qualquer um desses ~19 registries pode misturar dados dos dois. Não é um bug isolado
pra corrigir aqui e ali — é uma decisão de arquitetura que falta (algo como um
`Map<WorldId, Registry>` em vez de `Registry` direto), e vale essa conversa antes de corrigir
registry por registry.

**Consequência concreta que achei ao seguir esse fio**: em `RoutineAISystem.java` (~linha 509),
o laço que escolhe com quem uma NPC vai puxar conversa espontânea itera `SimTale.ACTIVE_NPCS`
inteiro — todos os mundos — e escolhe o "mais próximo" comparando `Vector3d` cru, sem checar se o
outro NPC está no mesmo mundo que quem está procurando. Com um mundo só, nunca aparece. Com dois
mundos carregados, uma NPC pode calcular "distância" contra alguém de outro mundo (coordenadas que
não têm relação nenhuma) e tentar puxar ela pra socializar — na melhor hipótese ela nunca chega
(timeout), na pior é território que eu não testei (dependendo de como a engine trata um
`Ref`/`Store` de mundo diferente, pode até lançar exceção ao ler o componente). Vi o mesmo padrão
(iterar `ACTIVE_NPCS` sem filtrar mundo) num comando de debug em `SimTaleCommand.java` (~linha
2040), aí com impacto bem menor por ser um comando manual de teste.

---

## 🟡 Redundâncias de código (duplicação que já causou bug antes)

### 2. Lógica de reanexar NPC duplicada linha a linha

O fluxo que "readota" uma NPC cujo componente não sobreviveu a um reload de mundo está copiado
quase idêntico em dois arquivos: `SimTaleEventHandler.java` (clique direito, ~linhas 178-207) e
`SimTaleUseNPCInteraction.java` (tecla F, ~linhas 90-113) — as mesmas ~25 linhas (buscar
`UUIDComponent`, carregar o registro no shell `simtale`, resolver o nome, recriar o
`SimNPCComponent`, `addComponent`). Hoje as duas cópias estão consistentes. Mas essa duplicação é
**exatamente** a causa do bug antigo que o seu próprio checklist documenta: "o código de adoção
estava duplicado em dois handlers... corrigir só o primeiro não adiantou nada, porque o teste foi
com F". O risco não desapareceu, só foi corrigido uma vez — a próxima mudança em um dos dois
caminhos (um novo guard, um novo tipo de registro) tem toda a chance de esquecer o outro. Vale
extrair isso pra um método único, tipo `SimNPCReattach.tryReattach(Ref, EntityStore/CommandBuffer)`.

### 3. Limiares de fome duplicados em 4 lugares, sem fonte única

Três constantes nomeadas, cada uma num arquivo diferente:

| Constante | Valor | Arquivo |
|---|---|---|
| `HUNGRY_ENOUGH_TO_EAT` | 70 | `InteractionManager.java:431` |
| `HUNGER_INTERRUPT_THRESHOLD` | 25 | `RoutineAISystem.java:91` |
| `STARVATION_THRESHOLD` | 5 | `NPCHungerHelper.java:69` |

E a tela de interação (`NPCInteractionPage.java:828-838`) repete os mesmos três números **como
literais soltos** (`hunger < 5`, `< 25`, `< 70`) em vez de referenciar as constantes. Hoje os
quatro lugares batem certinho. O problema é que essa mesma tela já ficou dessincronizada uma vez
antes — o checklist relata que ela chegou a mostrar "50 procura, 5 perde vida", os dois errados,
antes de ser ajustada manualmente. Sem uma fonte única, qualquer futuro ajuste de balanceamento
(um número que hoje está "chutado", nas palavras do próprio checklist) tem 4 lugares pra lembrar
de mudar, e é fácil esquecer um.

---

## 🟢 Achados menores

**15 blocos `catch (Exception ignored) {}`** espalhados pelo código: `BabyCareManager.java` (6
ocorrências), `HouseManager.java` (3), `GrowthManager.java`, `SimNPCPersistence.java`,
`NPCDoorHelper.java`, `SimNpcPlayerListHelper.java` (2), `SimTaleItemRegistry.java`. A maioria é
parse defensivo de `UUID.fromString` sobre dado que pode estar corrompido/ausente — baixo risco,
comportamento aceitável. Um caso é mais preocupante: `SimNPCPersistence.java:386-391` tenta
`addComponent`, e se falhar tenta `putComponent`, e se **essa também falhar**, ignora em silêncio.
Se as duas falharem, a entidade fica carregada no mundo sem o componente do SimTale — vira uma
"NPC fantasma" que não vai se comportar como NPC — e não sobra nenhuma linha de log dizendo que
isso aconteceu. Vale pelo menos logar no catch mais externo, já que é exatamente o tipo de falha
silenciosa que o `AUDITORIA.md` já teve que caçar antes em outros pontos do código.

**Palavra "some" ainda dispara `INSULT_CHAT`** (`SimTaleChatHandler.java:727`, dentro de
`hasWord`). É uma conjugação comum do verbo "sumir" ("ele sempre some por aí") e não
necessariamente uma ofensa — risco baixo de falso positivo, mas fácil de acontecer numa conversa
qualquer.

---

## O que não encontrei

Nenhuma comparação de `String` com `==`, nenhum recurso (`Scanner`/`FileReader`/etc.) aberto sem
fechar, nenhum comando duplicado registrado duas vezes (`SimTaleCommand` tem 36+ subcomandos, os
nomes de todos os `super("...")` em `SimTaleCommand.java`, `DebugCommands.java`,
`BuildCommand.java` e `SimDebugCommand.java` são únicos), e os `.get(0)`/`.getFirst()` que existem
no código estão todos guardados por uma checagem de lista vazia antes. `TODO`/`FIXME`/`HACK` não
aparece em lugar nenhum do código-fonte.
