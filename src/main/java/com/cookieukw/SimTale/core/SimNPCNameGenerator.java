package com.cookieukw.SimTale.core;

import java.util.Random;

/**
 * Generates random, immersive names for SimTale NPCs using syllables and surnames.
 * Ported from the original MobsAndMates mod.
 */
public class SimNPCNameGenerator {

    private static final String[] SYLLABLES_START = {
        "Ka", "Lo", "Mi", "Ru", "Sa", "Te", "Va", "Zo", "Ne", "Fi",
        "Ga", "Di", "Ar", "El", "Or", "Ul", "In", "Tha", "Bra", "Vor",
        "Xe", "Qu", "Ri", "Po", "Ma", "Jo", "Ha", "Ni", "Pa", "Ze",
        "Yu", "Ki", "Do", "Fa", "Se", "La", "Tor", "Bel", "Var", "Gor",
        "Zan", "Kel", "Fen", "Ser", "Ion", "Grim"
    };

    private static final String[] SYLLABLES_MIDDLE = {
        "ri", "na", "lo", "ta", "ma", "ro", "va", "za", "li", "ko",
        "su", "fi", "dra", "sha", "lon", "mir", "kor", "zan", "ren", "vil",
        "tur", "sel", "mon", "dar", "tra", "zor", "vek", "sin", "ria", "pho",
        "tal", "gen", "bar", "kin", "vor", "rian", "thel", "mar", "den", "fur",
        "zen", "lir", "vok", "sar"
    };

    private static final String[] SYLLABLES_END = {
        "n", "ra", "to", "ma", "va", "dor", "zan", "s", "ka", "rix",
        "th", "mir", "los", "dan", "nor", "rik", "vus", "sha", "zor", "li",
        "ren", "var", "dun", "mar", "vek", "tal", "mos", "rin", "dak"
    };

    private static final String[] SURNAMES = {
        "Greenfield", "Cornhand", "Wheatborn", "VeggieLord", "Haystacker",
        "Bookseer", "Quillmind", "Scrollkeeper", "Inkwhisper", "Wiseleaf",
        "Steelbinder", "Ironhide", "Shieldsmith", "Metalforge", "Platestrong",
        "Swiftfish", "Netthrow", "Deepwater", "Hookmaster", "Seafoot",
        "Holybrew", "Lightcaller", "Soulmend", "Faithstone", "Pureheart",
        "Stonewalker", "Dustcatcher", "Creeperbane", "Torchbearer", "TradeMaster"
    };

    private static final Random RANDOM = new Random();

    private static String pick(String[] array) {
        return array[RANDOM.nextInt(array.length)];
    }

    /**
     * Generates a random name combining start, middle, and optional end syllables,
     * with a 50% chance of adding a thematic surname.
     * 
     * @return a randomly generated name
     */
    public static String generate() {
        String name;
        int attempts = 0;
        boolean unique;
        do {
            String firstName = pick(SYLLABLES_START) + pick(SYLLABLES_MIDDLE);
            if (RANDOM.nextDouble() < 0.4) {
                firstName += pick(SYLLABLES_END);
            }

            if (RANDOM.nextDouble() < 0.5) {
                name = firstName + " " + pick(SURNAMES);
            } else {
                name = firstName;
            }

            unique = true;
            for (SimNPCComponent npc : com.cookieukw.SimTale.SimTale.ACTIVE_NPCS) {
                if (npc.name != null && npc.name.equalsIgnoreCase(name)) {
                    unique = false;
                    break;
                }
            }
            attempts++;
        } while (!unique && attempts < 50);
        
        return name;
    }
}
