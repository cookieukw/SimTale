# Changelog

## [1.4.1] — 2026-11-09

- Migrados os dados de crescimento e cuidados com bebês para o shell próprio do SimTale, com
  cópia repetível dos registros antigos e preservação do backup de `default.db` do Caskara.
- Corrigida a chave dos baús sem mundo e adicionada migração das chaves antigas `_x_y_z`.
- Atualizadas as receitas para usar `TimeSeconds`, aceito pelo schema atual do Hytale.
- Atualizado o alvo de compatibilidade para a release 0.7.x e a pre-release 0.8.0-pre.2.1.
- Adicionada a opção `hostileMobsCanAttackNpcs` (desligada por padrão); sem ela, dano do jogo não
  mata NPCs. Quando ativada, apenas mobs hostis reconhecidos podem atacá-los fora da zona segura.
- A proteção padrão também usa o componente nativo de invulnerabilidade do Hytale; guardas são os
  únicos moradores que enfrentam hostis, enquanto os demais procuram abrigo em casa e permanecem
  deitados até a ameaça passar, sem alternar entre dormir e levantar durante o dia.
- Adicionada uma banheira craftável com colisão própria, assento e rotina de higiene dos NPCs; o
  mapa agora marca banheiras colocadas e volta a registrar os marcadores após recriar o mundo.
- O cemitério registra e exibe a data da morte, ordena os NPCs do mais recente ao mais antigo e
  informa a causa quando disponível.
- Ressurreições preservam o estágio, a escala e a variante visual de filhos NPC.

## [1.4.0] — 2026-11-06

Esta versão reúne tudo o que foi adicionado depois da 1.3.1. A versão 1.3.2 foi um build interno
usado durante o desenvolvimento do mercador viajante; as mudanças dela estão incluídas aqui.

### NPCs seguindo o jogador

- Adicionado `/simtale follow` para fazer o NPC mais próximo seguir o jogador.
- Adicionado `/simtale follow stop` para liberar os NPCs que estão seguindo.
- Adicionados **Follow** / **Stop Following** ao menu de interação do NPC.
- NPCs comuns mostram a ação em até 6 blocos; guardas podem ser mandados seguir sempre.
- Seguir não exige amizade, romance ou intimidade.
- Seguidores usam o pathfinding nativo em vez de teleportar, param a uma distância natural e
  retomam o follow depois de dormir, comer ou cumprir outra tarefa temporária.
- Seguidores andam quando estão perto e correm quando ficam longe; as animações de caminhada e
  corrida agora alternam corretamente.
- Slots de animação presos são limpos ao iniciar o follow, evitando que o NPC fique parado na
  pose errada.
- Mensagens e rótulos do follow localizados em inglês, português e espanhol.

### Casas e validação de casas

- Limite de interior suportado aumentado de 512 para 2.048 blocos.
- A validação agora distingue casas grandes, telhados/aberturas expostos e chunks não carregados.
- Aberturas de até dois blocos são toleradas, permitindo acesso de varanda ou pequenas falhas
  decorativas sem tratar o exterior inteiro como interior da casa.
- Cômodos abertos não espalham mais a varredura pelo mundo até o antigo limite rígido.
- O Blueprint da Casa mostra diagnósticos mais claros para casas grandes demais ou abertas.
- O Blueprint da Casa e os itens de inspeção usam o mundo atual do jogador em vez de assumir o
  primeiro mundo carregado.

### Postos de trabalho

- Adicionados registros por mundo para Postos de Pesca, Postos de Lenha e Postos de Fazenda.
- Reivindicação, liberação, remoção e descoberta no boot dos postos agora são isoladas por mundo.
- Marcadores de postos são únicos por mundo e mostram se o posto está disponível ou ocupado.
- Postos de Pesca revalidam o alvo de água antes de um pescador reivindicá-los.
- Um Posto de Pesca redireciona automaticamente para água próxima quando o bloco de água original
  é removido.
