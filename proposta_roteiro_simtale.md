# Roteiro de Vídeo: SimTale — O The Sims Dentro do Hytale

---

### [00:00] INTRODUÇÃO: O THE SIMS NO HYTALE

[CENA: Câmera livre cinematográfica em terceira pessoa sobrevoando um vilarejo vivo no Hytale. NPCs caminhando, conversando, cuidando de plantações e interagindo entre si.]

**Locução:**
Eu recriei o The Sims completo dentro do Hytale.

Essa história começou lá atrás, quando eu criei uma versão de addon para o Minecraft Bedrock que basicamente transformava os villagers em players que interagiam pelo chat. Na época, eu acabei não atualizando e nem dando continuidade para esse addon por conta de várias limitações técnicas da plataforma. Mas aí o Hytale chegou e eu fiquei uns belos meses testando as paradas, fazendo mods mais simples e etc. Foi aí que surgiu a ideia de portar aquele mod que eu tinha feito para o Bedrock e ver até onde eu conseguia chegar. Foi assim que nasceu o SimTale, e isso foi em março desse ano.

Os NPCs padrão do Hytale são legais para preencher cenário, mas vamos ser sinceros: depois de alguns minutos você percebe que eles são quase estátuas ambulantes. Não têm casa, não têm história, não têm sentimentos e nem rotina. No início, a minha intenção era apenas portar aquele meu addon antigo e colocar caixas de diálogo simples. Só que, quando eu comecei a mexer na API e nas ferramentas do Hytale, a ficha caiu: a gente tinha em mãos um potencial absurdo para ir muito além de só um monte de texto na tela.

Eu decidi criar um simulador de vida mais completo. Cada habitante do mundo agora tem necessidades fisiológicas, personalidade, rotina de trabalho, memórias, relacionamentos amorosos e até a capacidade de casar, engravidar e construir família.

---

### [02:00] A PSICOLOGIA DOS NPCS: NECESSIDADES E PERSONALIDADE

[CENA: O jogador se aproxima de um morador da vila e clica com botão direito. A interface do SimTale abre, exibindo as barras de status, traços de personalidade e botões de interação.]

**Locução:**
Tudo começa na cabeça de cada npc. O coração do comportamento deles gira em torno de cinco necessidades fundamentais, exatamente como no The Sims: Fome, Energia, Social, Diversão e Higiene.

Se um morador passa o dia inteiro trabalhando sem comer ou sem tomar um banho, a energia dele se esgota. Ele fica visivelmente de mau humor e muda a postura e a forma como responde a você.

Só que ninguém é igual. Cada cidadão nasce com traços de personalidade próprios, como Amigável, Agressivo, Preguiçoso, Carente, Ganancioso ou Paranóico. E isso muda completamente várias interações e comportamentos. Se você tentar contar uma piada para um morador com fome que tem o traço Agressivo, ele provavelmente vai te dar um coice e mandar você sumir da frente dele. Já um NPC Carente vai comemorar qualquer atenção mínima que você der.

Além disso, eles têm preferências e gostos individuais: comidas favoritas, pratos que odeiam, itens preferidos para ganhar de presente, uma estação do ano favorita e até hobbies — como passar a tarde pescando na beira do rio, cuidando da horta ou explorando cavernas.

Se você descobrir a comida favorita de alguém e entregar de presente justamente quando essa pessoa estiver morrendo de fome, a afinidade e a confiança de vocês cresce bastante

Para sustentar tudo isso sem parecer repetitivo, eu escrevi e programei mais de 800 falas diferentes dentro do código , só que isso foi há tempos atrás, e atualmente tem beeem mais coisas que vou falar mais para frente. São 200 variações para homens, 200 para mulheres e 400 falas exclusivas para crianças, divididas entre meninos e meninas. Cada fala muda de acordo com o humor, a personalidade e o que está acontecendo na vila naquele momento.

