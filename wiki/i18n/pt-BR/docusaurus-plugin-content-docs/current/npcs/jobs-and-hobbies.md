---
sidebar_position: 3
title: Empregos e hobbies
---

# Empregos e hobbies

## Empregos

| Emprego | O que faz |
|---|---|
| `DESEMPREGADO` (`UNEMPLOYED`) | Nada em especial |
| `MINERADOR` (`MINER`) | Sai em expedições de mineração e volta com minérios |
| `FAZENDEIRO` (`FARMER`) | Reivindica um Espantalho (Scarecrow), colhe e replanta plantações específicas |
| `PESCADOR` (`FISHERMAN`) | Reivindica um Posto de Pesca (Fishing Post) e coleta peixes |
| `LENHADOR` (`LUMBERJACK`) | Reivindica um Posto de Lenhador (Lumber Post) e coleta madeira. (Ele é um lenhador, mas bem calmo. Sem fúria por favor) |
| `GUARDA` (`GUARD`) | Vigia noturna — dorme de dia |
| `EXPLORADOR` (`EXPLORER`) | Explora a região |
| `CONSTRUTOR` (`BUILDER`) | Caminha até os locais de construção |
| `CAÇADOR` (`HUNTER`) | Sai em expedições de caça e volta com carne crua |

### Atribuindo um emprego

Segure a ferramenta correspondente e use **Atribuir Trabalho** (Assign Job) no painel de interação.

| Emprego | Gatilho contém no nome da ferramenta |
|---|---|
| Minerador | `Pickaxe` (picareta) |
| Fazendeiro | `Hoe` (enxada) |
| Pescador | `Tool_Fishing_Trap` (armadilha de pesca) |
| Lenhador | `Hatchet` (machadinha) |
| Guarda | qualquer arma reconhecida — corpo-a-corpo (espada, machado, adaga, lança, maça, ...) ou à distância (arco, besta, arma de fogo, ...) |
| Explorador | `Tool_Map` (o item mapa/bússola) |
| Construtor | `Tool_Hammer` (martelo) |
| Caçador | `Shortbow` (arco curto) ou `Crossbow` (besta), especificamente |

Um NPC pode recusar: cada um deles possui uma lista aleatória de trabalhos que gosta e trabalhos que odeia.

### Guardas e categorias de arma

O Guarda não fica mais preso à espada. Dar qualquer item que o SimTale reconheça como arma atribui
o emprego de Guarda, e o Guarda lembra se aquela arma era corpo-a-corpo ou à distância:

- **Corpo-a-corpo** (espada, machado, adaga, lança, maça, ...): o Guarda se aproxima até cerca de
  2,5 blocos antes de lutar, como sempre.
- **À distância** (arco, besta, arma de fogo, ...): o Guarda para a uma certa distância (cerca de
  7 blocos) em vez de andar até o alcance corpo-a-corpo.

Um arco ou besta continua tornando o NPC um **Caçador**, não um Guarda — essa checagem roda
primeiro, então nada mudou para o Caçador.

Como uma arma nova (de outro mod, ou de uma futura atualização do SimTale) não é algo que o
SimTale consiga prever pelo nome, existem duas formas de ensiná-lo: outro mod chamando
`WeaponCategoryRegistry.register("idDoItem", WeaponCategory.RANGED)` no próprio código dele, ou um
arquivo `simtale-weapons.json` na pasta onde o servidor roda, listando entradas
`{"idDoItem": "MELEE"}` / `{"idDoItem": "RANGED"}` manualmente. Nenhum dos dois é necessário para
as armas que já vêm no Hytale.

### Restrições de trabalho infantil

Apenas NPCs nas fases de vida `ADOLESCENTE` (`TEEN`) ou `ADULTO` (`ADULT`) podem receber empregos. Crianças pequenas (`BABY`, `TODDLER`, `CHILD`) são protegidas e recusarão ferramentas ou serviços ("Eu sou só uma criança!"). No entanto, crianças podem praticar livremente hobbies de lazer como pesca ou jardinagem quando sua diversão baixar, permitindo que fiquem perto das plantações ou lagos da família sem compromisso econômico formal.

