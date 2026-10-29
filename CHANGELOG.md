# Changelog

## [1.3.0] — 2026-10-20

### Correções de interações

- Baús colocados **dentro da vila e fora de qualquer casa** agora são **baús globais**.
  Eles não têm dono e qualquer morador pode retirar comida deles.
- Os NPCs continuam priorizando os baús da própria casa. Quando a despensa de casa não tem
  comida — ou quando o NPC não tem casa — eles procuram nos baús globais/compartilhados.
- Baús privados fora de uma casa continuam ignorados, para os NPCs não saquearem baús de loot
  do mundo.
- `/simtale forceeat` agora inicia a busca pelo baú no mesmo tick e não é interrompido pelo sono
  noturno antes de o NPC chegar ao baú.
- NPCs não sentam mais em bancadas de trabalho, fornalhas, bigornas ou outras estações de
  crafting. Só móveis de assento reais contam: cadeiras, banquetas, sofás, poltronas e bancos
  de mobília.
- A casa de um NPC morto é liberada imediatamente, em vez de continuar marcada como “ocupada”
  com um dono que não existe mais.

### Alterações para NPCs

- As necessidades agora **persistem entre reinícios** do servidor (fome, energia, social,
  diversão e higiene).
- A busca por comida acontece com fome abaixo de **70** (ocioso) e a interrupção de tarefa com
  fome abaixo de **25**. O ritmo pode ser acelerado pela configuração.
- Trabalhadores têm **30% de chance** de levar o excedente para um baú global da vila mesmo
  quando o baú de casa ainda tem espaço. Antes o baú da vila só era usado quando o de casa
  enchia.
- NPCs sem-teto são atribuídos automaticamente a casas registradas e vazias ao entrar no mundo.
- A regeneração da vila também considera o interior de casas registradas, mesmo que a casa
  esteja um pouco fora do raio calculado da vila.
- O trabalho agora tem prioridade sobre lazer, cadeiras, conversas e passeios ociosos. Antes,
  Mineradores e Caçadores podiam passar a sessão inteira sem nunca chegar à expedição.
- Todas as profissões seguram a ferramenta na mão: picareta, arco curto, enxada, machado,
  armadilha de pesca, martelo ou mapa. A ferramenta usa um slot vazio para não apagar itens
  coletados, e o Guarda continua com a arma exata que recebeu.
- Partidas e retornos de expedição de Minerador/Caçador agora aparecem no log do servidor.
- Guardas ficaram mais fortes: recebem **vida máxima dobrada**, **+50 de armadura**, **+10 de
  dano físico** e **25% de redução de dano**. Os bônus são aplicados ao nascer, ao receber o
  emprego e periodicamente; são removidos se o NPC trocar de profissão.
- O combate dos guardas agora tem **golpes reais**: eles correm até o hostil, desferem cerca de
  três ataques visíveis e o bicho morre pela própria animação de morte, em vez de simplesmente
  desaparecer. Se o alvo recuar, o guarda volta a perseguir.
- Quando um hostil entra na vila, os **dois guardas mais próximos** daquela vila largam a
  patrulha e vão até ele, mesmo que estejam longe. Antes só reagiam se o bicho passasse a menos
  de 10 blocos de um guarda. A detecção também cobre zumbis, ghouls, wraiths, lobisomens,
  hounds desbotados, cavaleiros das sombras, **trorks**, goblins, scaraks, outlanders, hederas e
  fen stalkers, além de esqueletos.

### Estabilidade e NPCs desaparecidos

- NPCs do SimTale agora removem qualquer `SpawnMarkerReference` que outro mod tenha anexado.
  O sistema de Spawning do Hytale removia a entidade 30 segundos depois de o marcador sumir
  (`NPCEntity despawning due to lost marker`), o que fazia a guarda desaparecer sem passar pelo
  fluxo de morte e sem causa registrada.
- Remoções externas de NPCs agora geram um aviso no log com nome, tarefa atual e último dano,
  deixando claro que não foi uma morte normal do SimTale.
- Novo comando `/simtale respawnmissing [nome]`: lista NPCs salvos que estão sem entidade no
  mundo e recria um deles pelo nome (ou parte do nome), reaproveitando o mesmo fluxo de revive do
  cemitério e remapeando relacionamentos, família e casa.
- Plumbobs e balões de pensamento agora possuem componente `Velocity`. A ausência dele podia
  derrubar o mundo com um `NullPointerException` no sistema de avoidance dos NPCs.
