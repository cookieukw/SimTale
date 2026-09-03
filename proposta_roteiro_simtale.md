# Roteiro de Vídeo / Devlog: SimTale (Transformando o Hytale num Simulador de Vida)

Este documento traz a estrutura de roteiro, texto de narração e guia de gravações para um vídeo no estilo **Documentário / Devlog**. O foco principal é mostrar o **conteúdo, as mecânicas e os sistemas de gameplay** do mod SimTale de forma envolvente, didática e divertida.

---

## 🎬 1. Estrutura Narrativa (Falas em Primeira Pessoa)

### **Ato 1: A Ideia — Dando Alma aos NPCs do Hytale (00:00 - 02:00)**
*   **O Gancho:** *"Os NPCs padrão do Hytale são legais para preencher o mapa, mas vamos ser sinceros: depois de alguns minutos, você percebe que eles são basicamente estátuas ambulantes. Eles não têm casa, não têm história, não têm sentimentos e não interagem com o mundo ao redor. E foi olhando para isso que eu me perguntei: e se a gente pudesse transformar o Hytale em um simulador de vida completo, no estilo The Sims?"*
*   **A Origem da Ideia:** *"Minha ideia inicial era super simples: eu só queria portar um addon de NPC que eu já tinha desenvolvido no Minecraft Bedrock e adaptá-lo para o Hytale. Mas quando comecei a fuçar nas ferramentas e na API do jogo, a ficha caiu. Eu percebi o potencial absurdo que a gente tinha nas mãos e vi que dava para ir MUITO além de caixas de diálogo simples. Foi aí que decidi criar o **SimTale** do zero."*
*   **O Conceito SimTale:** *"A proposta do mod é simples de entender, mas gigante de executar: dar alma, personalidade, rotina e laços familiares para os habitantes do mundo. Cada NPC que você encontra passa a ter necessidades fisiológicas, preferências de vida, empregos, relacionamentos e até a capacidade de formar uma família e ter filhos."*
*   **A Filosofia de Design:** *"Eu não queria apenas criar botões na tela. Eu queria que os NPCs realmente vivessem no mundo: que tivessem suas próprias casas, que sentissem fome, que dormissem à noite, que tivessem hobbies e que reagissem de formas completamente diferentes dependendo de quem eles são."*

---

### **Ato 2: A Psicologia — Necessidades, Traits e o "Leash Point" (02:00 - 05:30)**
*   **As Necessidades (Needs):** *"O coração da inteligência dos NPCs gira em torno de cinco necessidades fundamentais: **Fome, Energia, Social, Diversão e Higiene**. Se um NPC passa muito tempo sem comer ou sem tomar banho, a barra dele cai e ele fica visivelmente de mau humor — mudando a forma como responde ao jogador."*
*   **Personalidade & Traços (Traits):** *"Mas o que torna cada cidadão único é o sistema de **Personalidade e Traços**. Um NPC pode ter traços como *Amigável, Agressivo, Preguiçoso, Carente, Ganancioso ou Paranóico*. Se você tentar contar uma piada para um NPC faminto que é 'Agressivo', ele provavelmente vai te dar um coice. Já um NPC 'Carente' vai adorar qualquer atenção que você der a ele."*
*   **Preferências e Gostos:** *"Além da personalidade, cada morador tem seus próprios **Gostos Pessoais**. Eles têm comidas favoritas e odiadas, itens que amam receber de presente, uma estação do ano preferida e até hobbies específicos — como pescar na beira do rio, cultivar plantas ou explorar cavernas."*
*   **Interações e Afinidade:** *"Ao abrir o menu de interação, você pode conversar, contar piadas, flertar ou presentear. Se você der de presente a comida favorita de um NPC quando ele estiver morrendo de fome, o nível de afinidade e confiança de vocês vai dar um salto gigante!"*
*   **A Variedade de Diálogos:** *"E para deixar tudo isso ainda mais dinâmico, eu programei uma variedade gigantesca de diálogos. No total, são mais de 800 falas diferentes no código. São 200 variantes para homens, 200 para mulheres e 400 exclusivas para as crianças, divididas entre meninos e meninas. Cada um tem diálogos, reações e frases completamente únicas que mudam de acordo com a personalidade, os gostos do NPC e até mesmo alguma ocasião especial no jogo."*
*   **O Conceito do Leash Point (Guarde essa informação!):** *"Para fazer a movimentação funcionar sem bug, eu criei uma âncora chamada **Leash Point** — um ponto de referência dinâmico que o mod passa para a física do Hytale controlar o caminho do NPC. Guarde bem essa informação, porque esse Leash Point vai resolver um problema inacreditável mais para frente!"*