:::caution A correspondência é feita pelo nome do item
A checagem procura por essas palavras dentro da ID do item, então um item de mod sem nenhuma dessas palavras no id ainda precisa de `WeaponCategoryRegistry.register(...)` ou `simtale-weapons.json` (veja acima) — ou, para os empregos que não são Guarda, simplesmente não será reconhecido.
:::

### O Fazendeiro

<div style={{textAlign: 'center'}}>
  <img src="/img/farmer_job.png" alt="O Fazendeiro" />
</div>

O Fazendeiro exige um **Espantalho** (Scarecrow) para atuar como sua estação de trabalho. Ele caminhará até as plantações maduras, irá colhê-las (deixando cair 1 produto e 1-2 sementes), e então levará tudo para um baú em sua própria casa. Se ele tiver sementes e houver solo arado vazio por perto, ele replantará. 
As plantações suportadas são: Cenoura, Trigo, Tomate e Milho.

### Expedições: Mineradores e Caçadores

Mineradores e Caçadores não caminham ativamente até pedras ou animais. Em vez disso, eles saem em "Expedições".
Quando o turno de trabalho começa, o modelo deles diminui (encolhe) e eles conceitualmente desaparecem por cerca de 2 minutos reais. Quando retornam, eles surgem de volta no Centro da Vila ou em suas casas, carregando espólios baseados em uma tabela de probabilidades.
- **Mineradores** voltam com minérios (Cobre, Ferro, Prata, Ouro, Adamantium, etc.).
- **Caçadores** voltam com Carnes Cruas (Boi, Porco, Frango).
Eles caminham imediatamente até o baú de suas casas para depositar os espólios.

### O Pescador

<div style={{textAlign: 'center'}}>
  <img src="/img/fisherman_job.png" alt="O Pescador" />
</div>

O Pescador exige um **Posto de Pesca** (Fishing Post). Para criar um, basta colocar um bloco de **Armadilha de Pesca** (Fishing Trap) perto da água (a até 8 blocos de distância).
Ele caminhará até esse posto, fará sua animação de coleta, e então depositará os peixes coletados no baú de casa.

### O Lenhador

<div style={{textAlign: 'center'}}>
  <img src="/img/lumberjack_job.png" alt="O Lenhador" />
</div>

O Lenhador exige um **Posto de Lenhador** (Lumber Post). Para criar um, basta colocar um bloco de **Bancada de Serraria** (Lumbermill Bench) perto de um tronco de árvore (a até 10 blocos de distância).
Ele caminhará até esse posto, fará sua animação de coleta, e então depositará a madeira coletada no baú de casa.

## Hobbies

| Hobby | Para onde ela vai |
|---|---|
| `PESCAR` (`FISHING`) | água mais próxima |
| `MINERAR` (`MINING`) | pedra mais próxima |
| `JARDINAGEM` (`GARDENING`) | plantação mais próxima |
| `LER` (`READING`) | casa |
| `DORMIR` (`SLEEPING`) | casa |

Quando a diversão cai abaixo de 40, ela vai e pratica o seu hobby, voltando feliz.

Se o cenário não existir — um pescador no deserto, por exemplo — ela não fica presa procurando infinitamente. Ela desiste e relaxa em casa, recuperando a diversão mais lentamente.

### Hobbies importam socialmente

- **Presentes**: dar uma vara de pescar para alguém cujo hobby é pescar vale muito mais do que um presente comum.
- **Conversa**: dois NPCs com o mesmo hobby constroem amizade mais rápido.
- **Trabalho**: um fazendeiro cujo hobby é jardinagem *ganha* diversão ao colher. Um que preferiria estar lendo perde um pouco de diversão durante o trabalho.
