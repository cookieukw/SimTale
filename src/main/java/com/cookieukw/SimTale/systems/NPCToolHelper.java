package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.util.InventoryHelper;

/**
 * Puts the profession's tool in the NPC's hand, the same way {@link NPCGuardHelper} already does
 * for a guard's weapon.
 *
 * <p>Before this only guards held anything. Farmers, miners, hunters, fishermen, lumberjacks,
 * builders and explorers all worked bare-handed, so from the outside there was no visual signal
 * that a profession was doing anything at all.
 */
public final class NPCToolHelper {

    private NPCToolHelper() {
    }

    /** The item that represents the profession in hand, or null when there is none. */
    public static String toolFor(Profession profession) {
        if (profession == null) return null;
        return switch (profession) {
            case MINER -> "Tool_Pickaxe_Copper";
            case FARMER -> "Tool_Hoe_Copper";
            case FISHERMAN -> "Tool_Fishing_Trap";
            case LUMBERJACK -> "Tool_Hatchet_Copper";
            case BUILDER -> "Tool_Hammer_Copper";
            case EXPLORER -> "Tool_Map";
            case HUNTER -> "Weapon_Shortbow_Copper";
            // Guards already get the exact weapon that promoted them, via NPCGuardHelper.
            case GUARD, UNEMPLOYED -> null;
        };
    }

    /**
     * Ensures the tool is in hotbar slot 0 and selected. Safe to call unconditionally: both
     * {@link InventoryHelper} calls no-op when the NPC already holds the right item.
     */
    public static void ensureToolEquipped(Ref<EntityStore> ref, SimNPCComponent npc, Store<EntityStore> store) {
        if (ref == null || !ref.isValid() || npc == null || store == null) return;

        String itemId = toolFor(npc.profession);
        if (itemId == null) return;

        /* If the tool is already somewhere in the hotbar, just select it. Otherwise prefer an
        empty slot: workers carry what they gather, and blindly writing slot 0 (as the guard
        helper does) would silently replace that stack with a pickaxe.
        */
        byte slot = InventoryHelper.findHotbarSlotWithItem(ref, store, itemId);
        if (slot < 0) {
            slot = InventoryHelper.findHotbarEmptySlot(ref, store);
        }
        if (slot < 0) {
            slot = 0;
        }

        if (InventoryHelper.setHotbarItem(ref, itemId, slot, store)) {
            InventoryHelper.setHotbarSlot(ref, slot, store);
        }
    }
}
