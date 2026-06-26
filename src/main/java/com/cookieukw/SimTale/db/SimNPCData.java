package com.cookieukw.SimTale.db;
import com.cookieukw.SimTale.core.MemoryManager;
import com.cookieukw.SimTale.core.Profession;

import com.cookieukw.SimTale.core.MemoryManager;
import com.cookieukw.SimTale.core.Needs;
import com.cookieukw.SimTale.core.Personality;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.logic.SocialStats;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.cookieukw.SimTale.core.NPCPreferences;
import com.cookieukw.SimTale.core.FamilySystem;

/**
 * Data Transfer Object for persisting SimTale NPC state via Caskara.
 */
public class SimNPCData {
    public String id; // UUID string
    public String name;
    public Personality personality;
    public Needs needs;
    public SocialStats stats;
    public MemoryManager memory;
    public Map<String, Relationship> relationships = new HashMap<>();
    public Profession profession;
    public NPCPreferences preferences;
    public FamilySystem family;

    /**
     * Required for Caskara POJO persistence.
     */
    public SimNPCData() {
    }

    public SimNPCData(UUID entityId, String name, Personality personality, Needs needs, SocialStats stats, MemoryManager memory, Profession profession, NPCPreferences preferences, FamilySystem family) {
        this.id = entityId.toString();
        this.name = name;
        this.personality = personality;
        this.needs = needs;
        this.stats = stats;
        this.memory = memory;
        this.profession = profession;
        this.preferences = preferences;
        this.family = family;
    }
}
