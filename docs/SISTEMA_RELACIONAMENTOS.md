# Sistema de Relacionamentos - SimTale

Este documento detalha a arquitetura completa do sistema de relacionamentos pretendido para o SimTale, separando as mecânicas para criar interações sociais profundas e dinâmicas com os NPCs.

## Visão Geral da Arquitetura

O sistema gira em torno de uma entidade central `Relationship`, que agrega todas as variáveis sociais entre o NPC e o Jogador, ou entre dois NPCs entre si — incluindo cortejo, casamento e filhos totalmente autônomos, sem nenhum jogador envolvido (ver seção "Cortejo e Casamento Autônomo entre NPCs" abaixo).

```
Relationship
├── Affinity (Geral)
├── Friendship (Amizade)
├── Trust (Confiança)
├── Romance (Romântico)
├── Mood (Humor Diário)
├── Personality & Traits (Personalidade)
├── Preferences (Gostos)
├── Memories (Memórias e Eventos)
├── Needs (Necessidades)
├── Family (Família e Casamento)
├── DailyInteractions (Controle de Spam)
└── RelationshipStage (Níveis de Relacionamento)
```

---

## Status de Implementação

| Componente | Status | Descrição |
| :--- | :--- | :--- |
| **Relationship (Base)** | 🟢 Já Adicionado | A classe base foi refatorada e suporta as novas divisões de afinidade. |
| **Friendship** | 🟢 Já Adicionado | Totalmente desvinculado de Romance e pareado com afinidade. |
| **Trust** | 🟢 Já Adicionado | Variável independente que pode cair bruscamente com insultos. |
| **Romance** | 🟢 Já Adicionado | Totalmente desacoplado, exigindo flertes explícitos. |
| **Mood** | 🟡 Precisa Melhorar | Temos um enum básico de `Mood`, mas não afeta dinamicamente 100% das respostas ainda. |
| **Personality / Traits** | 🟡 Precisa Melhorar | Sistema de Traits existe (ex: GREEDY, SHY), mas as ramificações são limitadas. |
| **Preferences (Gostos)** | 🟢 Já Adicionado | Sistema de Gostos aleatórios criado com comidas, clima e hobbies (`NPCPreferences`). |
| **Memories (Memórias)**| 🟢 Já Adicionado | NPCs reagem quando o jogador some por mais de 3 dias no jogo. |
| **Needs (Necessidades)**| 🟢 Já Adicionado | IA autônoma implementada: NPCs andam até comida, camas e água para resolver suas necessidades. |
| **Family (Casamento/Filhos)**| 🟢 Já Adicionado | Casamento (`MARRIED`), gestação, nascimento, co-parentalidade, estágios de crescimento, vínculos (`FamilyBonds`, `ParentChildBond`), co-sleeping e status filial personalizado ("Filho"/"Filha"). Casamento e reprodução agora também acontecem entre duas NPCs sozinhas, sem jogador (`NPCSocialHelper.tryCourtship`), com desejo de filhos individual por NPC (`FamilySystem.desiredChildren`). |
| **DailyInteractions** | 🟢 Já Adicionado | Cooldown implementado que zera ganhos após 3 interações no mesmo dia. |
| **RelationshipStage** | 🟢 Já Adicionado | Novos status adicionados (`DATING`, `ACQUAINTANCE`, `ENEMIES`, etc). |

---

## Detalhamento das Mecânicas e Mock-ups de Implementação

### 1. Níveis de Amizade, Confiança e Romance (Separados)
Um NPC pode te adorar (Amizade 900) mas não te contar um segredo (Confiança 200). Da mesma forma, dar presentes normais não deve gerar romance se o jogador não usar interações de flerte (Romance).

**Exemplo de Implementação (Mock):**
```java
public class Relationship {
    public int affinity; // O número cru geral (-100 a 1000)
    
    // Sub-categorias independentes
    public int friendship; 
    public int trust;
    public int romance;
    
    public RelationshipStatus stage = RelationshipStatus.UNKNOWN; // Desconhecido
    
    public void interact(InteractionType type, NPC npc) {
        // Exemplo: Piada aumenta amizade, mas não romance. Segredo aumenta confiança.
        if (type == InteractionType.SECRET) {
            this.trust += 15;
        } else if (type == InteractionType.FLIRT) {
            this.romance += 10;
        }
    }
}
```

### 2. Mood (Humor)
Humor diário e temporário do NPC. Mesmo um "Melhor Amigo" responderá de forma curta ou irritada se estiver `Cansado` ou `Triste`.

**Exemplo de Implementação:**
```java
public enum MoodState {
    HAPPY, SAD, ANGRY, TIRED, ENERGETIC, SICK
}

public class MoodManager {
    public MoodState currentMood;
    
    public String modifyDialog(String baseDialog) {
        if (currentMood == MoodState.SAD) {
            return "... " + baseDialog.toLowerCase() + " ...";
        }
        return baseDialog;
    }
}
```

### 3. Preferences (Sistema de Gostos)
Variáveis para definir o que o NPC gosta e odeia. Isso afeta o ganho de afinidade ao receber presentes ou interagir em certos locais.