- Um Posto de Pesca sem alvo de água restante é liberado em vez de mandar o NPC para o ar.
- Rótulos dos marcadores de postos localizados em inglês, português e espanhol.
- Corrigida uma condição que impedia trabalhadores sem reserva social ativa de iniciar o trabalho.

### Isolamento por mundo e persistência

- Adicionadas APIs explícitas de persistência por mundo para dados do SimTale e cemitérios.
- Registros de camas, índices de casas, baús, postos de trabalho e snapshots de vila agora
  suportam acesso por mundo.
- Rotinas, crescimento, gravidez, cuidado com bebês, construção, portas, comportamento de guarda,
  estoques, marcadores de mapa e ferramentas de debug resolvem o mundo a partir da entidade ou do
  jogador relevante.
- Snapshots de mapa e sobreposições da vila são isolados por mundo.
- Verificações de zona segura usam a vila do mundo atual do NPC.
- A descoberta de baús compartilhados e os resumos de estoque da vila não selecionam mais baús de
  outro mundo.
- Buscas por casa, família e revival de NPC usam índices de casas do mundo correto.

### Segurança da vila, guardas e defesa civil

- NPCs dentro da zona segura da vila são protegidos de dano normal, incluindo dano de jogadores,
  mobs, fogo e quedas.
- Se um caminho de dano direto ou de debug ainda reduzir o NPC a 0 HP dentro da zona segura, o
  SimTale restaura a vida em vez de entrar no fluxo de morte.
- A remoção externa de NPCs continua registrada no log com nome, tarefa e última causa de dano.
- Residentes desaparecidos podem ser recuperados com `/simtale respawnmissing [nome]`.
- Guardas agora reconhecem animais hostis além dos mortos-vivos e criaturas já cobertas: aranhas,
  ursos, aranhas de caverna, lobos gigantes (dire wolves) e javalis.
- Moradores comuns também reagem a hostis próximos enquanto estão ociosos ou passeando. Eles usam
  a via de combate civil, com dano menor (cerca de 10% da vida máxima por golpe) e sem os bônus de
  vida, armadura e redução de dano dos guardas.

### Aparência e roupas dos NPCs

- Adicionada uma camiseta base neutra sob as camadas de roupa superior.
- Corrigidas variantes adultas, infantis, de Natal e de Halloween que podiam deixar o torso
  superior visualmente descoberto.
- Scripts de geração de modelos atualizados para manter a camiseta base automaticamente.
- 1.089 variantes de modelo geradas foram corrigidas.

### Mapa e interface

- Snapshots de marcadores atualizam imediatamente depois que casas, vilas, camas, postos e móveis
  reconhecidos mudam.
- Marcadores de casa são lidos do índice de casas do mundo atual.
- Baús globais próximos são agrupados em um único marcador de mapa, com contagem, e o rótulo é
  localizado.
- A invalidação de chunks do mapa agora atinge apenas o mundo que mudou, corrigindo chunks azuis
  ou marcados como não carregados em outros mundos.
- NPCs ausentes em expedições ou jornadas de trabalho não deixam mais plumbob nem marcador de mapa
  para trás.
- Painéis de interação com NPC foram reorganizados em seções mais claras de necessidades,
  relacionamentos, preferências, detalhes e ações.
- Rótulos de fallback da interação agora usam chaves de tradução em vez de texto fixo.
- Traduções completas de Follow e postos de trabalho adicionadas para `pt-BR` e `es-ES`.
- Adicionada validação estrita de locales para paridade de chaves, placeholders, entradas
  duplicadas, valores vazios, BOM e codificação UTF-8.

### Mercador viajante

- Adicionado um mercador viajante temporário que visita a vila por cerca de 30 minutos e depois
  vai embora.
- As verificações de chegada rodam a cada 15 minutos, exigem pelo menos três moradores e têm 25%
  de chance, para que as visitas pareçam um evento em vez de tráfego constante.
- O mercador chega em chão já usado por aldeões, a pelo menos 28 blocos do jogador e dentro da vila
  mais próxima.
- Cada visita sorteia nome, variante de corpo, gênero e especialidade: mercadorias gerais,
  mantimentos, minério, remédios, curiosidades ou sementes.
