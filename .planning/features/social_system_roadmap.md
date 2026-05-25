# 🗺️ Roadmap: Sistema Social SimTale (O Mundo Vivo)

Este documento é o guia definitivo para transformarmos os NPCs do SimTale de "botões de menu" em **personagens vivos**, reativos e memoráveis.

---

## 1. Núcleo Cognitivo e Sentimental
- [x] **Afinidade Base (-100 a 100):** Medidor de longo prazo que define o *Status* (`Odeia` até `Apaixonado`).
- [x] **Humor Temporário:** Estado emocional volátil (`Feliz`, `Bravo`, `Triste`, `Assustado`).
- [ ] **Sistema de Confiança:** Eixo separado da afinidade (Um NPC pode te amar, mas achar você um traidor).
- [x] **Memória Curta (Short-term Memory):** Registro dos últimos 5-10 eventos (ex: "Me bateu há 2 min", "Me deu pão hoje").

### 💡 Snippet: Memória Curta
```java
public class MemoryManager {
    public enum MemoryEvent { INSULTED, GIFTED, HELPED_IN_COMBAT, HIT_ME }
    
    public record Memory(MemoryEvent event, long timestamp, UUID playerSource) {}
    
    private final LinkedList<Memory> recentMemories = new LinkedList<>();

    public void addMemory(MemoryEvent event, UUID player) {
        if (recentMemories.size() >= 5) recentMemories.removeFirst();
        recentMemories.add(new Memory(event, System.currentTimeMillis(), player));
    }
    
    public boolean remembers(MemoryEvent event, long maxAgeMillis) {
        return recentMemories.stream()
            .anyMatch(m -> m.event() == event && (System.currentTimeMillis() - m.timestamp() < maxAgeMillis));
    }
}
```

---

## 2. Dinâmica de Interações
- [x] **Ações Básicas:** Papo, Piada, Paquera, Insulto, Presente.
- [ ] **Bate-Papo Contextual:** Sistema de lore, fofoca e observações do bioma ao redor.
- [x] **Piadas com Consequência:** Probabilidade de sucesso dependente do *Humor* atual.
- [x] **Flerte com Barreira:** Flerte falha miseravelmente e gera penalidade se feito no status `Estranho` ou com humor `Bravo`.
- [ ] **Presentes Favoritos/Odiados:** Dicionário de itens preferidos para cada NPC ou tipo de Personalidade.

### 💡 Snippet: Interação com Barreira e Humor
```java
public void attemptFlirt(SimNPCComponent npc, Player player) {
    if (npc.needs.isMiserable()) {
        DialogueSystem.say(npc, player, "Você acha que eu tô com cabeça pra isso agora?");
        npc.relationships.get(player.getUuid()).addAffinity(-10); // Punido por ser sem noção
        return;
    }
    
    if (npc.getRelationship(player.getUuid()).getStatus().ordinal() < RelationshipStatus.FRIEND.ordinal()) {
        DialogueSystem.say(npc, player, "Cara... quê? A gente mal se conhece.");
        return;
    }
    
    // Sucesso!
    DialogueSystem.say(npc, player, "Heh... continua falando.");
    npc.relationships.get(player.getUuid()).addAffinity(5);
}
```

---

## 3. O Mundo Vivo (Sistemas Emergentes)
- [x] **Traits de Personalidade Exclusivos:** `Tímido`, `Agressivo`, `Ganancioso`. (Ex: Gananciosos cobram mais caro se afinidade for baixa).
- [ ] **Sistema de Fofoca (Gossip):** Se o Player bater em um NPC, os NPCs próximos salvam essa memória e espalham pro resto da vila.
- [ ] **Reações ao Ambiente (Contexto Mágico):**
  - Jogador está com HP baixo? *"Meu deus, você está sangrando!"*
  - Está chovendo? *"Que clima horrível..."*
  - Item lendário na mão? *"Onde você conseguiu essa espada?!"*
- [ ] **Eventos Interrompidos:** Chance aleatória de iniciar um mini-evento no meio do papo (Ex: Monstro aparece e o NPC saca a arma).

### 💡 Snippet: Fofoca e Contexto Ambiente
```java
public String getGreetingDialogue(SimNPCComponent npc, Player player, World world) {
    // 1. Checa fofoca/memória de agressão
    if (npc.memory.remembers(MemoryEvent.SAW_PLAYER_ATTACK_VILLAGER, 3600000)) {
        return "Fica longe de mim! Eu sei o que você fez na vila hoje!";
    }
    
    // 2. Checa contexto do jogador
    if (player.getHealth() <= player.getMaxHealth() * 0.2f) {
        return "Você está péssimo! Precisa de bandagens?!";
    }
    
    // 3. Checa contexto do clima/mundo
    if (world.getWeather() == Weather.RAIN) {
        return "Não aguento mais essa chuva estragando minhas plantações...";
    }
    
    return "Olá, " + player.getName() + "!"; // Retorno padrão
}
```

---

## 4. Evolução, Rotina e Estética
- [ ] **Ciclo de Rotina IA:** (GOAP / Behavior Trees) NPC transita entre: Dormir -> Trabalhar (Minerar/Pescar) -> Sentar pra fofocar.
- [ ] **Evolução Gradativa:**
  - *Conhecido:* Diálogos curtos.
  - *Amigo:* Ajuda em combates próximos.
  - *Romance:* Dá presentes raros ao jogador do nada.
- [ ] **UI Reativa (Estética):**
  - Ícone pulsando quando "Apaixonado".
  - Borda tremendo e texto vermelho quando o NPC está "Bravo".
  - *Typewriter effect* (Letras aparecendo uma por uma no chat).

### 💡 Snippet: Modificando a UI pelo Humor
```java
// Dentro do NPCInteractionPage.java
public void build(Ref<EntityStore> playerRef, UICommandBuilder cmd, UIEventBuilder evt, Store<EntityStore> store) {
    if (npc.getMood() == Mood.ANGRY) {
        cmd.set("#MainPanel.Background", "TexturePath: 'Textures/angry_panel.png'");
        cmd.set("#NpcName.Style.TextColor", "#FF0000"); // Nome vermelho
        cmd.set("#NpcName.Classes", "ShakeAnimation"); // Classe de CSS/UI do Hytale
    } else if (npc.getRelationship(player.getUuid()).getStatus() == RelationshipStatus.ROMANCE) {
        cmd.set("#PortraitFrame.Background", "TexturePath: 'Textures/heart_frame.png'");
    }
}
```
