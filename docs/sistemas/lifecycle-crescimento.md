# Sistema de Ciclo de Vida e Crescimento (Pregnancy & Baby Care)

> **TL;DR**: Gerencia a gestação das NPCs/jogadoras, o nascimento físico do bebê como item carregável, e o crescimento em fases (Bebê -> Toddler -> Criança -> Adolescente -> Adulto). Inclui um sistema de co-parentalidade com turnos de cuidados automatizados de 2 minutos.

---

## 1. O que é e para que serve
O sistema de ciclo de vida controla a reprodução, hereditariedade e envelhecimento dos personagens no SimTale. Ele permite que o jogador (ou NPCs) engravide, simula sintomas físicos de gestação (lentidão e consumo de stamina), processa o parto, e introduz os cuidados do recém-nascido. O bebê nasce como um item no inventário e os pais devem revezar seus cuidados (alimentar, brincar, medicar) em turnos para garantir que ele cresça de forma saudável até atingir a vida adulta e se tornar independente.

---

## 2. Como funciona por dentro
O ciclo de vida é suportado por três gerenciadores principais no pacote `com.cookieukw.SimTale.core.lifecycle`:

```
PregnancyManager (Gestação de 5 dias reais de jogo)
   ↓ Parto: Gera item "simtale:Baby" no inventário
BabyCareManager (Turnos de cuidados de 2 minutos)
   ↓ Crescimento: Controla ticks de idade e progresso das fases
GrowthManager (Aplica escala visual de 0.4f a 1.0f baseada na idade)
```

### O Nascimento e o Item Bebê
*   A gravidez avança a cada tick do servidor (`PregnancyTickSystem`). Ao final do quinto dia, o parto ocorre.
*   O bebê é gerado como um `ItemStack` customizado (`simtale:Baby`) que carrega o ID único do filho (`childId`) em seus metadados.
*   **Transição de Custódia**: A custódia do bebê é vinculada ao inventário físico. Se o jogador tiver o item `simtale:Baby` na sua hotbar/mochila, o `BabyCareTickSystem` reconhece o jogador como o atual portador (`currentHolderId`). Se o item for transferido para o inventário do NPC cônjuge ou colocado em um contêiner, a custódia muda automaticamente.

### Co-parentalidade Ativa e Turnos
*   O turno de cuidados dura **2 minutos** (`TURN_DURATION` de 120.000 ms em desenvolvimento para testes rápidos).
*   Se o jogador passar do tempo de carregar o bebê ou ficar offline, o `MotherAIManager` faz com que o pai/mãe NPC tome a iniciativa: o NPC pega o bebê do inventário do jogador (ou do berço), simula as ações de cuidado offline (alimentar, curar, brincar) e envia mensagens localizadas ao jogador informando que cuidou do filho.

### Estágios de Crescimento e Escala Visual
O `GrowthManager` lê o estágio de crescimento (`GrowthStage`) e aplica modificadores de escala tridimensional na entidade física do NPC via pacotes de renderização do Hytale:
*   👶 **BABY** (Escala: `0.4f` - puramente item ou berço)
*   🧸 **TODDLER** (Escala: `0.5f` - anda no mundo, segue os pais)
*   👦 **CHILD** (Escala: `0.7f` - autônomo, brinca, realiza hobbies)
*   🧑 **TEEN** (Escala: `0.85f` - assume profissão adulta e tarefas)
*   👨 **ADULT** (Escala: `1.0f` - independente, busca própria moradia)

### Vínculo Familiar, Moradia e Co-Sleeping (`FamilyBonds`)
Ao nascer e ao transicionar entre estágios físicos, o `FamilyBonds.linkToFamily` assegura que a criança esteja plenamente integrada à família:
*   **Vínculo Afetivo Inicial**: Inicializa friendship, trust e affinity altos com os pais, evitando que os genitores sejam tratados como estranhos (`STRANGER`).
*   **Herança de Cama**: Localiza e associa a cama dos pais à criança (`FamilyBonds.findParentBed`). Crianças compartilham a mesma cama que os pais (`FamilyBonds.isChildOf`), e se o ponto de montagem nativo estiver ocupado, deitam sobre/ao lado da cama com animação `Sleep` sem perder o registro do lar.
*   **Proteção do Sono**: Durante o repouso noturno, o `GrowthTickSystem` não remove a criança da cama se o pai/mãe acordar ou se afastar.

