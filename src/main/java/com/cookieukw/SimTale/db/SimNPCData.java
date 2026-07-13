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
import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.lifecycle.PregnancyComponent;

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
    public Gender gender;
    public SimBedData.BedPos bedLocation;
    public PregnancyComponent pregnancy;
    
    // Emotion system persistence fields
    public String activeEmotion = "NEUTRAL";
    public float emotionIntensity = 0.0f;
    public String emotionSource = "routine";
    public long lastEmotionChangeTick = 0;

    /**
     * Required for Caskara POJO persistence.
     */
    public SimNPCData() {
    }

    public SimNPCData(UUID entityId, String name, Personality personality, Needs needs, SocialStats stats, MemoryManager memory, Profession profession, NPCPreferences preferences, FamilySystem family, Gender gender, SimBedData.BedPos bedLocation, PregnancyComponent pregnancy) {
        this.id = entityId.toString();
        this.name = name;
        this.personality = personality;
        this.needs = needs;
        this.stats = stats;
        this.memory = memory;
        this.profession = profession;
        this.preferences = preferences;
        this.family = family;
        this.gender = gender;
        this.bedLocation = bedLocation;
        this.pregnancy = pregnancy;
    }
}
