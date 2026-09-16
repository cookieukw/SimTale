package com.cookieukw.SimTale.tests;

import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.SimNPCComponent;

import java.util.UUID;

/**
 * Emotion priority.
 *
 * <p>Each case below is a balancing complaint that turned out to be a rule, not a number: "she was
 * happy and went bored two seconds later", and the quieter one where an ambient HAPPY at 0.3
 * silently overwrote a deliberate HAPPY at 1.0.
 */
public final class MoodTests {

    private MoodTests() {
    }

    /** Matches SimNPCComponent.EMOTION_HOLD_TICKS; read from the class so the two cannot drift. */
    private static final long HOLD = SimNPCComponent.EMOTION_HOLD_TICKS;

    public static void run() {
        Assert.suite("Anything lands on a neutral NPC", MoodTests::fromNeutral);
        Assert.suite("Stronger feelings win immediately", MoodTests::stronger);
        Assert.suite("Weaker feelings wait", MoodTests::weaker);
        Assert.suite("Same feeling: intensity decides", MoodTests::sameFeeling);
    }

    private static SimNPCComponent npc() {
        SimNPCComponent npc = new SimNPCComponent(UUID.randomUUID(), "Test");
        Assert.equal(npc.getMood(), Mood.NEUTRAL, "an NPC starts neutral");
        return npc;
    }

    private static void fromNeutral() {
        SimNPCComponent npc = npc();
        npc.setEmotion(Mood.BORED, 0.1f, "test", 0L);
        Assert.equal(npc.getMood(), Mood.BORED, "even the weakest mood lands on neutral");
        Assert.floatEqual(npc.emotionIntensity, 0.1f, "intensity is stored");
    }

    private static void stronger() {
        SimNPCComponent npc = npc();
        npc.setEmotion(Mood.HAPPY, 0.9f, "gift", 0L);
        npc.setEmotion(Mood.ANGRY, 0.3f, "insult", 1L);
        Assert.equal(npc.getMood(), Mood.ANGRY,
                "anger outranks happiness even at a much lower intensity");

        npc.setEmotion(Mood.SCARED, 0.1f, "damage", 2L);
        Assert.equal(npc.getMood(), Mood.SCARED, "fear outranks anger");
    }

    private static void weaker() {
        SimNPCComponent npc = npc();
        npc.setEmotion(Mood.ANGRY, 0.9f, "insult", 0L);

        // The reported bug: an ambient trigger stealing a real emotion moments later.
        npc.setEmotion(Mood.BORED, 0.4f, "idleness", 10L);
        Assert.equal(npc.getMood(), Mood.ANGRY, "boredom cannot interrupt fresh anger");

        // Holding its time is not enough on its own — the current mood also has to have faded.
        npc.setEmotion(Mood.BORED, 0.4f, "idleness", HOLD + 100L);
        Assert.equal(npc.getMood(), Mood.ANGRY,
                "still angry: the hold expired but the anger is still strong");

        // Decay is what fades it, and it lives in the tick system; emulate it here.
        npc.emotionIntensity = 0.2f;
        npc.setEmotion(Mood.BORED, 0.4f, "idleness", HOLD + 200L);
        Assert.equal(npc.getMood(), Mood.BORED,
                "held its time and faded, so a weaker mood finally lands");
    }

    private static void sameFeeling() {
        SimNPCComponent npc = npc();
        npc.setEmotion(Mood.HAPPY, 0.3f, "wellness", 0L);
        npc.setEmotion(Mood.HAPPY, 0.8f, "gift", 5L);
        Assert.floatEqual(npc.emotionIntensity, 0.8f, "a stronger dose of the same mood refreshes it");

        // The quiet bug: the ambient wellness HAPPY at 0.3 used to overwrite a deliberate 1.0.
        npc.setEmotion(Mood.HAPPY, 0.3f, "wellness", 10L);
        Assert.floatEqual(npc.emotionIntensity, 0.8f,
                "a weaker dose of the same mood must not water it down");

        /* Once the hold has passed the weaker dose is allowed through, which is what lets a mood
        eventually settle back down instead of sticking at its peak forever.
        */
        npc.setEmotion(Mood.HAPPY, 0.3f, "wellness", 10L + HOLD);
        Assert.floatEqual(npc.emotionIntensity, 0.3f, "after the hold, the weaker dose lands");

        // Intensity is clamped: callers pass fractions, and a stray 1.5 would never decay away.
        npc.setEmotion(Mood.HAPPY, 5.0f, "test", 20L + HOLD);
        Assert.floatEqual(npc.emotionIntensity, 1.0f, "intensity is clamped to 1");
        npc.setEmotion(Mood.SCARED, -3.0f, "test", 30L + HOLD);
        Assert.floatEqual(npc.emotionIntensity, 0.0f, "and to 0");
    }
}
