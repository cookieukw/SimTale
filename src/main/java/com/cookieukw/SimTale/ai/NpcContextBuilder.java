package com.cookieukw.SimTale.ai;


import com.cookieukw.SimTale.logic.JobType;
import java.util.regex.Pattern;
import com.cookieukw.SimTale.core.Child;
import com.cookieukw.SimTale.core.Memory;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.NeedsHelper;
import com.cookieukw.SimTale.core.NPCPreferences;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.Trait;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.WorldUtil;
import com.cookieukw.SimTale.core.lifecycle.LifecycleUtils;
import com.cookieukw.SimTale.systems.HouseManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NpcContextBuilder {

    private static final Pattern LEADING_NAME_TAG =
            Pattern.compile("^\\s*\\[[^\\]]{0,40}\\]:?\\s*");

    /** "Nearby" for AI world-awareness purposes -- same range RoutineAISystem already uses
     *  to look for an available chat partner (SOCIALIZE_SEARCH_RANGE_SQ), so what the NPC
     *  says about "who's around" matches what its own AI actually considers nearby. */
    private static final double NEARBY_PLAYER_RANGE_SQ = 20.0 * 20.0;

    /**
     * Strips a leading "[Name]" / "[Name]:" tag the model sometimes prepends despite being told
     * not to (see the directive in {@link #build}) — including the failure mode where it emits
     * the literal placeholder "[NomeNPC]" instead of substituting the real name. Safety net for
     * prompt non-compliance, not a replacement for the instruction itself.
     */
    public static String stripLeadingNameTag(String text) {
        if (text == null) return null;
        return LEADING_NAME_TAG.matcher(text).replaceFirst("");
    }

    /**
     * True when at least one other currently-loaded NPC shares this profession and none of them
     * has completed more jobs than {@code npc}. A lone practitioner of a profession (no rival
     * loaded at all) returns false -- "best in the village" should mean there was competition,
     * not just that nobody else happens to be around right now. Ties count both sides as "the
     * best", which is a harmless bit of village pride, not a bug.
     */
    private static boolean isTopInProfession(SimNPCComponent npc) {
        boolean hasRival = false;
        for (SimNPCComponent other : SimTale.ACTIVE_NPCS) {
            if (other == npc || other.profession != npc.profession) continue;
            hasRival = true;
            if (other.jobsCompleted > npc.jobsCompleted) return false;
        }
        return hasRival;
    }

    public static AiRequest build(SimNPCComponent npc, UUID playerUuid, String playerName, List<AiMessage> conversationHistory) {
       
        /* `UUID.fromString(playerUuid.toString())` was a no-op round-trip, and the null check
        was an `assert`, which the JVM disables by default — so on a real server the NPE
        fell straight through to `catch (Throwable)` and was silently swallowed.
        */
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
        /* mood.name(), not mood.ptName: the rest of this prompt is English, and the reply
        language is dictated by the directive at the end instead.
        */
        systemPrompt.append("Your current mood is ").append(mood.name());
        if (npc.emotionIntensity > 0) {
            systemPrompt.append(" (intensity: ").append(String.format("%.1f", npc.emotionIntensity)).append("/1.0, cause: ").append(npc.emotionSource).append(")");
        }
        systemPrompt.append(".\n");

        // 4. Profession & Work Status
        if (npc.profession != null) {
            systemPrompt.append("Your profession: ").append(npc.profession.name()).append(".\n");
        }
        if (npc.currentJob != null && npc.currentJob != JobType.NONE) {
            systemPrompt.append("Currently, you are working as: ").append(npc.currentJob.name()).append(".\n");
        }
        if (npc.profession != null && npc.jobsCompleted > 0) {
            systemPrompt.append("Jobs completed in your profession so far: ").append(npc.jobsCompleted).append(".\n");
            if (isTopInProfession(npc)) {
                systemPrompt.append("Word around the village is that you are the best ")
                        .append(npc.profession.name())
                        .append(" there is -- nobody else in that trade has finished as many jobs as you.\n");
            }
        }

        // 5. Needs
        systemPrompt.append("Your biological and social needs:\n");
        systemPrompt.append("- Hunger: ").append(String.format("%.1f", NeedsHelper.getNeed(null, npc.entityRef, NeedsHelper.HUNGER_ID))).append("/100\n");
        systemPrompt.append("- Energy: ").append(String.format("%.1f", NeedsHelper.getNeed(null, npc.entityRef, NeedsHelper.ENERGY_ID))).append("/100\n");
        systemPrompt.append("- Social: ").append(String.format("%.1f", NeedsHelper.getNeed(null, npc.entityRef, NeedsHelper.SOCIAL_ID))).append("/100\n");
        systemPrompt.append("- Fun: ").append(String.format("%.1f", NeedsHelper.getNeed(null, npc.entityRef, NeedsHelper.FUN_ID))).append("/100\n");
        systemPrompt.append("- Hygiene: ").append(String.format("%.1f", NeedsHelper.getNeed(null, npc.entityRef, NeedsHelper.HYGIENE_ID))).append("/100\n");
        if (NeedsHelper.isMiserable(null, npc.entityRef)) {
            systemPrompt.append("WARNING: You are feeling very miserable!\n");
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
                String sourceName = (mem.playerSource != null && mem.playerSource.equals(playerUuid.toString())) ? playerName : "someone else";
                systemPrompt.append("- Event [").append(mem.event.name()).append("] with ").append(sourceName)
                        .append(", ").append(ageSecs).append(" seconds ago.\n");
            }
        }
        /* 9b. World Awareness -- location, time, health, home, nearby players, other NPC bonds

        Added per docs/ROADMAP.md's "Contexto da IA" item: all of this reads data that already
        exists elsewhere in the mod (position, the built-in Health stat, HouseManager's owner
        map, the relationships map already used for the player above) -- no new system, just
        wiring more of what is already tracked into the prompt.
        */
        Ref<EntityStore> npcRef = npc.entityRef;
        Store<EntityStore> npcStore = (npcRef != null && npcRef.isValid()) ? npcRef.getStore() : null;

        Vector3d npcPos = null;
        if (npcStore != null) {
            TransformComponent npcTransform = npcStore.getComponent(npcRef, TransformComponent.getComponentType());
            if (npcTransform != null) {
                npcPos = npcTransform.getPosition();
                systemPrompt.append("Your current location: (").append((int) npcPos.x).append(", ")
                        .append((int) npcPos.y).append(", ").append((int) npcPos.z).append(").\n");
            }
        }

        World npcWorld = (npcRef != null) ? WorldUtil.fromEntityRef(npcRef) : null;
        if (npcWorld != null) {
            systemPrompt.append("Current world tick: ").append(npcWorld.getTick()).append(".\n");
        }

        if (npcStore != null) {
            EntityStatMap statMap = npcStore.getComponent(npcRef, EntityStatMap.getComponentType());
            EntityStatValue hp = statMap != null ? statMap.get(DefaultEntityStatTypes.getHealth()) : null;
            if (hp != null) {
                systemPrompt.append("Your health: ").append(String.format("%.0f", hp.get()))
                        .append("/").append(String.format("%.0f", hp.getMax())).append(".\n");
            }
        }

        boolean hasHome = npc.entityId != null && HouseManager.OWNER_TO_HOUSE_ID.containsKey(npc.entityId);
        systemPrompt.append(hasHome
                ? "You have a home registered in the village.\n"
                : "You do not have a home of your own yet.\n");

        if (npcPos != null) {
            /* Same 20-block radius RoutineAISystem.SOCIALIZE_SEARCH_RANGE_SQ uses to decide
            who counts as "nearby" for its own social-partner search, so what the NPC says
            about who's around lines up with what its AI actually treats as close by.
            */
            List<String> nearbyPlayers = new ArrayList<>();
            for (PlayerRef pr : Universe.get().getPlayers()) {
                if (pr.getUuid() != null && pr.getUuid().equals(playerUuid)) continue; // the one already talking
                Ref<EntityStore> pRef = pr.getReference();
                if (pRef == null || !pRef.isValid()) continue;
                TransformComponent pt = pRef.getStore().getComponent(pRef, TransformComponent.getComponentType());
                if (pt == null) continue;
                if (pt.getPosition().distanceSquared(npcPos) <= NEARBY_PLAYER_RANGE_SQ) {
                    String otherName = pr.getUsername();
                    if (otherName != null) nearbyPlayers.add(otherName);
                }
            }
            if (!nearbyPlayers.isEmpty()) {
                systemPrompt.append("Other players nearby: ").append(String.join(", ", nearbyPlayers)).append(".\n");
            }
        }

        if (npc.relationships != null && !npc.relationships.isEmpty()) {
            List<String> npcBonds = new ArrayList<>();
            for (Map.Entry<UUID, Relationship> entry : npc.relationships.entrySet()) {
                if (entry.getKey().equals(playerUuid)) continue; // already covered above
                SimNPCComponent other = LifecycleUtils.findNPCById(entry.getKey());
                if (other == null) continue; // not a currently-loaded NPC (most likely a player entry)
                Relationship r = entry.getValue();
                npcBonds.add(other.name + " (" + r.status.name() + ", friendship " + r.friendship + ")");
            }
            if (!npcBonds.isEmpty()) {
                systemPrompt.append("Your relationships with other villagers: ")
                        .append(String.join("; ", npcBonds)).append(".\n");
            }
        }

        /* 10. Directives

        Without an explicit ban, the model tends to imitate chat-script formatting from its
        training data and prefixes its own reply with a speaker tag — sometimes even a literal,
        unsubstituted placeholder like "[NomeNPC]" instead of the real name — which then doubles
        up with the "[Name] " prefix the game itself adds when displaying the message.
        */
        systemPrompt.append("\nRespond in the first person in a natural way, maintaining total consistency with your personality, mood, traits and feelings towards the player. Do not break character. Reply with ONLY the words you say — no name tag, no speaker label, no brackets, no quotation marks around the whole reply, no formatting of any kind. The game already shows your name next to the message. Important: you must reply exclusively in the language with the locale code ").append(language).append(".");

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
