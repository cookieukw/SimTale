package com.cookieukw.SimTale.core;
import com.cookieukw.SimTale.SimTale;

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

    /**
     * Surnames are built from two halves instead of picked from a list.
     *
     * <p>The old list held thirty entries and only half the NPCs got one at all, so a village of
     * twenty had visible repeats within minutes — and, worse, unrelated NPCs shared a surname, which
     * reads as family when nothing about them is related. These two arrays give 26 x 26 = 676
     * combinations in the same tone, which is enough that a shared surname is much more likely to
     * mean an actual shared ancestor.
     *
     * <p>Split into halves for a second reason: {@link #blendSurnames} needs a seam to cut on, so a
     * child of Greenfield and Steelbinder can be born a Greenbinder. That only produces something
     * pronounceable if the pieces were designed to be interchangeable.
     */
    private static final String[] SURNAME_HEAD = {
        "Green", "Iron", "Storm", "Ash", "Oak", "Stone", "Silver", "Ember",
        "Frost", "Thorn", "Bright", "Dusk", "Fair", "Grim", "Hollow", "Amber",
        "Wild", "Deep", "Swift", "Copper", "Moss", "Salt", "Bram", "Elder",
        "North", "Reed"
    };

    private static final String[] SURNAME_TAIL = {
        "field", "hide", "walker", "brook", "wood", "hand", "born", "keeper",
        "mind", "smith", "forge", "water", "foot", "caller", "mender", "stone",
        "heart", "bearer", "weaver", "warden", "ridge", "hollow", "vale", "crest",
        "shade", "mare"
    };

    private static final Random RANDOM = new Random();

    private static String pick(String[] array) {
        return array[RANDOM.nextInt(array.length)];
    }

    /** A fresh family name, for an NPC with no parents in this world. */
    public static String generateSurname() {
        return pick(SURNAME_HEAD) + pick(SURNAME_TAIL);
    }

    /** Just the given name, with no family name attached. */
    public static String generateFirstName() {
        String firstName = pick(SYLLABLES_START) + pick(SYLLABLES_MIDDLE);
        if (RANDOM.nextDouble() < 0.4) {
            firstName += pick(SYLLABLES_END);
        }
        return firstName;
    }

    /**
     * The family name a child of these two parents carries.
     *
     * <p>Most of the time it is one parent's name unchanged, which is what keeps siblings
     * recognisable as siblings and lets a lineage stay readable across generations. The rest of the
     * time the two names are cut at the seam and recombined, so the world slowly grows family names
     * nobody started with — a Greenfield marrying a Steelbinder can found the Greenbinders.
     *
     * <p>Always fusing was rejected for the same reason: if every child invents a surname, the
     * surname stops carrying information at all.
     *
     * @param motherSurname may be null or empty
     * @param fatherSurname may be null or empty
     */
    public static String inheritSurname(String motherSurname, String fatherSurname) {
        boolean hasMother = motherSurname != null && !motherSurname.isEmpty();
        boolean hasFather = fatherSurname != null && !fatherSurname.isEmpty();

        // A child of a parent with no family name still gets one, rather than growing up nameless
        // and passing that gap to its own children.
        if (!hasMother && !hasFather) return generateSurname();
        if (!hasMother) return fatherSurname;
        if (!hasFather) return motherSurname;

        if (RANDOM.nextDouble() < 0.25) {
            String blended = blendSurnames(motherSurname, fatherSurname);
            if (blended != null) return blended;
        }
        return RANDOM.nextBoolean() ? motherSurname : fatherSurname;
    }

    /**
     * Head of one surname plus tail of the other, or null when neither can be split.
     *
     * <p>Splitting is done by looking the halves up in the arrays rather than cutting at a fixed
     * character count: a blind cut on "Ash" and "Underhill" produces sounds no other name in the
     * world has. Returning null when a name is unrecognised is deliberate — an imported or
     * hand-edited surname is left whole instead of being mangled.
     */
    private static String blendSurnames(String a, String b) {
        String headA = matchingPrefix(a);
        String tailB = matchingSuffix(b);
        String headB = matchingPrefix(b);
        String tailA = matchingSuffix(a);

        boolean firstWay = headA != null && tailB != null;
        boolean secondWay = headB != null && tailA != null;

        if (firstWay && secondWay) {
            return RANDOM.nextBoolean() ? headA + tailB : headB + tailA;
        }
        if (firstWay) return headA + tailB;
        if (secondWay) return headB + tailA;
        return null;
    }

    private static String matchingPrefix(String surname) {
        for (String head : SURNAME_HEAD) {
            if (surname.startsWith(head)) return head;
        }
        return null;
    }

    private static String matchingSuffix(String surname) {
        for (String tail : SURNAME_TAIL) {
            if (surname.endsWith(tail)) return tail;
        }
        return null;
    }

    /** The family name inside a full name, or an empty string when there is none. */
    public static String extractSurname(String fullName) {
        if (fullName == null || !fullName.contains(" ")) return "";
        return fullName.substring(fullName.lastIndexOf(' ') + 1);
    }

    /**
     * Generates a full name for an NPC with no parents in this world.
     *
     * <p>Everyone gets a surname now. The old coin flip left half the village on a first name
     * alone, which made those NPCs look like a different class of character and made the family
     * inheritance below start from nothing every other generation.
     *
     * @return a randomly generated name
     */
    public static String generate() {
        String name;
        int attempts = 0;
        boolean unique;
        do {
            name = generateFirstName() + " " + generateSurname();

            unique = true;
            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.name != null && npc.name.equalsIgnoreCase(name)) {
                    unique = false;
                    break;
                }
            }
            attempts++;
        } while (!unique && attempts < 50);

        return name;
    }

    /**
     * Generates a first name that, combined with {@code surname}, does not collide with any
     * currently active NPC's full name.
     *
     * <p>A child born from a pregnancy inherits its surname instead of rolling one (see
     * {@code GeneticsData.inheritSurname} / {@code PregnancyManager}), so it cannot call
     * {@link #generate()} outright -- only the first-name half is free to retry. Before this,
     * children's names were generated with a plain {@link #generateFirstName()} and no
     * uniqueness check at all, so two unrelated children born around the same time could end up
     * with the exact same full name purely by chance (confirmed in game 13/09: two separate
     * "grew to Bebe" log lines for the same full name).
     *
     * @param surname the child's already-decided (inherited) surname
     * @return a first name such that {@code firstName + " " + surname} is unique, best-effort
     */
    public static String generateUniqueFirstName(String surname) {
        String firstName;
        int attempts = 0;
        boolean unique;
        do {
            firstName = generateFirstName();
            String fullName = (surname == null || surname.isEmpty()) ? firstName : firstName + " " + surname;

            unique = true;
            for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                if (npc.name != null && npc.name.equalsIgnoreCase(fullName)) {
                    unique = false;
                    break;
                }
            }
            attempts++;
        } while (!unique && attempts < 50);

        return firstName;
    }
}