E para fazer esses personagens andarem sem bugar no cenário, eu precisei inventar uma âncora invisível chamada Leash Point, que serve como referência para a física do Hytale guiar o caminho do NPC. Guarda bem esse nome, porque esse Leash Point salvou a gente de um dos bugs mais bizarros que vai surgir mais para frente

### [05:30] ARQUITETURA E LAR: O ALGORITMO DE CASAS E BAÚS INTELIGENTES

[CENA: Jogador montando uma casa do zero com blocos de madeira e pedra. O jogador coloca a porta, a cama, mesa, cadeira, iluminação e o baú. O NPC sem-teto reconhece o local na hora.]

**Locução:**
Beleza, os NPCs já tinham personalidade, necessidades e tudo mais. Mas eles ainda precisavam de um lugar para morar. E para esse sistema eu me inspirei bastante no Terraria e na forma como os habitantes de lá exigem casas adequadas.

Só que aqui teve um desafio técnico grande. Se cada morador ficasse escaneando os blocos do mapa o tempo todo tentando achar onde dormir, cem NPCs fariam milhões de checagens por segundo e iam derreter o processador do servidor. A solução que eu achei foi fazer o mod escutar o exato momento em que o jogador coloca uma cama no chão e cadastrar essa cama na hora dentro de um registro global, sem custo nenhum de desempenho.

A partir dessa cama, o algoritmo do SimTale faz uma validação rápida em duas etapas. Primeiro, um teste de curto-circuito: tem paredes ao redor? Se não tiver, ele nem perde tempo processando. Se tiver, ele checa se o cômodo está totalmente vedado, sem buracos no teto, e confere se a casa possui os itens obrigatórios: uma cama, uma mesa, uma cadeira, uma fonte de luz e um baú. Se faltar qualquer um desses itens, ou se o quarto for apertado demais, o morador simplesmente recusa a casa.

E o baú da casa funciona como o estoque da família. O NPC sabe onde fica o baú dele e o que tem guardado ali dentro. Se bater fome, ele vai sozinho até lá buscar comida; se ele colheu trigo na fazenda, guarda tudo na despensa.

E uma coisa que eu tive que tomar bastante cuidado no código foi garantir que eles nunca mexam nos baús dos vizinhos e nem tentem saquear baús de masmorras ou estruturas do mapa aberto.

---

### [09:00] AMOR E FAMÍLIA: CASAMENTO, GRAVIDEZ E CUIDADOS

[CENA: Jogador entregando o anel de casamento para uma NPC. Corações na tela. Depois, corte para o período de gravidez, o nascimento do bebê e os cuidados no berço.]

**Locução:**
Depois de construir uma casa e passar um bom tempo conversando e presenteando, o nível de afeto fica alto o suficiente pra você poder entregar o Anel de Casamento.

Quando casa, os dois passam a morar juntos, dividem a cama e compartilham o inventário da casa. E aí naturalmente veio a ideia de fazer o sistema de família e gravidez.

Quando você instala o mod, você escolhe o sexo do seu personagem para essa parte funcionar. A gestação dura uns cinco dias do jogo e afeta a gameplay de verdade: a mãe perde velocidade, a energia cai muito mais rápido e a stamina trava para atividades pesadas. Então nada de sair correndo, minerar ou lutar enquanto tiver grávida.

Na hora do parto, o personagem perde metade da vida pelo esforço, então tem que descansar. O bebê nasce como um item que você carrega no colo, e a partir daí começa o sistema de co-parentalidade: os pais dividem turnos para segurar a criança. Se o bebê começa a chorar no berço, quem estiver no turno tem que largar o que tiver fazendo e ir lá cuidar dele.

O filho herda traços genéticos dos pais, tipo cor de cabelo e olhos, e tem um sistema que mistura os nomes do casal pra gerar o sobrenome da criança.