---

### **Ato 3: Arquitetura & Lar — O Algoritmo de Casas e Baús Inteligentes (05:30 - 09:00)**
*   **A Inspiração no Terraria:** *"Um dos sistemas mais legais do SimTale é como os NPCs reconhecem o que é uma casa. Não adianta só colocar um NPC no meio do nada; ele precisa de um lar de verdade. Eu desenvolvi esse sistema fortemente inspirado no **Terraria** e na forma como os habitantes de lá exigem moradias adequadas."*
*   **O Desafio da CPU (Chunk Scan vs Eventos):** *"Mas como o jogo sabe onde estão as camas no mundo? Se cada NPC ficasse escaneando os chunks o tempo todo, com 100 NPCs o servidor teria que fazer milhões de checagens por segundo e iria derreter a CPU. A solução foi inteligente: o mod escuta o evento exato em que o jogador coloca um bloco de cama no chão e cadastra ela instantaneamente no nosso registro global (`BedRegistry`) com custo zero de lag!"*
*   **O Algoritmo de Validação da Casa:** *"A partir da cama cadastrada, o sistema faz uma validação em 2 etapas. Primeiro, um curto-circuito rápido: se não houver paredes ao redor, ele cancela na hora. Se houver paredes, ele verifica se o cômodo está 100% vedado e sem buracos no teto. Por fim, ele checa se o local tem o kit básico de uma moradia: **uma cama, uma mesa, uma cadeira, iluminação e um baú**. Se faltar algo ou se o espaço for apertado demais, o NPC ignora a casa."*
*   **Inventário da Casa & Baús Inteligentes:** *"Os baús da residência funcionam como o inventário global da família. O NPC sabe exatamente onde fica cada baú da sua casa e o que tem dentro. Se ele sente fome, vai direto no seu baú pegar um lanche; se coleta materiais no trabalho, guarda tudo na despensa. E o melhor: o código é inteligente para que os moradores **nunca confundam com os baús dos vizinhos e jamais tentem saquear baús de masmorras ou do mapa aberto!**"*

---

### **Ato 4: Amor e Família — Gravidez, Genética e o Cuidado com os Bebês (09:00 - 13:00)**
*   **Relacionamentos e Casamento:** *"Conforme a afinidade aumenta, você pode entregar o **Anel de Casamento** para se casar com um NPC. Ao se casarem, os NPCs passam a dividir a mesma casa, a mesma cama e compartilham o inventário entre si. Se você for casado com uma NPC, ela se muda para a sua casa e fica com você — e vice-versa!"*
*   **O Sistema de Gravidez Realista:** *"Depois do casamento vem o sistema de **Gravidez**. Ao instalar o mod pela primeira vez, você escolhe um sexo para a mecânica de reprodução. Homens engravidam NPCs femininas, e mulheres engravidarem do marido NPC. A gestação dura cerca de 5 dias do jogo e traz alterações de gameplay reais: a mãe perde velocidade de caminhada, a stamina não recarrega para atividades físicas pesadas (como correr, minerar ou lutar) e a energia cai rapidamente."*
*   **Parto e Co-Parentalidade:** *"No momento do parto, o personagem perde cerca de metade da vida pelo esforço, exigindo cuidados reforçados. O bebê nasce como um item de colo e ativa o sistema de **Co-Parentalidade**: os pais dividem turnos trocando o bebê de colo a cada poucas horas reais. Se a criança chora no berço, quem estiver no turno para o que está fazendo para ir amamentar, dar carinho ou colocar pra dormir."*
*   **Genética & A Trava Anti-Troll:** *"A criança herda características físicas dos pais por probabilidade genética (duas pessoas de cabelo castanho têm chance maior de filho castanho, mas com pequena margem para loiro ou preto). Nós temos um sistema exlusivamente para nomes, que pega o nome do player e do NPC para fazer uma mistura e dar de sobrenome para o filho. E um detalhe divertido de bastidores: eu tive que programar uma **trava de segurança** no sistema de presentes, porque nos primeiros testes os jogadores podiam oferecer o próprio bebê recém-nascido como presente para outros NPCs no menu de troca!"*
*   **Crescimento em Fases:** *"Com o tempo, o bebê evolui pelas fases de desenvolvimento: **Bebê → Criança → Adolescente → Adulto**, aprendendo tarefas de casa e buscando sua própria vida ao atingir a maioridade."*
*   **O Truque de Escala nas Crianças:** *"Sobre esse sistema de crescimento, ele funciona de um jeito bem inteligente. Eu criei um único modelo 3D exclusivo para os NPCs crianças, feito de uma forma tão boa que nos permite reaproveitar todos os assets de roupas e itens do jogo sem precisar modelar nada do zero. Quando o bebê nasce, esse modelo de criança é renderizado em uma escala bem menor. Conforme ele cresce, a escala vai aumentando gradualmente. A partir da adolescência, o modelo muda para o do jogador comum, só que ainda em escala menor, até que na fase adulta assume a escala normal de 100%."*

