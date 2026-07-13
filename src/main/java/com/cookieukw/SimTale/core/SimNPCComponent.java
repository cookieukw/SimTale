package com.cookieukw.SimTale.core;

import com.cookieukw.SimTale.db.SimBedData.BedPos;
import com.cookieukw.SimTale.engine.MagicEngine;
import com.cookieukw.SimTale.core.lifecycle.PregnancyComponent;
import com.cookieukw.SimTale.logic.JobType;
import com.cookieukw.SimTale.logic.SocialStats;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
    public NPCPreferences preferences;

    public FamilySystem family = new FamilySystem();
    public Gender gender;
    public PregnancyComponent pregnancy;
    public BedPos bedLocation;
    public transient Ref<EntityStore> entityRef;
    public transient ModelComponent originalModel;
    
    // Emotion system fields
    public Mood activeEmotion = Mood.NEUTRAL;
    public float emotionIntensity = 0.0f;
    public String emotionSource = "routine";
    public long lastEmotionChangeTick = 0;
    
    // MobsAndMates job and conversation state
    public UUID currentConversationPartner;
    public long conversationTimeoutTick;
    public JobType currentJob = JobType.NONE;
    public long jobDepartureTick;
    public long jobCompletionTick;
    public UUID jobEmployer;
    public boolean isAway = false;
    public transient MagicEngine activeMagicGame;
    public transient boolean isInteractingViaUI = false;
    public transient boolean forceSleep = false;
    public transient Mood lastPlayedEmotion = null;

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

        if (bedLocation != null) {
            clone.bedLocation = new BedPos(bedLocation.x, bedLocation.y, bedLocation.z);
        }
        
        // Clone new states
        clone.currentConversationPartner = currentConversationPartner;
        clone.conversationTimeoutTick = conversationTimeoutTick;
        clone.currentJob = currentJob;
        clone.jobDepartureTick = jobDepartureTick;
        clone.jobCompletionTick = jobCompletionTick;
        clone.jobEmployer = jobEmployer;
        clone.isAway = isAway;
        
        // Clone emotion state
        clone.activeEmotion = activeEmotion;
        clone.emotionIntensity = emotionIntensity;
        clone.emotionSource = emotionSource;
        clone.lastEmotionChangeTick = lastEmotionChangeTick;
        
        // Clone pregnancy
        if (pregnancy != null) {
            clone.pregnancy = new PregnancyComponent();
            clone.pregnancy.pregnant = pregnancy.pregnant;
            clone.pregnancy.fatherId = pregnancy.fatherId;
            clone.pregnancy.startTick = pregnancy.startTick;
            clone.pregnancy.durationTicks = pregnancy.durationTicks;
            clone.pregnancy.trimester = pregnancy.trimester;
        }
        return clone;
    }

    public Relationship getRelationship(UUID target) {
        return relationships.computeIfAbsent(target, Relationship::new);
    }

    public Mood getMood() {
        return activeEmotion != null ? activeEmotion : Mood.NEUTRAL;
    }

    public void setEmotion(Mood emotion, float intensity, String source, long currentTick) {
        long elapsed = currentTick - lastEmotionChangeTick;
        boolean forceChange = false;
        
        if (elapsed < 100) {
            int newPriority = getEmotionPriority(emotion);
            int currentPriority = getEmotionPriority(activeEmotion);
            if (newPriority > currentPriority) {
                forceChange = true;
            } else if (intensity - emotionIntensity > 0.4f) {
                forceChange = true;
            }
        } else {
            forceChange = true;
        }

        if (forceChange) {
            this.activeEmotion = emotion;
            this.emotionIntensity = Math.max(0.0f, Math.min(1.0f, intensity));
            this.emotionSource = source;
            this.lastEmotionChangeTick = currentTick;
        }
    }

    private int getEmotionPriority(Mood m) {
        if (m == null) return 0;
        return switch (m) {
            case SCARED -> 6;
            case ANGRY -> 5;
            case SAD -> 4;
            case SLEEPY -> 3;
            case EXCITED, HAPPY -> 2;
            case BORED -> 1;
            case NEUTRAL -> 0;
        };
    }
}