E aqui aconteceu uma coisa muito engraçada nos testes: o bebê ficava no inventário como qualquer outro item. Isso quer dizer que você podia abrir o menu de presentes de um vizinho e dar o seu próprio filho recém-nascido de presente pra ele. Eu tive que ir lá e programar uma trava no código pra impedir isso.

---

### [12:30] CRESCIMENTO, ESCALAS E A PILHA DE FILHOS

[CENA: Comparação em fila das fases de idade: bebê recém-nascido minúsculo, criança em tamanho infantil, adolescente e adulto. Em seguida, o jogador caminhando enquanto empilha crianças na cabeça.]

**Locução:**
Com o passar dos dias, o bebê cresce pelas fases: Bebê, Criança, Adolescente e Adulto. Quando chega na maioridade, ele arruma um emprego e vai atrás da própria casa.

Pra fazer isso sem pesar o jogo, eu não podia criar dezenas de modelos 3D diferentes pra cada idade e refazer todas as roupas e armaduras do Hytale pra cada tamanho. Ia ser completamente inviável. Então eu criei um único modelo de criança feito de um jeito que dá pra reaproveitar os mesmos assets de roupa e itens do jogo original. Isso quer dizer que futuramente se o pessoal do hytale enfiar mais assets no jogo, eles já ficam adaptados para os os NPCs crianças

Quando a criança nasce, esse modelo é renderizado numa escala bem pequena. Conforme ela vai crescendo, a escala vai aumentando aos poucos. Na adolescência, o mod troca pro modelo padrão do Hytale só que em tamanho menor, e quando vira adulto chega nos 100%.

E tem uma zoeira que eu deixei no mod de propósito: o sistema de carregar crianças permite empilhar seus filhos uns em cima dos outros na sua cabeça. Você sai andando pela vila com uma torre de crianças na cabeça como se nada tivesse acontecendo.

---

### [14:30] UMA VILA AUTÔNOMA: TRABALHOS, EXPEDIÇÕES E O BUG DAS PORTAS

[CENA: Fazendeiro plantando sementes na horta; pescador recolhendo peixes no rio; caçador e minerador partindo para expedição e voltando com mochilas cheias.]

**Locução:**
Os moradores também precisam trabalhar. Pra definir a profissão de cada um, você entrega o item correspondente pro NPC — uma picareta faz dele um Minerador, uma enxada faz Fazendeiro, arco faz Caçador, e assim por diante.

O Fazendeiro vai sozinho até o baú da casa pegar sementes, planta na horta, colhe quando tá maduro e guarda a comida pra todo mundo. O Pescador fica cuidando das armadilhas na beira do rio.

Agora, pro Minerador e pro Caçador eu não podia simplesmente soltar a IA deles pelo mapa. Ia travar o servidor calculando rotas e o caçador provavelmente ia sair matando os animais de estimação da sua própria fazenda. Então eu criei o sistema de Expedições: eles saem do mapa por um tempo, como se tivessem ido explorar cavernas ou florestas, e voltam depois trazendo minérios, couro e carnes pro depósito da vila.

E lembra do Leash Point que eu falei lá no começo? Pois é. Antes dele, a gente tinha um bug muito chato: qualquer morador que passasse na calçada saía abrindo as portas de todas as casas da rua, sem o menor motivo.

A solução foi cruzar o Leash Point com a direção do olhar do NPC: ele só abre a porta se o ponto de ancoragem dele estiver dentro daquele cômodo e se ele estiver olhando pra porta. Resolveu na hora.

---

### [16:00] O SISTEMA DE REGISTRO: UMA SOLUÇÃO PRA TUDO

[CENA: Jogador colocando cadeiras, banheiras e baús em diferentes casas. Interface de debug mostrando os registros.]

**Locução:**
Lembra que eu falei que o mod escuta o momento exato em que o jogador coloca uma cama no chão pra cadastrar ela? Pois é, essa ideia funcionou tão bem que eu acabei replicando ela pra praticamente todos os móveis do jogo. Cadeiras, banheiras, baús, postes de pesca, fazendas — tudo usa o mesmo padrão de registro.

