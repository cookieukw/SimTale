package com.cookieukw.SimTale.tests;

import com.cookieukw.SimTale.core.SimNPCNameGenerator;
import com.cookieukw.SimTale.core.lifecycle.GeneticsData;

import java.util.HashSet;
import java.util.Set;

/**
 * Names: variety, and what a child inherits.
 *
 * <p>Written after a village turned out full of repeats — thirty hardcoded surnames, half the NPCs
 * with none at all, and every child a player had carrying the literal surname "SimTale". The
 * assertions below are the rules that came out of fixing that; the sample dump at the end exists
 * because the interesting property here is qualitative. "Does this read like a village" is not
 * something an assertion can answer, so the suite prints its work and lets a human look.
 */
public final class NameTests {

    private NameTests() {
    }

    public static void run() {
        Assert.suite("Every NPC gets a family name", NameTests::alwaysHasSurname);
        Assert.suite("Surnames vary", NameTests::variety);
        Assert.suite("A child inherits from its parents", NameTests::inheritance);
        Assert.suite("Unknown surnames are never mangled", NameTests::foreignSurnames);
        Assert.suite("No parent means a new family", NameTests::orphanSurname);

        printSamples();
    }

    private static void alwaysHasSurname() {
        // The old generator flipped a coin and left half the village on a first name alone, which
        // also meant inheritance restarted from nothing every other generation.
        for (int i = 0; i < 200; i++) {
            String name = SimNPCNameGenerator.generate();
            Assert.isTrue(name.contains(" "), "generated name has two parts: " + name);
            Assert.isFalse(SimNPCNameGenerator.extractSurname(name).isEmpty(),
                    "surname is not empty: " + name);
        }
    }

    private static void variety() {
        Set<String> surnames = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            surnames.add(SimNPCNameGenerator.generateSurname());
        }
        // The old list could not exceed 30 no matter how many draws. The bar is set well under the
        // 676 possible combinations because this is a randomness check, not a coupon-collector one.
        Assert.isTrue(surnames.size() > 200,
                "500 draws produce more than 200 distinct surnames, got " + surnames.size());

        Set<String> firstNames = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            firstNames.add(SimNPCNameGenerator.generateFirstName());
        }
        Assert.isTrue(firstNames.size() > 300,
                "first names vary too, got " + firstNames.size() + " distinct in 500");
    }

    private static void inheritance() {
        String mother = "Greenfield";
        String father = "Ironforge";

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            seen.add(SimNPCNameGenerator.inheritSurname(mother, father));
        }

        // Only four names are reachable: either parent whole, or the two halves swapped. Anything
        // else means the blend cut somewhere it should not have.
        for (String result : seen) {
            boolean expected = result.equals("Greenfield") || result.equals("Ironforge")
                    || result.equals("Greenforge") || result.equals("Ironfield");
            Assert.isTrue(expected, "inherited surname is a parent's or a clean blend: " + result);
        }

        Assert.isTrue(seen.contains("Greenfield"), "the mother's name is reachable");
        Assert.isTrue(seen.contains("Ironforge"), "the father's name is reachable");
        Assert.isTrue(seen.contains("Greenforge") || seen.contains("Ironfield"),
                "blending happens: a new family name appears over 2000 births");

        // One parent missing a surname is not a reason to invent one — the other line is right there.
        Assert.equal(SimNPCNameGenerator.inheritSurname("Greenfield", ""), "Greenfield",
                "a child of one named parent takes that name");
        Assert.equal(SimNPCNameGenerator.inheritSurname(null, "Ironforge"), "Ironforge",
                "null is treated as absent, not as a name");

        // GeneticsData is what the birth code calls, and it takes full names rather than surnames.
        String fromFullNames = GeneticsData.inheritSurname("Luna Greenfield", "Kori Ironforge");
        Assert.isFalse(fromFullNames.contains(" "),
                "inheriting from full names yields a surname, not a whole name: " + fromFullNames);
    }

    private static void foreignSurnames() {
        // A surname that did not come from the generator — imported, hand-edited, from an older
        // save — has no seam the blend can recognise, so it must pass through whole.
        for (int i = 0; i < 500; i++) {
            String result = SimNPCNameGenerator.inheritSurname("Wollstonecraft", "Nakagawa");
            boolean intact = result.equals("Wollstonecraft") || result.equals("Nakagawa");
            Assert.isTrue(intact, "unrecognised surnames are inherited whole, got: " + result);
        }

        // Half-recognised is the interesting case: one side can be split, the other cannot, so the
        // blend has exactly one valid direction and must not force the other.
        for (int i = 0; i < 500; i++) {
            String result = SimNPCNameGenerator.inheritSurname("Greenfield", "Nakagawa");
            boolean valid = result.equals("Greenfield") || result.equals("Nakagawa")
                    || result.equals("Green" + "field");
            Assert.isTrue(valid, "one-sided blend stays sane, got: " + result);
        }
    }

    private static void orphanSurname() {
        for (int i = 0; i < 100; i++) {
            String result = SimNPCNameGenerator.inheritSurname("", "");
            Assert.isFalse(result.isEmpty(),
                    "a child of two nameless parents founds a family rather than inheriting a gap");
        }
    }

    /**
     * Prints what the rules actually produce.
     *
     * <p>Not an assertion and not noise: the failure this suite exists to catch was "the village
     * feels repetitive", which no threshold expresses well. Reading twenty names is faster than
     * arguing about a distinct-count.
     */
    private static void printSamples() {
        System.out.println();
        System.out.println("  Ten villagers with no family in this world:");
        for (int i = 0; i < 10; i++) {
            System.out.println("    " + SimNPCNameGenerator.generate());
        }

        System.out.println();
        System.out.println("  Ten children, with the parents they came from:");
        for (int i = 0; i < 10; i++) {
            String mother = SimNPCNameGenerator.generate();
            String father = SimNPCNameGenerator.generate();
            String childSurname = GeneticsData.inheritSurname(mother, father);
            String child = SimNPCNameGenerator.generateFirstName() + " " + childSurname;

            String motherSurname = SimNPCNameGenerator.extractSurname(mother);
            String fatherSurname = SimNPCNameGenerator.extractSurname(father);
            String note = childSurname.equals(motherSurname) ? "mother's"
                    : childSurname.equals(fatherSurname) ? "father's"
                    : "blended";

            System.out.println("    " + mother + "  +  " + father);
            System.out.println("      -> " + child + "   (" + note + ")");
        }
        System.out.println();
    }
}
