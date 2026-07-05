package com.cookieukw.SimTale.core.lifecycle;

import com.cookieukw.SimTale.core.Gender;

import java.util.UUID;

/**
 * Componente de crescimento atribuído a um NPC que é filho de outro.
 * Representa toda a informação de um ser que nasceu no jogo e está crescendo.
 *
 * Uma única entidade com este componente passa por todos os estágios
 * (BABY → TODDLER → CHILD → TEEN → ADULT) apenas mudando o estágio
 * e a escala visual.
 */
public class GrowthComponent {

    /** UUID da mãe. */
    public UUID motherId;

    /** UUID do pai. */
    public UUID fatherId;

    /** Tick absoluto do mundo em que nasceu. */
    public long birthTick;

    /** Estágio de crescimento atual. */
    public GrowthStage stage = GrowthStage.BABY;

    /** Gênero do filho. */
    public Gender gender;

    /** Dados genéticos (seeds para aparência e personalidade). */
    public GeneticsData genetics;

    /** Necessidades do bebê (fome, afeto, saúde). */
    public BabyNeeds babyNeeds;

    /** Nome do filho. */
    public String name;

    /** Sobrenome herdado. */
    public String surname;

    /**
     * Se true, o filho está sendo carregado por alguém (NPC mãe ou player).
     * O UUID de quem está carregando fica em {@link #carriedBy}.
     */
    public boolean isBeingCarried = false;

    /** UUID da entidade que está carregando este filho (mãe ou player). */
    public UUID carriedBy;

    /**
     * Escala atual aplicada ao modelo. Atualizada conforme o estágio muda.
     */
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
     * Calcula a idade em dias in-game.
     */
    public int getAgeDays(long currentTick) {
        return (int) ((currentTick - birthTick) / PregnancyComponent.TICKS_PER_DAY);
    }

    /**
     * Atualiza o estágio de crescimento baseado na idade.
     *
     * @return true se o estágio mudou
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

    /**
     * Verifica se o filho já é adulto.
     */
    public boolean isAdult() {
        return stage == GrowthStage.ADULT;
    }

    /**
     * Verifica se o filho ainda precisa de cuidados (BABY ou TODDLER).
     */
    public boolean needsCare() {
        return stage == GrowthStage.BABY || stage == GrowthStage.TODDLER;
    }

    /**
     * Pega o filho no colo.
     */
    public void pickUp(UUID carrierId) {
        this.isBeingCarried = true;
        this.carriedBy = carrierId;
        // Carregado → afeto sobe
        if (babyNeeds != null) {
            babyNeeds.showAffection(5f);
        }
    }

    /**
     * Solta o filho.
     */
    public void putDown() {
        this.isBeingCarried = false;
        this.carriedBy = null;
    }

    /**
     * Retorna o nome completo (nome + sobrenome).
     */
    public String getFullName() {
        if (surname != null && !surname.isEmpty()) {
            return name + " " + surname;
        }
        return name;
    }
}