- NPCs não morrem mais afogados ou sufocados por ficarem presos em água/blocos: esse dano é
  cancelado e o NPC é levado de volta para a cama ou casa. O log de remoção externa agora mostra
  a causa real (`Drowning`, `Suffocation`, etc.) em vez de `por `.
- A checagem de 0 HP agora também roda durante expedições de Minerador/Caçador, em vez de deixar
  uma morte por dano externo sem passar pelo fluxo de morte.

### Casas e vila

- Corrigido o bug em que **ar era tratado como chunk não carregado**. Isso fazia toda varredura
  de casa falhar com `TOO_LARGE_OR_UNENCLOSED` e impedia camas novas de virarem casas.
- Móveis como mesas, bancadas, cadeiras, lâmpadas e baús agora são **atravessados** pela
  varredura de casa. Eles são registrados para os requisitos, mas não bloqueiam o cômodo.
- Workbench agora conta como **superfície** (`SURFACE`), não como assento.
- Uma cama válida colocada perto de uma vila registra uma **casa sem dono** imediatamente. O
  primeiro imóvel válido de um mundo também pode iniciar uma vila.
- Casas vazias são atribuídas ao adulto sem-teto mais próximo; casas cujo dono morreu são
  liberadas e podem ser reocupadas.
- O Blueprint agora diferencia casa aberta/grande demais de chunk não carregado.

### Mapa e interface

- Marcador da casa passou para o **centro do interior**, não para a primeira cama.
- Casa com morador aparece verde e com o nome; casa vazia aparece azul; cama sem dono aparece
  cinza.
- Uma casa com dono mas sem nome resolvido não fica mais azul: aparece como “Casa (Ocupada)”.
- Os ícones de gostos/desgostos crescem com a **afinidade**:
  - 0–19: 2 gostos, 0 desgostos;
  - 20–39: 3 gostos, 1 desgosto;
  - 40–59: 4 gostos, 2 desgostos;
  - 60–79: 5 gostos, 4 desgostos;
  - 80–100: todos.
- Um `+N` no fim da linha mostra quantos ícones ainda estão ocultos.
- Ícones de preferências aumentados de 24 para 36 px; ícones de profissão/hobby de 28 para 34 px.

### Vila segura e regeneração

- NPCs do SimTale dentro da fronteira da vila não podem tomar dano (mobs, queda, fogo ou
  jogador). `/simtale forcekill` continua funcionando.
- Ao entrar na vila, o jogador recebe um efeito de **regeneração** via RuneCore
  (`Food_Health_Regen_Medium`), renovado enquanto permanecer na área.

### Morte e NPCs desaparecidos

- A causa da morte agora é salva no registro do NPC e exibida no cemitério.
- Ao entrar no mundo, o jogador é avisado quantos NPCs estão enterrados.
- O log do servidor registra a morte e a causa.
- A casa do NPC falecido é liberada e pode ser atribuída a outro morador.

### Compatibilidade com outros mods

- O log da sessão de 28/10 mostra plugins mirando versões antigas do servidor: **RuneCore**,
  **Automatic Slab Placement**, **Arm Cannons**, **Composter Table** e **BetterSigns**.
- O BetterFood registra um spawn com o role inexistente `Spectre_Void`, e o Patchwork falha ao
  gerar arquivos em `/mnt` (`Operação sem suporte`). Nenhum desses é causado pelo SimTale, mas
  todos aparecem como erros no boot e devem ser atualizados/desabilitados.

### Configuração

Novas opções no `simtale-config.json`:

```json
{
  "hungerDecayMultiplier": 1.0,
  "villageChestDepositChance": 0.3,
  "villageSafeZoneEnabled": true,
  "villageRegenEnabled": true
}
```

- `hungerDecayMultiplier`: multiplica a velocidade da fome. `1.0` mantém o ritmo original,
  `10.0` acelera bastante e `0` congela a fome.
- `villageChestDepositChance`: chance de 0 a 1 de usar o baú da vila mesmo com espaço em casa.
- `villageSafeZoneEnabled`: liga/desliga a proteção dos NPCs dentro da vila.
- `villageRegenEnabled`: liga/desliga a regeneração ao entrar na vila.

### Documentação

- A pasta `docs/` do projeto foi removida.
- A wiki passou a ser a documentação canônica, com as páginas em inglês e PT-BR atualizadas
  para as mudanças acima.
- Comentários e referências antigas a `docs/` foram apontados para a wiki.

### Testes

```text
All tests passed (1873 assertions)
Wiki build: en + pt-BR OK
```
