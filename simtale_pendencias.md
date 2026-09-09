# SimTale — o que falta testar, segundo o `testing_checklist.md`

Revisão do checklist (última conferência interna: 10-11/08) contra o que já tem `[x]` **com
observação preenchida** de verdade. Segui a própria regra do arquivo: um `[x]` cuja linha `→`
ficou em branco, ou que diz "não testado"/"nunca testado", conta como **pendente**, não como
feito — só o texto do risco não garante que alguém jogou e viu funcionar.

Numeração das seções é a mesma do arquivo original, para ser fácil de achar lá.

---

## 🔴 Sistemas inteiros ainda sem nenhum teste em jogo

Estas quatro seções são funcionalidades novas (10-11/08) com o design e o código prontos, mas
**zero itens confirmados** — vale priorizar antes de qualquer coisa, porque são as áreas com
mais risco de esconder um bug de fundação (como aconteceu com cama/baú antes).

**Seção 19 — Cemitério e ressurreição** (12 itens, nenhum testado): lista aparece no painel,
cemitério vazio mostra aviso, botão Ressuscitar funciona com o nome certo, item some da lista na
hora, casamento e filhos sobrevivem ao revive, amizades sobrevivem, funciona com o cônjuge fora
de alcance, cama/casa antiga é reavida sem despejar ninguém, dono da casa volta a ser dono dos
baús, não dá pra ressuscitar duas vezes, sem texto em português vazando na versão em inglês.

**Seção 20 — Brigar com o filho** (9 itens, nenhum testado): o botão troca de "Insultar" para
"Brigar" com filho próprio; reação por estágio (criança triste, adolescente com raiva, adulto
relevando); uma briga não custa nada mas a 4ª no mesmo dia custa confiança/afinidade; o contador
reseta no dia seguinte; filho de outra NPC não é afetado. **O teste mais importante da seção**:
confirmar que os filhos voltam a crescer depois de um restart do mundo — a correção resolveu um
bug de fundação (`ACTIVE_CHILDREN` não recarregava do banco) que também travava o crescimento.

**Seção 21 — Colo e recorte de trabalho infantil** (9 itens, nenhum testado): botão "Pegar no
Colo" só aparece para filho próprio até `CHILD`; ela sobe no ombro e acompanha ao andar; desce
com agachar + botão direito; a rotina fica suspensa enquanto carregada; não é derrubada pela
limpeza de montaria; confirmar que a montaria não dá ao jogador controle sobre o movimento da
criança (ponto marcado como não confirmável só lendo o bytecode); criança não pode virar
Guarda/Caçador/Minerador mas continua podendo plantar; ao virar `TEEN` o emprego restrito
destrava sozinho.

**Seção 23 — Registro de baús persistido** (4 itens, nenhum testado): baús reaparecem sozinhos
depois de reiniciar o mundo (procurar "baú(s) recarregado(s) do banco" no log); baú longe do
spawn (fora do raio da varredura de entrada) também é registrado; baú quebrado não volta depois
de reiniciar; NPC com fome volta a achar comida usando o registro persistido.

**Seção 22 — Tema de madeira e telas sem "debug"** (8 itens, nenhum testado): é revisão visual
pura — conferir que não sobrou mancha nos cards de cama/baú, a barra de gravidez não tem mais as
marcas brancas, todas as telas (camas, baús, cemitério, gravidez, interação, gênero) ficaram
marrons, os títulos não mostram mais "debug" pro jogador, texto continua legível em cima do
marrom, botões âmbar/terracota com os destrutivos (Remover/Desvincular) ainda visivelmente
vermelhos, hover/pressed/disabled ainda respondem certo.

---

## 🟠 Comportamento nunca implementado (não é falta de teste, é falta de código)

Da seção 16 — três dos itens craftáveis ainda **não fazem nada** ao serem usados, segundo a
nota mais recente (11/08): **Sino da Vila** (`TownBell`, devia reunir os moradores), o
comportamento de item do **Contrato de Imigração** e o **Bolo de Aniversário** (`BirthdayCake`,
devia avançar o estágio de crescimento). Os outros quatro (Planta da Casa, Registro do
Estalajadeiro, Luneta do Almoxarife, Diário do Inspetor) foram implementados em 11/08 mas ainda
não têm teste confirmado em jogo (ver abaixo).

Também da mesma seção: não existe um **validador de build** automático (script no
`processResources`, nos moldes do `tools/check_ui.py`) para pegar os quatro tipos de erro de
asset que já derrubaram o servidor uma vez (raiz de textura errada, chave de tradução ausente,
vírgula sobrando no JSON, dimensão de textura fora do múltiplo de 32).

E um item de limpeza apontado no próprio documento: `Example_Recipe.json` transforma 10
`Soil_Dirt` em 1 `Soil_Dirt` — parece sobra de teste esquecida, vale conferir se é proposital.

---

## 🟡 Pendências por sistema (parcialmente testado)

**1. Casas e Camas** — falta testar quebrar a cama (a NPC libera a casa sem deixar registro
órfão?). Melhoria a avaliar, não testada: mover a cama de lugar cria uma casa nova?

**3. Sono** — o novo sono por horário do Guarda (dorme de dia, não deveria dormir à noite nem
exausto) tem 3 itens sem observação, incluindo a energia dele decaindo/recuperando no ciclo
invertido.

**4. Fome e Alimentação** — destravado pelo fix do registro de baú, mas ainda não testado:
escolha por tier de comida, desempate por gosto (odiada vs. neutra), interrupção de tarefa por
fome abaixo de 25, comportamento com baú inalcançável (não pode virar busca em loop), e cura de
vida ao comer.

