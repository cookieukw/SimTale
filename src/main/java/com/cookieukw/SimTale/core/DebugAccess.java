package com.cookieukw.SimTale.core;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;

/**
 * Who is allowed to act from the inspection screens, as opposed to merely reading them.
 *
 * <p>The screens were gated on {@code readOnly}, which only ever answered "did an item open this
 * or a command". A survival player running {@code /simtale debugchests} got Teleport and Remove,
 * and Teleport over the chest registry is a free warp to anywhere they have ever stored something
 * — the exact cheat the item version was written to avoid.
 *
 * <p>Creative is the right line rather than a permission group: the debug screens exist to build
 * and diagnose a village, and that is what creative mode already means. In survival the same
 * screens stay open and stay useful, they just stop being a control panel.
 */
public final class DebugAccess {

    private DebugAccess() {
    }

    public static boolean canEdit(Player player) {
        return player != null && player.getGameMode() == GameMode.Creative;
    }
}
