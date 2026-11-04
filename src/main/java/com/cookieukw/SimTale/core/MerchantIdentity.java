package com.cookieukw.SimTale.core;

import java.util.SplittableRandom;

/**
 * Everything that makes one visit feel like a different person.
 *
 * <p>It is rolled from the visit seed, so the same visit restored after a reload keeps the same
 * name, body and trade — a merchant must not change face because the server restarted.
 */
public record MerchantIdentity(String name, MerchantSpecialty specialty, Gender gender, int variant) {
    private static final String[] FIRST_NAMES = {
            "Mara", "Oswin", "Tilda", "Corvin", "Sable", "Emeric", "Runa", "Valdis",
            "Perrin", "Ysolde", "Bram", "Nessa", "Orrin", "Lysa", "Doran", "Ivy",
            "Kestrel", "Halden", "Neve", "Tobias", "Mira", "Alaric", "Sena", "Rowan"
    };

    private static final String[] LAST_NAMES = {
            "Voss", "Merrin", "Holloway", "Ashdown", "Dray", "Fenn", "Blackwater",
            "Thorne", "Marsh", "Quill", "Greaves", "Alder", "Sallow", "Reed",
            "Crane", "Wick", "Barrow", "Lark", "Stone", "Frost"
    };

    public static MerchantIdentity roll(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        String first = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
        String last = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
        MerchantSpecialty specialty = MerchantSpecialty.values()[random.nextInt(MerchantSpecialty.values().length)];
        Gender gender = random.nextBoolean() ? Gender.MALE : Gender.FEMALE;
        int variant = 1 + random.nextInt(200);
        return new MerchantIdentity(first + " " + last, specialty, gender, variant);
    }

    public SimNPCFactory.NPCType npcType() {
        return gender == Gender.FEMALE
                ? SimNPCFactory.NPCType.HUMAN_FEMALE
                : SimNPCFactory.NPCType.HUMAN_MALE;
    }
}
