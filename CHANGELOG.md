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
