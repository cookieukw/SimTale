package com.cookieukw.SimTale.core;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.cookieukw.SimTale.logic.SocialStats;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.cookieukw.SimTale.logic.JobType;
import com.cookieukw.SimTale.engine.MagicEngine;

/**
 * Persists SimTale data for an entity.
 */
public class SimNPCComponent implements Component<EntityStore> {
    public UUID entityId;
    public String name;
    public Personality personality;
    public Needs needs;
    public SocialStats stats;
    public MemoryManager memory = new MemoryManager();
    public Map<UUID, Relationship> relationships = new HashMap<>();
    public Profession profession;
    public NPCPreferences preferences = new NPCPreferences();
    public FamilySystem family = new FamilySystem();



    // Runtime properties
    public transient Ref<EntityStore> entityRef;
    public transient ModelComponent originalModel;
    
    // MobsAndMates job and conversation state
    public UUID currentConversationPartner;
    public long conversationTimeoutTick;
    public JobType currentJob = JobType.NONE;
    public long jobDepartureTick;
    public long jobCompletionTick;
    public UUID jobEmployer;
    public boolean isAway = false;
    public transient MagicEngine activeMagicGame;

    /**
     * Default constructor for registry and codecs.
     */
    public SimNPCComponent() {
        this.personality = Personality.createDefault();
        this.needs = new Needs();
        this.stats = new SocialStats();
        this.preferences = NPCPreferences.createRandom();
        assignRandomProfession();
    }

    public SimNPCComponent(UUID entityId, String name) {
        this();
        this.entityId = entityId;
        this.name = name;
    }

    private void assignRandomProfession() {
        Profession[] profs = Profession.values();
        this.profession = profs[(int)(Math.random() * profs.length)];
    }

    @Override
    public SimNPCComponent clone() {
        SimNPCComponent clone = new SimNPCComponent(entityId, name);
        clone.personality = new Personality(personality.kindness, personality.humor, personality.aggression,
                personality.charisma);
        clone.needs = new Needs();
        clone.needs.hunger = needs.hunger;
        clone.needs.energy = needs.energy;
        clone.needs.social = needs.social;
        clone.needs.fun = needs.fun;
        clone.stats = new SocialStats();
        clone.stats.level = stats.level;
        clone.stats.xp = stats.xp;
        clone.memory = new MemoryManager();
        clone.memory.recentMemories.addAll(memory.recentMemories);
        clone.relationships = new HashMap<>(relationships);
        clone.preferences = new NPCPreferences();
        clone.preferences.favoriteFoods.addAll(preferences.favoriteFoods);
        clone.preferences.hatedFoods.addAll(preferences.hatedFoods);
        clone.preferences.favoriteSeason = preferences.favoriteSeason;
        clone.preferences.favoriteWeather = preferences.favoriteWeather;
        clone.preferences.hobby = preferences.hobby;
        
        // Clone new states
        clone.currentConversationPartner = currentConversationPartner;
        clone.conversationTimeoutTick = conversationTimeoutTick;
        clone.currentJob = currentJob;
        clone.jobDepartureTick = jobDepartureTick;
        clone.jobCompletionTick = jobCompletionTick;
        clone.jobEmployer = jobEmployer;
        clone.isAway = isAway;
        return clone;
    }

    public Relationship getRelationship(UUID target) {
        return relationships.computeIfAbsent(target, Relationship::new);
    }

    public Mood getMood() {
        if (memory.remembers(MemoryEvent.ATTACKED, null, 300000)) return Mood.SCARED;
        if (memory.remembers(MemoryEvent.INSULTED, null, 120000)) return Mood.ANGRY;
        
        if (needs != null) {
            if (personality.traits.contains(Trait.AGGRESSIVE) && (needs.hunger < 50 || needs.energy < 50)) return Mood.ANGRY;
            if (needs.energy < 20) return Mood.SLEEPY;
            if (needs.isMiserable()) return Mood.SAD;
            return Mood.HAPPY;
        }
        return Mood.NEUTRAL;
    }
}