Quando você coloca uma cadeira no chão, o mod cadastra ela num ChairRegistry. O NPC depois vai lá, senta, e a cadeira fica marcada como ocupada até ele levantar. A mesma coisa com a banheira: ele toma banho, recupera higiene, e nenhum outro morador tenta entrar na mesma banheira ao mesmo tempo.

Se eu não tivesse feito assim, cada NPC ia precisar escanear centenas de blocos toda hora pra achar onde sentar ou onde tomar banho. Com esse sistema, a informação já tá pronta, catalogada e sem lag nenhum.

---

### [17:30] INTERAÇÕES SOCIAIS E BRINCADEIRAS DE CRIANÇA

[CENA: Dois NPCs adultos conversando entre si na praça da vila com balões de fala. Depois, corte para duas crianças correndo uma atrás da outra brincando de pega-pega.]

**Locução:**
Os moradores não interagem só com o jogador. Eles conversam entre si também. Quando dois NPCs ficam ociosos perto um do outro, eles começam a socializar: escolhem um assunto, viram um pro outro e trocam falas baseadas na personalidade e no humor de cada um.

Isso vale pra tudo. Se dois NPCs adultos que não são casados ficam conversando bastante, eles vão criando afinidade aos poucos. Se essa afinidade chegar num certo ponto, eles podem começar um romance, casar e ter filhos por conta própria, sem o jogador precisar fazer nada. A vila cresce sozinha.

E as crianças? As crianças brincam. Se duas crianças estão ociosas perto uma da outra, elas podem começar uma partida de pega-pega ou de esconde-esconde. Uma sai correndo, a outra vai atrás, com diálogos próprios durante a brincadeira.
---

### [19:00] ROUPAS FESTIVAS E GUARDAS

[CENA: NPCs vestidos com roupas de Natal andando pela vila com neve. Depois, corte para um NPC Guarda patrulhando e enfrentando um mob hostil.]

**Locução:**
Uma coisa que eu queria muito era que a vila reagisse às datas do ano. Então eu fiz um sistema de roupas festivas que funciona pelo calendário do jogo. Quando chega dezembro, os NPCs trocam de roupa sozinhos e vestem roupas de Natal. No final de outubro, eles vestem fantasias de Halloween. Quando a data passa, eles voltam pro visual normal automaticamente.

E cada NPC tem o modelo de fantasia gerado individualmente, então eles não ficam todos iguais. Cada morador mantém o rosto e os traços dele mesmo com a roupa festiva.

Também tem a profissão de Guarda. Você entrega uma arma pro NPC e ele vira um guarda da vila. Ele patrulha o perímetro e quando detecta um mob hostil por perto, ele parte pra cima e luta. E o mais legal é que o sistema identifica se a arma é corpo a corpo ou à distância, tipo arco ou besta, e muda o comportamento de combate de acordo — o guarda com espada vai pra cima, o com arco fica na distância.

---

### [20:30] ENCERRAMENTO E O FUTURO DO SIMTALE

[CENA: Pôr do sol na vila com as luzes das casas acendendo, fumaça saindo das chaminés e os moradores indo dormir em suas respectivas camas.]

**Locução:**
Um tempo atrás eu tava conversando com um amigo sobre tudo isso, e ele falou que o SimTale parecia uma mistura de MineColonies com Comes Alive do Minecraft. Eu fui pesquisar depois e realmente se parece bastante. O engraçado é que eu nem conhecia esse MineColonies antes, acabei chegando numa proposta parecida por conta própria.

O mod ainda tem muita coisa pra crescer. Eu quero trazer profissões mais fora da curva, tipo vendedores com economia própria, eventos de vilarejo mais elaborados e mecânicas de interação ainda mais profundas.

Se você curtiu e quer acompanhar o SimTale, deixa o like, se inscreve e comenta o que você faria primeiro com esse mod.

Valeu por assistir, e até o próximo!