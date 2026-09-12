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
    public SocialStats stats;
    public MemoryManager memory = new MemoryManager();
    public Map<UUID, Relationship> relationships = new HashMap<>();
    public Profession profession;
    /** Melee vs. ranged, resolved from whatever item last made this NPC a Guard. Only meaningful
     *  when {@code profession == Profession.GUARD}; defaults to MELEE so an existing save (from
     *  before this field existed, always sword-only) keeps its current combat behavior. */
    public WeaponCategory guardWeaponCategory = WeaponCategory.MELEE;
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
     * Ticks since the face animation was last played.
     *
     * <p>Facial expressions are one-shot clips, so a mood that never changes shows its face exactly
     * once and then sits neutral forever. This drives a periodic replay.
     */
    public transient int expressionAge = 0;

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
        clone.guardWeaponCategory = guardWeaponCategory;
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

    /** How long a mood is protected from being replaced by a weaker one: 30 s. */
    public static final long EMOTION_HOLD_TICKS = 600;

    /** Below this, a mood counts as faded and stops defending its slot. */
    private static final float FADED_EMOTION = 0.25f;

    /**
     * Sets the current mood, unless something stronger is still in effect.
     *
     * <p>The old rule was "after 100 ticks, anything overwrites anything". Five seconds is nothing,
     * so ambient triggers — the idleness roll, the wellness check — steamrolled real emotions: an
     * NPC made happy on purpose turned BORED seconds later. Worse, the ambient HAPPY at intensity
     * 0.3 would overwrite a HAPPY at 1.0, quietly weakening it.
     *
     * <p>Now priority decides. Something stronger always lands. Something equal only lands if it is
     * at least as intense, or if the hold window has passed. Something weaker has to wait for the
     * current mood to both hold its time and fade.
     */
    public void setEmotion(Mood emotion, float intensity, String source, long currentTick) {
        long elapsed = currentTick - lastEmotionChangeTick;
        int newPriority = getEmotionPriority(emotion);
        int currentPriority = getEmotionPriority(activeEmotion);

        boolean change;
        if (activeEmotion == null || activeEmotion == Mood.NEUTRAL) {
            change = true;
        } else if (newPriority > currentPriority) {
            change = true;
        } else if (newPriority == currentPriority) {
            // Same feeling: a stronger dose refreshes it, a weaker one waits its turn.
            change = intensity >= emotionIntensity || elapsed >= EMOTION_HOLD_TICKS;
        } else {
            change = elapsed >= EMOTION_HOLD_TICKS && emotionIntensity <= FADED_EMOTION;
        }

        if (change) {
            this.activeEmotion = emotion;
            this.emotionIntensity = Math.clamp(intensity, 0.0f, 1.0f);
            this.emotionSource = source;
            this.lastEmotionChangeTick = currentTick;
        }
    }

    /**
     * Sets mood unconditionally, bypassing the priority/hold guard {@link #setEmotion} enforces.
     * <p>
     * {@code /simtale setmood} calls this instead of {@link #setEmotion}. That guard exists to
     * stop ambient triggers (the idleness roll, the wellness check) from stealing a real emotion's
     * spotlight — exactly right for organic mood changes, but wrong for an operator command: a
     * tester who scolded an NPC seconds ago and then runs {@code setmood HAPPY} wants HAPPY, not
     * "denied, ANGRY is still within its 30s hold". Going through {@link #setEmotion} made the
     * command silently no-op whenever a stronger mood was still protected, while still printing
     * "Mood definido para HAPPY" — reported success and did nothing, with nothing in the message
     * or the log to tell the two apart.
     */
    public void forceEmotion(Mood emotion, float intensity, String source, long currentTick) {
        this.activeEmotion = emotion;
        this.emotionIntensity = Math.clamp(intensity, 0.0f, 1.0f);
        this.emotionSource = source;
        this.lastEmotionChangeTick = currentTick;
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
