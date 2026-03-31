package com.cookieukw.SimTale.core;

import com.cookieukw.SimTale.logic.SocialStats;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.cookieukw.SimTale.logic.JobType;

/**
 * Persists SimTale data for an entity.
 */
public class SimNPCComponent implements Component<EntityStore> {
    public UUID entityId;
    public String name;
    public Personality personality;
    public Needs needs;
    public SocialStats stats;
    public Map<UUID, Relationship> relationships = new HashMap<>();

    // MobsAndMates job and conversation state
    public UUID currentConversationPartner;
    public long conversationTimeoutTick;
    public JobType currentJob = JobType.NONE;
    public long jobCompletionTick;
    public UUID jobEmployer;

    /**
     * Default constructor for registry and codecs.
     */
    public SimNPCComponent() {
        this.personality = Personality.createDefault();
        this.needs = new Needs();
        this.stats = new SocialStats();
    }

    public SimNPCComponent(UUID entityId, String name) {
        this();
        this.entityId = entityId;
        this.name = name;
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
        clone.relationships = new HashMap<>(relationships);
        
        // Clone new states
        clone.currentConversationPartner = currentConversationPartner;
        clone.conversationTimeoutTick = conversationTimeoutTick;
        clone.currentJob = currentJob;
        clone.jobCompletionTick = jobCompletionTick;
        clone.jobEmployer = jobEmployer;
        return clone;
    }

    public Relationship getRelationship(UUID target) {
        return relationships.computeIfAbsent(target, Relationship::new);
    }
}
