package com.cookieukw.SimTale.core.lifecycle;

import java.util.Random;

/**
 * Seeds genéticas que determinam a aparência e personalidade de um filho.
 * Todas as propriedades visuais e comportamentais podem ser regeneradas
 * deterministicamente a partir dessas seeds, evitando salvar dezenas de atributos.
 */
public class GeneticsData {

    public long geneticsSeed;
    public long personalitySeed;

    public transient String skinGradientId;
    public transient String hairGradientId;
    public transient String eyeGradientId;
    public transient float heightMultiplier = 1.0f;

    public transient int hairModelIndex;

    public GeneticsData() {
        this.geneticsSeed = new Random().nextLong();
        this.personalitySeed = new Random().nextLong();
    }

    public GeneticsData(long geneticsSeed, long personalitySeed) {
        this.geneticsSeed = geneticsSeed;
        this.personalitySeed = personalitySeed;
    }

    /**
     * Combines the seeds of two parents to create the child's seeds.
     * Uses XOR and displacement to mix deterministically.
     */
    public static GeneticsData combine(GeneticsData mother, GeneticsData father) {
        Random mixer = new Random(mother.geneticsSeed ^ father.geneticsSeed);
        long childGenetics = mixer.nextLong();
        long childPersonality = mixer.nextLong();
        return new GeneticsData(childGenetics, childPersonality);
    }

    /**
     * Regenerates the derived visual data from the genetic seed.
     * This allows reconstructing the appearance without saving each individual attribute.
     *
     * @param skinGradients list of available skin gradient IDs
     * @param hairGradients list of available hair gradient IDs
     * @param eyeGradients  list of available eye gradient IDs
     * @param totalHairModels total number of available hair models
     */
    public void regenerateAppearance(String[] skinGradients, String[] hairGradients,
                                      String[] eyeGradients, int totalHairModels) {
        Random rng = new Random(geneticsSeed);

        this.skinGradientId = skinGradients[rng.nextInt(skinGradients.length)];
        this.hairGradientId = hairGradients[rng.nextInt(hairGradients.length)];
        this.eyeGradientId = eyeGradients[rng.nextInt(eyeGradients.length)];
        this.heightMultiplier = 0.85f + rng.nextFloat() * 0.30f;
        this.hairModelIndex = rng.nextInt(totalHairModels);
    }

    /**
     * Herds an attribute from one of the parents with 50/50 probability.
     */
    public static <T> T inherit(T fromMother, T fromFather) {
        return Math.random() < 0.5 ? fromMother : fromFather;
    }

    /**
     * Generates a child's surname by combining the parents' surnames.
     * Uses 50/50 probability for each parent's surname.
     *
     * @param motherName mother's full name (e.g., "Luna Greenfield")
     * @param fatherName father's full name (e.g., "Kori Steelbinder")
     * @return inherited surname, or "N/A" if neither parent has a surname
     */
    public static String inheritSurname(String motherName, String fatherName) {
        String motherSurname = extractSurname(motherName);
        String fatherSurname = extractSurname(fatherName);

        if (motherSurname.isEmpty() && fatherSurname.isEmpty()) return "";
        if (motherSurname.isEmpty()) return fatherSurname;
        if (fatherSurname.isEmpty()) return motherSurname;

        return Math.random() < 0.5 ? motherSurname : fatherSurname;
    }

    private static String extractSurname(String fullName) {
        if (fullName == null || !fullName.contains(" ")) return "";
        return fullName.substring(fullName.lastIndexOf(' ') + 1);
    }
}
