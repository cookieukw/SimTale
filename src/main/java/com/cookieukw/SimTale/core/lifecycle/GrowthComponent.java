package com.cookieukw.SimTale.core.lifecycle;

import com.cookieukw.SimTale.core.Gender;

import java.util.UUID;

/**
 * Growth component assigned to an NPC that is the child of another.
 * Represents all information about a being born in the game that is growing.
 *
 * A single entity with this component goes through all stages
 * (BABY → TODDLER → CHILD → TEEN → ADULT) just by changing the stage
 * and the visual scale.
 */
public class GrowthComponent {

    public UUID motherId;

    public UUID fatherId;

    public long birthTick;

    public GrowthStage stage = GrowthStage.BABY;

    public Gender gender;
    public GeneticsData genetics;

    public BabyNeeds babyNeeds;
    public String name;

    public String surname;

    /**
     * If true, the child is being carried by someone (mother NPC or player).
     * The UUID of who is carrying is in {@link #carriedBy}.
     */
    public boolean isBeingCarried = false;
    public UUID carriedBy;

    public float currentScale;

    public GrowthComponent() {
        this.genetics = new GeneticsData();
        this.babyNeeds = new BabyNeeds();
        this.currentScale = GrowthStage.BABY.getScale();
    }

    public GrowthComponent(UUID motherId, UUID fatherId, long birthTick, Gender gender,
                           GeneticsData genetics, String name, String surname) {
        this.motherId = motherId;
        this.fatherId = fatherId;
        this.birthTick = birthTick;
        this.gender = gender;
        this.genetics = genetics;
        this.babyNeeds = new BabyNeeds();
        this.name = name;
        this.surname = surname;
        this.stage = GrowthStage.BABY;
        this.currentScale = GrowthStage.BABY.getScale();
    }

    /**
     * Calculates the age in in-game days.
     */
    public int getAgeDays(long currentTick) {
        return (int) ((currentTick - birthTick) / PregnancyComponent.TICKS_PER_DAY);
    }

    /**
     * Updates the growth stage based on age.
     *
     * @return true if the stage changed
     */
    public boolean updateStage(long currentTick) {
        int ageDays = getAgeDays(currentTick);
        GrowthStage newStage = GrowthStage.fromAge(ageDays);
        if (newStage != this.stage) {
            this.stage = newStage;
            this.currentScale = newStage.getScale();
            return true;
        }
        return false;
    }


    public boolean isAdult() {
        return stage == GrowthStage.ADULT;
    }

    public boolean needsCare() {
        return stage == GrowthStage.BABY || stage == GrowthStage.TODDLER;
    }

    public void pickUp(UUID carrierId) {
        this.isBeingCarried = true;
        this.carriedBy = carrierId;
        if (babyNeeds != null) {
            babyNeeds.showAffection(5f);
        }
    }

    public void putDown() {
        this.isBeingCarried = false;
        this.carriedBy = null;
    }

    public String getFullName() {
        if (surname != null && !surname.isEmpty()) {
            return name + " " + surname;
        }
        return name;
    }
}
