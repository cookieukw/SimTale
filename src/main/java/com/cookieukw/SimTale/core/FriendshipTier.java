package com.cookieukw.SimTale.core;

import javax.annotation.Nonnull;

/**
 * Friendship tiers used to select the TOM of NPC lines, not only
 * to free/lock actions (as was done with the affinity checks
 * scattered throughout the ChatHandler).
 * Adjust the limits (min/max) freely if the real scale of
 * `friendship` is not -100..100 -- just mess with the constants here,
 * nothing else in the code depends on the exact value, only on the order.
 */
public enum FriendshipTier {
    HOSTILE(-100, -21, "hostile"),
    STRANGER(-20, 19, "stranger"),
    ACQUAINTANCE(20, 49, "acquaintance"),
    FRIEND(50, 79, "friend"),
    CLOSE_FRIEND(80, 100, "close_friend");

    public final int min;
    public final int max;
    /** Used as part of the translation key, e.g.: "npc-interactions.chat.greeting.friend.1" */
    public final String translationKey;

    FriendshipTier(int min, int max, String translationKey) {
        this.min = min;
        this.max = max;
        this.translationKey = translationKey;
    }

    @Nonnull
    public static FriendshipTier fromAffinity(int affinity) {
        for (FriendshipTier tier : values()) {
            if (affinity >= tier.min && affinity <= tier.max) {
                return tier;
            }
        }
        /* Off any tier (affinity below the minimum or above the
        maximum expected) -- falls to the nearest extreme instead of
        throwing an exception, so never lock the chat because of it.
        */
        return affinity < HOSTILE.min ? HOSTILE : CLOSE_FRIEND;
    }
}