### Trabalho, Hobbies e Profissões na Infância (`WorkEligibility`)
*   **Desemprego na Infância**: Todas as crianças nascem como `Profession.UNEMPLOYED`. A atribuição de trabalhos contratuais ou profissões perigosas (`GUARD`, `HUNTER`, `MINER`) é terminantemente bloqueada via UI e chat.
*   **Participação por Hobbies**: Crianças ajudam e convivem na vila através de seus hobbies autônomos (`NPCLeisureHelper`), como pescar (`FISHING`) ou cuidar da horta (`GARDENING`).
*   **Profissão na Adolescência**: Ao atingir o estágio `TEEN` no `GrowthManager`, o jovem desempregado recebe automaticamente uma profissão adulta sorteada para começar a trabalhar.

### Reconhecimento Parental na Interface (`ParentChildBond`)
Quando o jogador abre a tela de interação com seu próprio filho:
*   O status de relacionamento deixa de exibir níveis adultos genéricos e exibe **Filho** (`Son`), **Filha** (`Daughter`) ou **Filho(a)** (`Child`), com indicadores de vínculo familiar `(Vínculo: X%, Afinidade: Y%)`.
*   O botão de *Insultar* é substituído por **Brigar** (*Scold*), e ações inapropriadas (*Flertar*, *Dar Emprego*) são ocultadas/bloqueadas.

---

## 3. Decisões de Design e Por Quê
*   **Turnos Curtos para Gameplay/Testes**: A duração dos turnos (`TURN_DURATION`) e o tempo de gestação foram reduzidos nas configurações padrões.
    *   *Por quê*: Gestões de meses reais e turnos de dias inteiros tornariam o teste de regressão e validação de balanceamento impossíveis durante o desenvolvimento. O tempo acelerado permite validar toda a progressão do ciclo de vida em algumas horas de jogo.
*   **Genética de Nomes e Rostos**: O bebê herda uma combinação do sobrenome dos pais e combina traços de cor de pele/cabelo.
    *   *Por quê*: Aumenta o sentimento de imersão e hereditariedade na dinastia da família do jogador.

---

## 4. Nível de Complexidade e Robustez
*   **Nível**: 🔴 Complexo/frágil
*   **Risco**: O sistema envolve a transferência de dados complexos através do inventário (metadados de `ItemStack`). Se o jogador perder o item `simtale:Baby` (jogar na lava ou deletar via comando), o ID do filho é desconectado e o NPC correspondente no ECS pode parar de crescer ou ficar órfão no banco de dados.

---

## 5. Dificuldades Encontradas e Resolvidas

### Perda de custódia e bebês duplicados
*   **Problema**: Jogadores conseguiam duplicar o bebê jogando o item no chão ou colocando em baús, fazendo o sistema gerar múltiplos registros de cuidados.
*   **Correção**: O `BabyCareTickSystem` agora varre ativamente todos os inventários abertos e o mundo ao redor. Se o item bebê não for encontrado no inventário ativo do cuidador da vez ou se for duplicado, o sistema destrói as instâncias excedentes e vincula a custódia rigidamente ao detentor oficial registrado no `BabyCareData` do banco de dados Caskara.

---

## 6. Pontos em Aberto / Dívida Técnica
*   **Envelhecimento Natural**: Atualmente, as crianças crescem até a fase adulta, mas NPCs adultos não continuam envelhecendo até a velhice ou morte natural, exceto por fome crítica (morte por inanição gerenciada pela IA).

---

## 7. Perguntas Frequentes (FAQ)

### Posso perder o bebê se meu inventário estiver cheio durante o parto?
Não. O `PregnancyManager` tenta colocar o bebê diretamente na Hotbar do jogador. Caso não haja espaço livre, o item bebê é dropado como uma entidade flutuante no chão de forma segura aos pés da jogadora, enviando um alerta visual.

### O NPC cônjuge ajuda a cuidar do bebê sozinho?
Sim. Se a barra de necessidades do bebê (alimentação, afeição, saúde) cair enquanto o jogador está longe ou offline, a IA do cônjuge assume a custódia e executa simulações matemáticas que sobem os status do bebê, garantindo que ele não adoeça por negligência.
