package com.cookieukw.SimTale.db;

import com.cookieukw.SimTale.core.Needs;
import com.cookieukw.SimTale.core.Personality;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.logic.SocialStats;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Data Transfer Object for persisting SimTale NPC state via Caskara.
 */
public class SimNPCData {
    public String id; // UUID string
    public String name;
    public Personality personality;
    public Needs needs;
    public SocialStats stats;
    public Map<String, Relationship> relationships = new HashMap<>();

    /**
     * Required for Caskara POJO persistence.
     */
    public SimNPCData() {
    }

    public SimNPCData(UUID entityId, String name, Personality personality, Needs needs, SocialStats stats) {
        this.id = entityId.toString();
        this.name = name;
        this.personality = personality;
        this.needs = needs;
        this.stats = stats;
    }
}
