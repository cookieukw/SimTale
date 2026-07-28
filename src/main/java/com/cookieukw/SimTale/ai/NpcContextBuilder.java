package com.cookieukw.SimTale.ai;

import com.cookieukw.SimTale.core.Child;
import com.cookieukw.SimTale.core.Memory;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.NPCPreferences;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.Trait;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NpcContextBuilder {

    public static AiRequest build(SimNPCComponent npc, UUID playerUuid, String playerName, List<AiMessage> conversationHistory) {
       
        // `UUID.fromString(playerUuid.toString())` was a no-op round-trip, and the null check
        // was an `assert`, which the JVM disables by default — so on a real server the NPE
        // fell straight through to `catch (Throwable)` and was silently swallowed.
        String language = "en-US";
        PlayerRef player = Universe.get().getPlayer(playerUuid);
        if (player != null) {
            String playerLanguage = player.getLanguage();
            if (playerLanguage != null && !playerLanguage.isBlank()) {
                language = playerLanguage;
            }
        }

        StringBuilder systemPrompt = new StringBuilder();
        
        // 1. Basic Stats
        systemPrompt.append("You are an NPC called ").append(npc.name).append(" in the SimTale universe.\n");
        if (npc.gender != null) {
            systemPrompt.append("Gender: ").append(npc.gender.getDisplayName()).append(".\n");
        }
        
        // 2. Personality & Traits
        if (npc.personality != null) {
            systemPrompt.append("Your personality is defined by:\n");
            systemPrompt.append("- Kindness: ").append(npc.personality.kindness).append("/100\n");
            systemPrompt.append("- Humor: ").append(npc.personality.humor).append("/100\n");
            systemPrompt.append("- Aggressiveness: ").append(npc.personality.aggression).append("/100\n");
            systemPrompt.append("- Charisma: ").append(npc.personality.charisma).append("/100\n");
            
            if (npc.personality.traits != null && !npc.personality.traits.isEmpty()) {
                systemPrompt.append("Personality traits: ");
                List<String> traitsList = new ArrayList<>();
                for (Trait trait : npc.personality.traits) {
                    traitsList.add(trait.name());
                }
                systemPrompt.append(String.join(", ", traitsList)).append(".\n");
            }
        }

        // 3. Current Mood
        Mood mood = npc.getMood();
        systemPrompt.append("Your current mood is ").append(mood.ptName);
        if (npc.emotionIntensity > 0) {
            systemPrompt.append(" (intensity: ").append(String.format("%.1f", npc.emotionIntensity)).append("/1.0, cause: ").append(npc.emotionSource).append(")");
        }
        systemPrompt.append(".\n");

        // 4. Profession & Work Status
        if (npc.profession != null) {
            systemPrompt.append("Your profession: ").append(npc.profession.ptName).append(".\n");
        }
        if (npc.currentJob != null && npc.currentJob != com.cookieukw.SimTale.logic.JobType.NONE) {
            systemPrompt.append("Currently, you are working as: ").append(npc.currentJob.getPortugueseName()).append(".\n");
        }

        // 5. Needs
        if (npc.needs != null) {
            systemPrompt.append("Your biological and social needs:\n");
            systemPrompt.append("- Hunger: ").append(String.format("%.1f", npc.needs.hunger)).append("/100\n");
            systemPrompt.append("- Energy: ").append(String.format("%.1f", npc.needs.energy)).append("/100\n");
            systemPrompt.append("- Social: ").append(String.format("%.1f", npc.needs.social)).append("/100\n");
            systemPrompt.append("- Fun: ").append(String.format("%.1f", npc.needs.fun)).append("/100\n");
            systemPrompt.append("- Hygiene: ").append(String.format("%.1f", npc.needs.hygiene)).append("/100\n");
            if (npc.needs.isMiserable()) {
                systemPrompt.append("WARNING: You are feeling very miserable!\n");
            }
        }

        // 6. Preferences
        if (npc.preferences != null) {
            systemPrompt.append("Your preferences:\n");
            
            List<String> favs = new ArrayList<>();
            if (npc.preferences.getFavoriteFoods() != null) {
                for (String f : npc.preferences.getFavoriteFoods()) {
                    favs.add(NPCPreferences.getFoodDisplayName(f));
                }
            }
            if (npc.preferences.getFavoriteItems() != null) {
                for (String i : npc.preferences.getFavoriteItems()) {
                    favs.add(NPCPreferences.getFoodDisplayName(i));
                }
            }
            if (!favs.isEmpty()) {
                systemPrompt.append("- Things/Foods you love: ").append(String.join(", ", favs)).append("\n");
            }
            
            List<String> hated = new ArrayList<>();
            if (npc.preferences.getHatedFoods() != null) {
                for (String f : npc.preferences.getHatedFoods()) {
                    hated.add(NPCPreferences.getFoodDisplayName(f));
                }
            }
            if (npc.preferences.getHatedItems() != null) {
                for (String i : npc.preferences.getHatedItems()) {
                    hated.add(NPCPreferences.getFoodDisplayName(i));
                }
            }
            if (!hated.isEmpty()) {
                systemPrompt.append("- Things/Foods you hate: ").append(String.join(", ", hated)).append("\n");
            }
            
            systemPrompt.append("- Favorite season: ").append(npc.preferences.getSeasonDisplayName()).append("\n");
            systemPrompt.append("- Favorite weather: ").append(npc.preferences.getWeatherDisplayName()).append("\n");
            systemPrompt.append("- Hobby: ").append(npc.preferences.getHobby().displayName()).append("\n");
        }

        // 7. Family Status
        if (npc.family != null) {
            if (npc.family.isMarried) {
                systemPrompt.append("You are married. Spouse ID: ").append(npc.family.spouseId).append(".\n");
            } else {
                systemPrompt.append("You are not married.\n");
            }
            if (npc.family.children != null && !npc.family.children.isEmpty()) {
                systemPrompt.append("You have ").append(npc.family.getChildCount()).append(" children: ");
                List<String> childNames = new ArrayList<>();
                for (Child child : npc.family.children) {
                    childNames.add(child.name != null ? child.name : child.id.toString());
                }
                systemPrompt.append(String.join(", ", childNames)).append(".\n");
            }
        }

        // 8. Relationship with speaking Player
        Relationship rel = npc.getRelationship(playerUuid);
        systemPrompt.append("Your relationship with player ").append(playerName).append(":\n");
        systemPrompt.append("- Status: ").append(rel.status.name()).append("\n");
        systemPrompt.append("- Affinity: ").append(rel.affinity).append(" (scale -100 to 1000)\n");
        systemPrompt.append("- Friendship: ").append(rel.friendship).append(" (scale -100 to 100)\n");
        systemPrompt.append("- Romance: ").append(rel.romance).append(" (scale 0 to 100)\n");
        systemPrompt.append("- Trust: ").append(rel.trust).append(" (scale -100 to 100)\n");

        // 9. Recent Memories
        if (npc.memory != null && npc.memory.recentMemories != null && !npc.memory.recentMemories.isEmpty()) {
            systemPrompt.append("Your recent memories:\n");
            long now = System.currentTimeMillis();
            for (Memory mem : npc.memory.recentMemories) {
                long ageSecs = (now - mem.timestamp) / 1000;
                String sourceName = (mem.playerSource != null && mem.playerSource.equals(playerUuid.toString())) ? playerName : "Alguém";
                systemPrompt.append("- Event [").append(mem.event.name()).append("] with ").append(sourceName)
                        .append(" há ").append(ageSecs).append(" segundos atrás.\n");
            }
        }
        // 10. Directives
        systemPrompt.append("\nRespond in the first person in a natural way, maintaining total consistency with your personality, mood, traits and feelings towards the player. Do not break character. Important: You must respond exclusively in ").append(language).append("’s native language.");

        // Metadata Map construction for tracing/debug
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("npcId", npc.entityId != null ? npc.entityId.toString() : "");
        metadata.put("playerUuid", playerUuid.toString());
        metadata.put("friendship", rel.friendship);
        metadata.put("romance", rel.romance);
        metadata.put("mood", mood.name());

        return new AiRequest(
                npc.name,
                playerName,
                systemPrompt.toString(),
                conversationHistory,
                metadata, playerUuid
        );
    }
}
