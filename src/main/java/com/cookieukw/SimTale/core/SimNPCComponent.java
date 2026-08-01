package com.cookieukw.SimTale.core;

import com.cookieukw.SimTale.db.SimBedData.BedPos;
import com.cookieukw.SimTale.engine.MagicEngine;
import com.cookieukw.SimTale.core.lifecycle.PregnancyComponent;
import com.cookieukw.SimTale.logic.JobType;
import com.cookieukw.SimTale.logic.SocialStats;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashMap;
import java.util.HashSet;
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
    /**
     * Marks the Grim Reaper NPC. This used to be inferred with {@code name.contains("Reaper")},
     * which never matched because the factory names it "Dona Morte" — meaning no reaper was
     * ever dispatched to collect a dying NPC.
     */
    public boolean isReaper = false;
    public transient MagicEngine activeMagicGame;
    /**
     * False until this component has been filled in from Caskara (or freshly created by the
     * factory).
     * <p>
     * Matters because the codec restores only the id and name: everything else comes back from
     * the default constructor, which rolls a *random* profession and preferences. Saving in
     * that state would overwrite the NPC's real data in the database with the placeholder, so
     * {@code SimNPCPersistence.saveNPC} refuses to write while this is false.
     */
    public transient boolean dataLoaded = false;

    public transient boolean isInteractingViaUI = false;
    /**
     * Player whose interaction page is currently open, so the AI can keep the NPC turned
     * toward them. Separate from {@link #currentConversationPartner}, which the page clears on
     * open to stop the chat timeout from firing during the dialogue.
     */
    public transient UUID uiInteractionPlayer;
    public transient boolean forceSleep = false;
    public transient Mood lastPlayedEmotion = null;

    /**
     * Persists only the identity of the NPC — its id and name.
     * <p>
     * Everything else (personality, needs, relationships, family, pregnancy) still lives in
     * Caskara and is restored by {@code SimNPCPersistence.loadNPC} on the first tick. The point
     * of this codec is not to store the data twice; it is to make sure the component itself is
     * always present on the entity.
     * <p>
     * Without it the component was runtime-only, so every reload left the entity with no
     * SimNPCComponent at all and the mod had to guess its way back: SimTaleTickSystem would
     * notice the gap, look the id up in Caskara and re-attach. That only runs while the entity
     * is being ticked, so an NPC could sit there orphaned until the player walked close enough
     * — visibly idle, with no plumbob, and invisible to /simtale clearall (which iterates
     * ACTIVE_NPCS), while the interaction key still worked because it has its own Caskara
     * fallback. Anchoring the id here removes that whole failure mode.
     */
    public static final BuilderCodec<SimNPCComponent> CODEC = BuilderCodec
        .builder(SimNPCComponent.class, SimNPCComponent::new)
        .append(new KeyedCodec<>("EntityId", Codec.STRING),
                (c, v) -> c.entityId = (v != null && !v.isEmpty()) ? UUID.fromString(v) : null,
                c -> c.entityId != null ? c.entityId.toString() : "").add()
        .append(new KeyedCodec<>("Name", Codec.STRING),
                (c, v) -> { if (v != null && !v.isEmpty()) c.name = v; },
                c -> c.name != null ? c.name : "").add()
        .build();

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
        // Traits are the whole point of Personality — without this the clone silently lost
        // GREEDY/SHY/LAZY/... and behaved like a blank NPC.
        if (personality.traits != null) {
            clone.personality.traits = new HashSet<>(personality.traits);
        }
        clone.needs = new Needs();
        clone.needs.hunger = needs.hunger;
        clone.needs.energy = needs.energy;
        clone.needs.social = needs.social;
        clone.needs.fun = needs.fun;
        clone.needs.hygiene = needs.hygiene;
        clone.stats = new SocialStats();
        clone.stats.level = stats.level;
        clone.stats.xp = stats.xp;
        clone.memory = new MemoryManager();
        clone.memory.recentMemories.addAll(memory.recentMemories);
        // Deep copy: a shallow HashMap copy shares the Relationship objects, so mutating the
        // clone's affinity/romance also mutated the original's.
        clone.relationships = new HashMap<>();
        for (Map.Entry<UUID, Relationship> entry : relationships.entrySet()) {
            Relationship source = entry.getValue();
            if (source == null) continue;
            Relationship copy = new Relationship(entry.getKey());
            copy.affinity = source.affinity;
            copy.friendship = source.friendship;
            copy.romance = source.romance;
            copy.trust = source.trust;
            copy.interactionsToday = source.interactionsToday;
            copy.lastInteractionDayIndex = source.lastInteractionDayIndex;
            copy.status = source.status;
            clone.relationships.put(entry.getKey(), copy);
        }
        clone.preferences = new NPCPreferences(
            preferences.getFavoriteFoods(),
            preferences.getHatedFoods(),
            preferences.getFavoriteItems(),
            preferences.getHatedItems(),
            preferences.getFavoriteSeason(),
            preferences.getFavoriteWeather(),
            preferences.getHobby(),
            preferences.getLikedProfessions(),
            preferences.getDislikedProfessions()
        );

        if (bedLocation != null) {
            clone.bedLocation = new BedPos(bedLocation.x, bedLocation.y, bedLocation.z, bedLocation.yaw);
        }

        // Carried over so a clone of a loaded component is still allowed to save; otherwise the
        // ECS swapping in a clone would silently block persistence for that NPC.
        clone.dataLoaded = dataLoaded;

        // Identity/family state — must be copied explicitly, otherwise the
        // default constructor's random profession would leak into the clone.
        clone.profession = profession;
        clone.gender = gender;
        clone.family = family;

        // Clone new states
        clone.currentConversationPartner = currentConversationPartner;
        clone.conversationTimeoutTick = conversationTimeoutTick;
        clone.currentJob = currentJob;
        clone.jobDepartureTick = jobDepartureTick;
        clone.jobCompletionTick = jobCompletionTick;
        clone.jobEmployer = jobEmployer;
        clone.isAway = isAway;
        clone.isReaper = isReaper;
        
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
            this.emotionIntensity = Math.clamp(intensity, 0.0f, 1.0f);
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