**Exemplo de Implementação (Mock):**
```java
public class NPCPreferences {
    public List<String> favoriteFoods = List.of("Torta de Maçã", "Pão Doce");
    public List<String> hatedFoods = List.of("Peixe Cru", "Sopa de Pedra");
    public String favoriteSeason = "Outono";
    public String favoriteWeather = "Chuva";
    public String hobby = "Pescaria";
    
    public int calculateGiftAffinity(Item gift) {
        if (favoriteFoods.contains(gift.getName())) return +50;
        if (hatedFoods.contains(gift.getName())) return -30;
        return +5; // Presente normal
    }
}
```

### 4. Memória (Tempo e Eventos Especiais)
NPCs lembram quanto tempo você ficou fora e de eventos grandes (Aniversários, Casamentos, Mortes).

**Exemplo de Implementação:**
```java
public class MemoryManager {
    public long lastInteractionTime;
    public List<LifeEvent> permanentMemories = new ArrayList<>();
    
    public String checkTimeAwayGreeting() {
        long daysAway = (System.currentTimeMillis() - lastInteractionTime) / (1000*60*60*24);
        if (daysAway > 30) {
            return "Nossa... faz semanas que não te vejo.";
        } else if (daysAway <= 1) {
            return "Bom te ver de novo!";
        }
        return "Oi!";
    }
    
    public void recordEvent(EventType type, String description) {
        permanentMemories.add(new LifeEvent(type, description));
    }
}
```

### 5. Rotina Dinâmica baseada no Relacionamento
O comportamento passivo (IA) do NPC muda conforme o status do relacionamento.

**Exemplo (Mock de IA):**
```java
public class NPCRoutineAI {
    public void evaluateDailyRoutine(Relationship rel) {
        if (rel.stage == RelationshipStatus.STRANGER) {
            // Apenas acena de longe
            playAnimation("WAVE");
        } else if (rel.stage == RelationshipStatus.BEST_FRIEND) {
            // Vai até a porta da casa do jogador visitar
            pathfindTo(Player.getHomeLocation());
        } else if (rel.stage == RelationshipStatus.MARRIED) {
            // Fica em casa limpando ou cozinhando
            performTask(Chores.CLEANING);
        }
    }
}
```

### 6. Sistema de Cooldowns (DailyInteractions)
Evitar que o jogador "farme" amizade clicando em "Conversar" 100 vezes por dia.

**Exemplo:**
```java
public class InteractionLimiter {
    public int interactionsToday = 0;
    public final int MAX_INTERACTIONS = 3;
    
    public boolean canInteract() {
        return interactionsToday < MAX_INTERACTIONS;
    }
    
    public void resetDaily() {
        interactionsToday = 0;
    }
}
```

---

## Arquitetura de Sistemas Avançados Implementados

### Família, Filhos e Vínculos de Sangue
O sistema de família e hereditariedade opera integrando `FamilyData`, `GrowthComponent` e os helpers `FamilyBonds` e `ParentChildBond`:

*   **Vínculos Automáticos (`FamilyBonds`)**: Ao nascer ou ser gerada, a criança recebe afeto e confiança basais elevados com os genitores (status `BEST_FRIEND` na máquina de estados interna), herança de sobrenome dinâmico e vinculação imediata à cama dos pais (`FamilyBonds.findParentBed`).
*   **Reconhecimento Parental (`ParentChildBond`)**: Verifica se o NPC alvo é filho do jogador interagindo. Quando verdadeiro:
    *   O painel social troca os tiers adultos genéricos por **Filho** (`Son`), **Filha** (`Daughter`) ou **Filho(a)** (`Child`), com indicadores de vínculo afetivo `(Vínculo: X%, Afinidade: Y%)`.
    *   A ação hostil *Insultar* é substituída por **Brigar** (*Scold*), com reações proporcionais ao estágio de idade.
    *   Ações inadequadas para menores (Flertar e Dar Emprego) são bloqueadas ou ocultadas.
*   **Co-Sleeping Familiar**: Membros da família podem dividir a mesma cama física sem conflito de desduplicação (`isBedTakenByAnotherNpc`). Se a montagem nativa estiver ocupada pelo pai ou mãe, a criança deita junto na cama com animação `Sleep` sem perder a referência de seu lar.
*   **Imunidade a Trabalho Infantil**: Crianças nascem como `UNEMPLOYED` e não podem ser contratadas para trabalhos pesados/perigosos (`WorkEligibility`). Participam da vida doméstica e da vila autonomamente através de seus hobbies (`GARDENING`, `FISHING`). Ao atingirem o estágio `TEEN`, a profissão adulta é sorteada.
*   **Termo de Endereçamento Dinâmico (`ParentChildBond.parentTermKey`)**: A palavra que um filho usa para se referir a um pai/mãe é recalculada a cada chamada, lendo o `Relationship.affinity` atual entre os dois — nunca decidida uma vez e memorizada. Com `affinity >= 40` ("vínculo caloroso") o filho usa `terms.mom_warm`/`terms.dad_warm` (mamãe/papai); abaixo disso mas ainda `>= 0`, usa `terms.mom_plain`/`terms.dad_plain` (mãe/pai); abaixo de `0` (`NAME_ONLY_THRESHOLD`) o método retorna `null` e quem chamou deve cair para o nome próprio do pai/mãe. `findChildOf` decide se o alvo é mãe ou pai comparando `playerUuid` com `motherId`/`fatherId` do `GrowthComponent`. Hoje só duas falas usam isso: `young.chat.own_child.1` e `young.joke.own_child.1` (lacuna de conteúdo, não de mecanismo — as vozes teen/adulta ainda não têm nenhuma fala com `{parent}`).

