# Sistema de Relacionamentos - SimTale

Este documento detalha a arquitetura completa do sistema de relacionamentos pretendido para o SimTale, separando as mecânicas para criar interações sociais profundas e dinâmicas com os NPCs.

## Visão Geral da Arquitetura

O sistema gira em torno de uma entidade central `Relationship`, que agrega todas as variáveis sociais entre o NPC e o Jogador (ou entre NPCs no futuro).

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
| **Relationship (Base)** | 🟡 Precisa Melhorar | Classe base existe, mas mistura conceitos e não possui todas as subdivisões. |
| **Friendship** | 🟡 Precisa Melhorar | Implementado de forma simples (-100 a 100), precisa parear com afinidade. |
| **Trust** | 🟡 Precisa Melhorar | Existe no código base, mas com baixo impacto nas interações atuais. |
| **Romance** | 🟡 Precisa Melhorar | Existe, mas precisa ser totalmente desacoplado da amizade padrão. |
| **Mood** | 🟡 Precisa Melhorar | Temos um enum básico de `Mood`, mas não afeta dinamicamente 100% das respostas ainda. |
| **Personality / Traits** | 🟡 Precisa Melhorar | Sistema de Traits existe (ex: GREEDY, SHY), mas as ramificações são limitadas. |
| **Preferences (Gostos)** | 🔴 Não Adicionado | Faltam comidas favoritas, horários, climas e hobbies. |
| **Memories (Memórias)**| 🟡 Precisa Melhorar | Memória de curto prazo existe (ex: lembra de insulto), mas falta decaimento por tempo longo e eventos de vida. |
| **Needs (Necessidades)**| 🟡 Precisa Melhorar | Sistema em construção (Fome, Social, etc existem no código, mas sem IA autônoma para resolver). |
| **Family (Casamento/Filhos)**| 🔴 Não Adicionado | Nada implementado além do status text. |
| **DailyInteractions** | 🔴 Não Adicionado | Sem cooldown robusto (é possível farmar interações). |
| **RelationshipStage** | 🟢 Já Adicionado | `RelationshipStatus` (Stranger, Friend, Best Friend, Partner, etc) já controla a progressão. |

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

## Mocks para Futuras Implementações Avançadas

Abaixo estão arquiteturas preparadas para os sistemas futuros complexos (Casamento, Filhos, Moradia e Necessidades):

### Família, Casamento e Moradia Compartilhada
```java
public class FamilySystem {
    public boolean isMarried;
    public UUID spouseId;
    public Location sharedHomeLocation;
    
    // MOCK: Ter filhos e crescer
    public List<Child> children = new ArrayList<>();
    
    public void attemptPregnancy() {
        // Mocked logic for future
    }
    
    public void onChildBirth() {
        // Mocked logic for future
    }
}

public class Child {
    public String name;
    public int ageStage; // 0=Baby, 1=Toddler, 2=Child, 3=Teen
    
    // MOCK: Crescer
    public void growUp() {
        ageStage++;
    }
}
```

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
