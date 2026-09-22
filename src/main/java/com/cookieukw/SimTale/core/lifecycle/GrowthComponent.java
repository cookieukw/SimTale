package com.cookieukw.SimTale.core.lifecycle;

import com.cookieukw.SimTale.core.Gender;

import java.util.UUID;

/**
 * Growth component assigned to an NPC that is the child of another.
 * Represents all information about a being born in the game that is growing.
 * A single entity with this component goes through all stages
 * (BABY → TODDLER → CHILD → TEEN → ADULT) just by changing the stage
 * and the visual scale.
 */
public class GrowthComponent {

    public UUID childId;
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

    /**
     * Which of the 200 pre-generated Human_(Child_)Male/Female role variants this child's body
     * uses — same face, hair and skin tone at every stage, since {@code generate_child_variants.py}
     * built each child role by proportionally rescaling the ADULT role of the exact same number,
     * not from a separate random pool. -1 means "not rolled yet": every promotion that spawns a
     * body picks and keeps one number for the child's whole life instead of re-rolling, so growing
     * up ages the same person into an adult body instead of swapping her for a random stranger who
     * happens to share a name. Records created before this field existed just roll theirs the first
     * time a promotion needs one (see the fallback in GrowthManager), same idiom as
     * FamilySystem.desiredChildren.
     */
    public int variant = -1;

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
        this.currentScale = GrowthStage.BABY.getScale();
    }

    /**
     * Calculates the age in in-game days.
     */
    public int getAgeDays(long currentTick) {
        if (currentTick < birthTick) {
            // World was restarted / session tick counter reset
            this.birthTick = currentTick - (this.stage.getStartDay() * PregnancyComponent.TICKS_PER_DAY);
        }
        int age = (int) ((currentTick - birthTick) / PregnancyComponent.TICKS_PER_DAY);
        return Math.max(stage.getStartDay(), age);
    }

    /**
     * Updates the growth stage based on age.
     *
     * @return true if the stage changed
     */
    /**
     * Whether {@link #updateStage} would change the stage, without changing anything.
     * <p>
     * Lets the caller decide when a promotion happens instead of finding out afterwards — needed
     * to keep a backlog of overdue children from all growing up in the same tick.
     */
    public boolean wouldChangeStage(long currentTick) {
        return GrowthStage.fromAge(getAgeDays(currentTick)).ordinal() > this.stage.ordinal();
    }

    public boolean updateStage(long currentTick) {
        int ageDays = getAgeDays(currentTick);
        GrowthStage newStage = GrowthStage.fromAge(ageDays);
        if (newStage.ordinal() > this.stage.ordinal()) {
            this.stage = newStage;
            this.currentScale = Math.max(this.currentScale, newStage.getScale());
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