### Cortejo e Casamento Autônomo entre NPCs (`NPCSocialHelper.tryCourtship`)

Toda vez que duas NPCs adultas terminam uma conversa espontânea agradável (`applyChatOutcome` →
`bondNpcs` retornou `pleasant == true`), `tryCourtship` roda em cima do ganho de amizade/afinidade já
aplicado:

1.  **Elegibilidade**: descarta se qualquer um dos dois é menor (`InteractionManager.isNpcAChild`,
    que cobre criança e adolescente), se são parentes próximos (`FamilyBonds.areCloseFamily`), ou se
    um dos dois já é casado com uma terceira pessoa (`family.isMarried` e o `spouseId` não bate com o
    outro — não modela caso extraconjugal).
2.  **Ganho de romance**: soma `NPC_ROMANCE_GAIN = 2` ao `romance` de cada `Relationship` (a visão do
    host sobre o guest e vice-versa), mesmo que já estejam casados um com o outro (reforça o valor que
    o rolamento diário de gravidez natural em `SimTaleTickSystem` lê).
3.  **Casamento**: se `hostView.romance >= 80 && guestView.romance >= 80 && hostView.friendship >= 70
    && guestView.friendship >= 70` (`NPC_MARRIAGE_ROMANCE_THRESHOLD` / `NPC_MARRIAGE_FRIENDSHIP_THRESHOLD`
    — exatamente a mesma barra que o pedido de casamento com aliança do jogador usa), os dois chamam
    `family.marry(...)` um no outro, o `status` das duas `Relationship` vira `MARRIED`, ambos são
    persistidos (`SimNPCPersistence.saveNPC`) e uma linha é anunciada a jogadores por perto
    (`npc-dialogues.npc_marriage.announce`).

`FamilyBonds.areCloseFamily(a, b)`: retorna `true` para a mesma NPC, para relação pai/filho em
qualquer direção (`isChildOf`), ou para irmãos que compartilham qualquer genitor (mãe ou pai, cheio
ou meio-irmão), comparando os `GrowthComponent` de nascimento de cada um. Existe porque
`FamilyBonds.linkToFamily` já deixa pais e irmãos com amizade/afinidade altíssimas (para simular o
vínculo familiar), exatamente a forma que `tryCourtship` leria como "ótimo par" se não fosse
explicitamente barrada.

`FamilySystem.desiredChildren` / `wantsAnotherChild()`: cada NPC guarda seu próprio número de "quantos
filhos eu quero ter, no total", sorteado uniformemente entre `0` e `4` na primeira vez que é lido
(`ThreadLocalRandom.current().nextInt(0, 5)`) e persistido depois disso — não é um valor de casal, é
por indivíduo, então dois cônjuges podem discordar. `wantsAnotherChild()` retorna
`children.size() < desiredChildren`. Esse gate só entra no rolamento diário de gravidez **natural**
para casais NPC-NPC, em `SimTaleTickSystem` (`bothWantIt = spouseNpc == null ||
(npc.family.wantsAnotherChild() && spouseNpc.family.wantsAnotherChild())`) — quando o cônjuge é um
jogador (`spouseNpc == null`, sem `FamilySystem` para guardar a preferência), o gate não se aplica e o
comportamento antigo (romance + chance) continua idêntico.

### Necessidades (Needs) e Alimentação
Esses status servirão de base para a IA decidir comer, dormir ou tomar banho.
```java
public class NeedsSystem {
    public float hunger = 100f;
    public float sleep = 100f;
    public float fun = 100f;
    public float social = 100f;
    public float hygiene = 100f;
    
    // MOCK: NPC cuidando de filhos ou se alimentando
    public void tickNeeds(FamilySystem family) {
        hunger -= 0.5f;
        if (hunger < 30) {
            // Trigger AI to find food
        }
        
        // MOCK: Cuidar dos bebês
        for(Child child : family.children) {
            if (child.ageStage == 0 && this.social > 50) {
                // AI vai cuidar do bebe
            }
        }
    }
}
```

---

## Conclusão e Próximos Passos
Esta arquitetura garante que o código suporte interações massivas e complexas sem se tornar um emaranhado de `if/else`. A prioridade de desenvolvimento futuro deve ser:
1. Implementar o sistema de `Preferences` (Gostos).
2. Separar as lógicas de ganho/perda de Amizade, Romance e Confiança.
3. Adicionar o `DailyInteractions` para evitar spam.
4. Expandir as ramificações do `Mood` nas respostas.