---

### **Ato 5: Uma Vila Viva — Profissões, Expedições e a Diversão com Crianças (13:00 - End)**
*   **Profissões e Estações de Trabalho:** *"Você pode atribuir profissões posicionando blocos de trabalho específicos. O **Pescador** trabalha perto de armadilhas de peixe no rio; o **Fazendeiro** pega sementes no baú da casa, semeia a horta e colhe automaticamente toda a produção para a comunidade."*
*   **Expedições (Mineradores e Caçadores):** *"Já para o **Minerador** e o **Caçador**, fazer a IA caminhar pelo mundo aberto coletando recursos geraria dois problemas: lag de escaneamento e o risco do caçador matar os animais de estimação da sua fazenda! A solução foi criar o sistema de **Expedição**: eles simplesmente se ausentam do mapa por um tempo (como se tivessem saído para explorar a selva ou cavernas profundas) e retornam mais tarde trazendo os minérios e carnes para o estoque da vila."*
*   **O Bug das Portas e a Solução do Leash Point:** *"E lembram do **Leash Point** que comentei lá no começo? Ele resolveu um dos bugs mais esquisitos que teve: antes, quando um NPC passava perto da casa do vizinho, ele saía abrindo todas as portas da rua sem a menor intenção de entrar! Agora, nós verificamos a posição do Leash Point e a direção do olhar do NPC: ele só abre a porta se o seu ponto de referência realmente estiver dentro daquela casa e se ele estiver olhando diretamente para a entrada!"*
*   **Empilhamento de Crianças e Profissões Futuras:** *"E o mod tem vários outros pequenos detalhes divertidos que você vai percebendo enquanto joga, como o sistema de carregar crianças, que basicamente permite que você empilhe os seus filhos uns em cima dos outros na sua cabeça e saia andando pela vila como se nada estivesse acontecendo. Atualmente não temos nenhuma profissão muito fora da curva, tipo um vendedor exclusivo ou um caçador de dragões, mas isso é algo que pretendo adicionar nas próximas versões."*
*   **Comparações e Inspirações:** *"Inclusive, eu andei conversando sobre essas mecânicas com um amigo e ele disse que a ideia parecia muito com o MineColonies misturado com o Comes Alive do Minecraft. Eu pesquisei depois e realmente se parece muito! Eu não conhecia esses mods antes, mas é engraçado ver como acabei recriando a mesma essência de forma independente para o Hytale. Futuramente, quero colocar sistemas ainda mais elaborados para tornar o SimTale a experiência definitiva de simulação de vida."*



---

## 📹 2. Guia de Gravação (Lista de Takes / B-Roll)