- O mercador veste o traje clássico (boina, túnica, calça com cinto e luvas), com cinco aparências
  masculinas e cinco femininas geradas a partir de rostos de moradores existentes.
- Cada visita sorteia cinco ofertas de um catálogo com cerca de 400 itens vanilla (minérios,
  barras, gemas, comida, poções, sementes, ferramentas, armas, armaduras, decoração e
  ingredientes), em vez de três itens fixos. Uma entrada premium é garantida quando a
  especialidade tem uma.
- Estoque, preços e quantidades ficam fixos enquanto o mercador está presente e depois de recarregar
  o mundo.
- A tela de comércio mostra o ícone real de cada oferta, usa os fundos de qualidade do ItemSlot e
  lista cinco entradas por especialidade.
- Ofertas esgotadas escondem o botão **Buy** e mostram **Sold Out** no lugar.
- Adicionadas ações de compra e venda. Vender devolve os itens ao estoque do mercador e paga o
  jogador em moedas.
- Interagir com o mercador abre direto o balcão de comércio, sem ficha de morador, painel de
  necessidades, conversa, flirt ou bronca.
- Mercadores não sorteiam mais uma profissão comum ao nascer, então nunca aparecem segurando
  picareta, enxada, arco ou outra ferramenta de profissão.
- Abrir e fechar o balcão limpa o slot de animação de movimento e libera o mercador corretamente,
  para que chegada e saída usem a animação normal de caminhada.
- Fechar o balcão libera explicitamente a pose do mercador, então vender e sair da loja não deixa
  mais o clipe de caminhada preso no idle.
- Visitas, estoque restante e identidade do mercador são persistidos com Caskara, para que reiniciar
  o servidor não duplique o mercador nem redefina o que já foi vendido.
- A tela de comércio está localizada em inglês, português e espanhol.
- Adicionado `/simtale merchant` para gerar uma visita de teste.

### Economia: moedas, porquinho e ouro

- Adicionadas `SimTale_Coin` (cobre), `SimTale_Coin_Thorium` e `SimTale_Coin_Gold`, com modelos 3D
  e ícones. Cem moedas de cobre equivalem a uma moeda de tório; cem moedas de tório equivalem a
  uma moeda de ouro.
- Receitas de conversão de barras: uma barra de cobre vira 100 moedas de cobre, uma barra de tório
  vira 100 moedas de tório e uma barra de ouro vira 100 moedas de ouro. As receitas de troca
  convertem 100 moedas de cobre em uma de tório e 100 de tório em uma de ouro.
- Adicionado o item Porquinho e sua tela de depósito/saque. As moedas guardadas são usadas
  automaticamente pelo mercador quando o inventário não tem o suficiente, e o troco de vendas
  transborda para a poupança quando o inventário está cheio.
- Blocos decorativos de ouro agora podem ser desmontados na Bancada de Trabalho: cada
  `Rock_Gold_Brick` ou variante (parede, viga, pilar, telhado, escada, peça lisa, ornamentada ou
  meio-bloco) rende uma Barra de Ouro.

### Estabilidade

- Corrigido um `NullPointerException` ao procurar a cama de um filho quando o registro do pai ou da
  mãe não existia mais.

### Ferramentas e validação

- Adicionada configuração do Crowdin e sincronização via GitHub Actions para os recursos em inglês,
  português e espanhol.
- Adicionadas verificações automatizadas para arquivos de UI, consistência de locales e recursos
  gerados.
- A suíte completa de testes, a validação estrita de locales e a validação de UI passam depois
  dessas mudanças.

## [1.3.1] — 2026-10-28

Esta versão consolidou o conjunto de recursos da linha 1.3.x. Não houve mudança de jogabilidade
em relação à 1.3.0 além dos ajustes já listados abaixo.

- A pasta `docs/` foi removida; a wiki passou a ser a documentação canônica em inglês e PT-BR.
- A suíte de testes passou a registrar 1.881 asserções.
- O build da wiki em inglês e PT-BR foi validado junto com a release.

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
