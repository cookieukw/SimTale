package com.cookieukw.SimTale.core.lifecycle;

import java.util.Random;

/**
 * Seeds genéticas que determinam a aparência e personalidade de um filho.
 * Todas as propriedades visuais e comportamentais podem ser regeneradas
 * deterministicamente a partir dessas seeds, evitando salvar dezenas de atributos.
 */
public class GeneticsData {

    /** Seed que determina atributos visuais: cor de pele, cabelo, olhos, altura. */
    public long geneticsSeed;

    /** Seed que determina a personalidade base. */
    public long personalitySeed;

    // --- Dados derivados (cacheable, regeneráveis a partir das seeds) ---

    /** Índice do gradiente de cor de pele (do catálogo de GradientSets). */
    public transient String skinGradientId;

    /** Índice do gradiente de cor de cabelo. */
    public transient String hairGradientId;

    /** Índice do gradiente de cor dos olhos. */
    public transient String eyeGradientId;

    /** Multiplicador de altura relativa (0.85 a 1.15). */
    public transient float heightMultiplier = 1.0f;

    /** Índice do modelo de cabelo no catálogo. */
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
     * Combina as seeds de dois pais para criar as seeds do filho.
     * Usa XOR e deslocamento para misturar de forma determinística.
     */
    public static GeneticsData combine(GeneticsData mother, GeneticsData father) {
        Random mixer = new Random(mother.geneticsSeed ^ father.geneticsSeed);
        long childGenetics = mixer.nextLong();
        long childPersonality = mixer.nextLong();
        return new GeneticsData(childGenetics, childPersonality);
    }

    /**
     * Regenera os dados visuais derivados a partir da seed genética.
     * Isso permite reconstruir a aparência sem salvar cada atributo individual.
     *
     * @param skinGradients lista de IDs de gradientes de pele disponíveis
     * @param hairGradients lista de IDs de gradientes de cabelo disponíveis
     * @param eyeGradients  lista de IDs de gradientes de olhos disponíveis
     * @param totalHairModels total de modelos de cabelo disponíveis
     */
    public void regenerateAppearance(String[] skinGradients, String[] hairGradients,
                                      String[] eyeGradients, int totalHairModels) {
        Random rng = new Random(geneticsSeed);

        // Seleciona gradientes baseados na seed
        this.skinGradientId = skinGradients[rng.nextInt(skinGradients.length)];
        this.hairGradientId = hairGradients[rng.nextInt(hairGradients.length)];
        this.eyeGradientId = eyeGradients[rng.nextInt(eyeGradients.length)];

        // Altura relativa: 0.85 a 1.15
        this.heightMultiplier = 0.85f + rng.nextFloat() * 0.30f;

        // Modelo de cabelo
        this.hairModelIndex = rng.nextInt(totalHairModels);
    }

    /**
     * Herda um atributo dos pais com chance 50/50.
     * Útil para propriedades que precisam de herança discreta.
     */
    public static <T> T inherit(T fromMother, T fromFather) {
        return Math.random() < 0.5 ? fromMother : fromFather;
    }

    /**
     * Gera um sobrenome do filho combinando os sobrenomes dos pais.
     * 50% de chance para cada sobrenome.
     *
     * @param motherName nome completo da mãe (ex: "Luna Greenfield")
     * @param fatherName nome completo do pai (ex: "Kori Steelbinder")
     * @return sobrenome herdado, ou "" se nenhum pai tiver sobrenome
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