| # | Cena / Mecânica | O que mostrar na tela | Dica de Produção |
| :--- | :--- | :--- | :--- |
| **01** | **Intro / Hook** | Time-lapse do jogo em velocidade alta com vilarejo movimentado e NPCs andando. | Use câmera livre em modo espectador com tom cinematográfico. |
| **02** | **Origem do Mod** | Gravação rápida do addon antigo no Bedrock vs o visual fluido do SimTale no Hytale. | Mostre o contraste de evolução do projeto. |
| **03** | **Menu de Interação & Needs** | Clicando no NPC e mostrando o painel de Fome, Energia, Social, Diversão e Higiene. | Zoom nos ícones das necessidades e no indicador de humor. |
| **04** | **Traços de Personalidade & Gostos** | NPC 'Agressivo' rejeitando uma piada vs entrega de comida favorita para NPC faminto. | Grave a reação imediata e o salto na barra de afinidade. |
| **05** | **Leash Point em Ação** | Tela de debug com as coordenadas/marcador do Leash Point mostrando a IA navegando. | Use `/simtale debugbeds` ou marcador visual no mapa. |
| **06** | **Construção de Casa (Terraria Style)** | Jogador construindo um cômodo com paredes, teto, porta, cama, mesa, cadeira e iluminação. | Time-lapse rápido da construção sendo montada e validada. |
| **07** | **Colocação da Cama (BedRegistry)** | Jogador colocando uma cama no chão e o NPC sem-teto reivindicando ela instantaneamente. | Registre o log/notificação e o NPC caminhando até a cama ao anoitecer. |
| **08** | **Baú da Família / Despensa** | NPC com fome caminhando autonomamente até o baú da sua casa para pegar comida. | Mostre o NPC abrindo o baú certo (e ignorando baús de vizinhos ou masmorras). |
| **09** | **Proposta de Casamento** | Entregando o *Wedding Ring* para o parceiro romântico e a NPC se mudando para a sua casa. | Registre a mensagem de aceitação e a mudança de residência no painel. |
| **10** | **Gravidez & Nerf de Stamina** | Jogadora/NPC grávida tentando correr ou minerar com a barra de stamina travada e velocidade reduzida. | Destaque no HUD o indicador de gestação e a lentidão de passos. |
| **11** | **Parto & Perda de Vida** | O momento da entrega do bebê com a perda de metade da vida e o bebê surgindo como item de colo. | Mostre o ícone do bebê no inventário/colo e o status de saúde. |
| **12** | **Troca de Bebê (Co-Parentalidade)** | O casal trocando o bebê de colo entre si após algumas horas do jogo. | Mostre o NPC vindo pegar o bebê do colo do jogador/parceiro. |
| **13** | **Genética & Mistura de Nomes** | Comparativo de pais e filhos mostrando a cor dos cabelos/olhos e o sobrenome fundido na UI. | Monte um frame lado a lado mostrando o nome da família e o visual herdado. |
| **14** | **Trava Anti-Troll (Bebê Presente)** | Jogador tentando selecionar o bebê no menu de presentes e a trava de segurança bloqueando. | Grave a tentativa de entrega com a mensagem cômica de bloqueio na tela. |
| **15** | **Evolução da Criança (Escalas)** | Lado a lado de recém-nascido (mini), criança (escala média), adolescente e adulto. | Posicione os 4 estágios de crescimento em fileira para mostrar a evolução de tamanho. |
| **16** | **Empilhando Crianças na Cabeça** | Jogador carregando 2 ou 3 crianças empilhadas na cabeça enquanto caminha pela vila. | Take cômico e descontraído mostrando a pilha de crianças andando no mapa. |
| **17** | **Fazendeiro & Pescador** | Fazendeiro pegando sementes no baú e semeando a horta + Pescador na armadilha do rio. | Grave a automação da colheita e do depósito dos vegetais no baú. |
| **18** | **Expedição (Minerador & Caçador)** | Minerador e caçador se ausentando da vila em expedição e retornando com minérios e carnes. | Mostre o NPC saindo do mapa e reaparecendo com os baús cheios de recursos. |
| **19** | **Inteligência de Portas** | NPC caminhando na rua sem abrir portas dos vizinhos, abrindo somente a porta da sua própria casa. | Grave o NPC passando reto pelas casas da rua e entrando direto no seu lar. |
| **20** | **Variedade de Diálogos (+800 Variantes)** | Conversando com crianças, homens e mulheres em diferentes humores e horários. | Mostre as caixas de texto com diálogos variados e reações engraçadas. |

---