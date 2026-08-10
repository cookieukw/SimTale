package com.cookieukw.SimTale.tests;

/**
 * Assertions shared by every suite.
 *
 * <p>Deliberately not JUnit: the project has no test dependency and the runner is a plain
 * {@code main}, which keeps {@code ./gradlew runTests} working with the same {@code compileOnly}
 * classpath the mod itself uses. Adding JUnit would mean resolving it against a server jar that is
 * only available locally.
 */
public final class Assert {

    private Assert() {
    }

    /** Counted so a suite can report how much it actually checked, not just that it finished. */
    static int checks = 0;

    public static void isTrue(boolean actual, String message) {
        checks++;
        if (!actual) {
            throw new AssertionError(message + " — expected true, got false");
        }
    }

    public static void isFalse(boolean actual, String message) {
        checks++;
        if (actual) {
            throw new AssertionError(message + " — expected false, got true");
        }
    }

    public static void equal(Object actual, Object expected, String message) {
        checks++;
        if (actual == null && expected == null) return;
        if (actual == null || !actual.equals(expected)) {
            throw new AssertionError(message + " — expected: " + expected + ", got: " + actual);
        }
    }

    public static void floatEqual(float actual, float expected, String message) {
        checks++;
        if (Math.abs(actual - expected) > 0.00001f) {
            throw new AssertionError(message + " — expected: " + expected + ", got: " + actual);
        }
    }

    /** Runs a suite, prints its name and the number of assertions it made. */
    public static void suite(String name, Runnable body) {
        int before = checks;
        System.out.print("  " + name + " ... ");
        body.run();
        System.out.println("OK (" + (checks - before) + " checks)");
    }
}