**7. Socialização e Perambulação** — **ponto de atenção**: o usuário relata nunca ter visto duas
NPCs conversando espontaneamente até agora — vale investigar o gatilho (`social < 50` +
proximidade) antes de mais nada, porque sem isso toda a cadeia abaixo fica sem base pra testar:
relação NPC↔NPC subindo com o tempo, contágio de humor numa conversa, discussão entre NPCs com
trait `AGGRESSIVE`/relação `ENEMIES`, prioridade do sono sobre uma conversa em andamento, e o
timeout de destino inalcançável (~15s).

**8. Lazer e Hobby** — ir fazer o hobby quando `fun` cai abaixo de 40, o fallback sem cenário
(ex.: pescador no deserto), e "gostar do trabalho" (jardineiro ganha `fun` colhendo, leitor
perde) seguem sem teste. A reposição de `fun` durante o hobby tem confirmação **incerta**
("parece funcionar", palavra do próprio usuário) — vale retestar de forma mais direta antes de
marcar como resolvido.

**9. Relacionamentos e Família** — só falta a gravidez pela própria jogadora
(`PlayerPregnancyTickSystem`), que é um caminho de código separado da gravidez de NPC.

**10. Crescimento** — falta confirmar que o `GrowthTickSystem` roda sozinho e faz a transição de
estágio sem comando manual, e acompanhar o ciclo completo bebê → adulto pelo menos uma vez.

**11. Morte e Grim Reaper** — vários itens da fundação de morte por fome/velhice nunca foram
testados: fome abaixo de 5 abandonando profissão/hobby/socialização e chorando; a NPC faminta
ainda conseguindo comer ao alcançar um baú; sono não interrompido pela fome. Além disso,
**"Implorar pela vida"** (usar `Ingredient_Voidheart` no Ceifador durante a coleta para reviver
a NPC) nunca foi testado em jogo. O **cemitério em si** (arquivamento no shell
`simtale_graveyard`) também não foi confirmado, nem se a casa/cama voltam a ficar livres depois
da morte, nem o que acontece com cônjuge e filhos de quem morreu.

**12. Construção** — falta testar o holograma nativo via `/build` (translúcido, some ao
concluir), a NPC realmente construindo (anima e a estrutura sobe) e cancelar a construção no
meio sem deixar holograma órfão ou canteiro travado.

**14. IA Generativa e Chat** — dois itens de robustez sem teste: o prompt hoje não leva
localização/vida/casa/players próximos/horário/relacionamento (item de roadmap, não bug — vale
anotar onde isso faz a NPC "falar bobagem"), e o comportamento sem internet ou com a API fora do
ar (tem que cair num fallback, não travar nem floodar erro).

**15.5 Vilas** — falta testar: a vila sumir quando as casas que a formam são destruídas; o
morador de uma cama quebrada procurar outra em vez de tentar voltar pra cama que não existe
mais; NPC sem casa numa vila passear sem se afastar pra sempre; o Guarda patrulhando o perímetro
da vila (12 paradas em círculo); e o Guarda sem vila nenhuma por perto ficando parado em vez de
perambular.

**16. Itens (os 4 já implementados)** — Planta da Casa, Registro do Estalajadeiro, Luneta do
Almoxarife e Diário do Inspetor foram codificados em 11/08 mas nenhum tem teste em jogo ainda,
incluindo o holograma de destaque da Planta da Casa não ficar órfão ao inspecionar duas casas
seguidas. A aparência na mão dos 8 itens novos está marcada como visualmente ok (10/08), mas
ainda falta confirmar comportamento de uso de 3 deles (ver seção 🟠 acima).

**18. NPC congelada / dump do `npcstate`** — falta confirmar que NPC suja sem água por perto não
trava mais (só perambula), que o backoff de banho (`cooldowns: bath=N`) aparece contando no
dump, e que perambular funciona como fallback real quando fome **e** higiene estão no vermelho
ao mesmo tempo.

**🐞 Aberto, a investigar** — dois itens de retestar depois do fix de 11/08 (fazendeira só
trabalha perto de um espantalho reivindicado; não sai andando para um espantalho a mais de 48
blocos).

---

## 🔵 Comandos nunca testados

Segundo a tabela de comandos (conferida no código em 09/08), três subcomandos existem e nunca
foram usados de verdade: `/simtale aistatus`, `/simtale forcebabyswap` e `/simtale
setprofession`.

Vários outros foram corrigidos recentemente e estão marcados **"retestar"**: `debugnear`,
`chestcheck`, `forcework`, `forceplacebaby`, `forceconstruct`, `setmood`, `forcekill`. E o
`unstick` tem um problema conhecido, não corrigido: destrava a NPC da cama, mas ela sai andando
ainda dormindo.

---

## 📋 Passo final pendente

O próprio checklist termina com uma tarefa que ainda não foi feita: juntar todas as observações
(`→`) preenchidas até agora e separar em **bugs** (para corrigir já) e **melhorias** (para
entrar no `docs/ROADMAP.md`). Com tanta seção nova (19 a 23) ainda sem nenhuma observação, faz
sentido deixar essa consolidação para depois de fechar pelo menos essas rodadas de teste.

Vale lembrar também o aviso do `AUDITORIA.md`: a auditoria de código foi só estática porque o
ambiente não tinha JDK 21 — **compilar de verdade antes de subir num servidor real** ainda é um
passo em aberto ali.
