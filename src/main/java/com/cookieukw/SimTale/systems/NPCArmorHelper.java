package com.cookieukw.SimTale.systems;

import com.cookie.runecore.api.RuneAttributes;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Lets an NPC actually wear armour, and hooks it into RuneCore's dynamic combat stats.
 * <p>
 * Mirrors {@link NPCGuardHelper}'s weapon-in-hand approach: {@link SimNPCComponent} is the
 * source of truth ({@code armorItemIds}, one entry per {@link ItemArmor#getArmorSlot()} value --
 * Head=0, Chest=1, Hands=2, Legs=3), and {@link #ensureArmorEquipped} reconciles the entity's real
 * {@code InventoryComponent.Armor} container against it. Safe to call unconditionally: it is a
 * no-op for an NPC nobody has ever given armour, and for a slot that already holds the right item.
 */
public class NPCArmorHelper {

    /** Number of armour slots (Head, Chest, Hands, Legs) -- matches ItemArmorSlot.VALUES.length. */
    public static final int SLOT_COUNT = 4;

    private NPCArmorHelper() {}

    /**
     * The armour slot a held item equips into (0=Head, 1=Chest, 2=Hands, 3=Legs), or -1 when the
     * item is not armour at all.
     * <p>
     * Reads the item's own asset data ({@code Item.getArmor()}) rather than guessing from the
     * item id's spelling -- {@code CombatStatsRegistry}'s armour entries happen to follow a
     * {@code _Head}/{@code _Chest}/{@code _Hands}/{@code _Legs} suffix convention, but that is
     * RuneCore's own naming choice, not something this should depend on.
     */
    public static int armorSlotFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        Item item = stack.getItem();
        ItemArmor armor = item != null ? item.getArmor() : null;
        return armor != null ? armor.getArmorSlot().getValue() : -1;
    }

    /**
     * Records {@code itemId} in the given slot, equips it into the NPC's real armour container,
     * and opts the NPC into RuneCore's dynamic combat stats (so the armour actually mitigates
     * damage instead of just being visible). Call this once, at the moment a player hands the NPC
     * a piece of armour.
     */
    public static void giveArmor(Ref<EntityStore> ref, SimNPCComponent npc, Store<EntityStore> store,
                                  int slot, String itemId) {
        if (npc.armorItemIds == null) npc.armorItemIds = new String[SLOT_COUNT];
        npc.armorItemIds[slot] = itemId;
        ensureArmorEquipped(ref, npc, store);
        RuneAttributes.track(npc.entityId);
    }

    /**
     * Reconciles the NPC's real {@code InventoryComponent.Armor} container against
     * {@code npc.armorItemIds}. Called every tick from {@code SimTaleTickSystem} (staggered, same
     * as the periodic save below it) so armour survives whatever recreates an NPC's components --
     * exactly the same defensive reasoning {@link NPCGuardHelper#handleGuardLogic} has for
     * re-equipping the held weapon every pass instead of trusting it to stick once.
     */
    public static void ensureArmorEquipped(Ref<EntityStore> ref, SimNPCComponent npc, Store<EntityStore> store) {
        if (npc.armorItemIds == null) return;

        boolean anySet = false;
        for (String id : npc.armorItemIds) {
            if (id != null) { anySet = true; break; }
        }
        if (!anySet) return;

        InventoryComponent.Armor armor = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        if (armor == null) {
            armor = new InventoryComponent.Armor(InventoryComponent.DEFAULT_ARMOR_CAPACITY);
            store.putComponent(ref, InventoryComponent.Armor.getComponentType(), armor);
        }

        ItemContainer inventory = armor.getInventory();
        for (int i = 0; i < npc.armorItemIds.length && i < inventory.getCapacity(); i++) {
            String itemId = npc.armorItemIds[i];
            if (itemId == null) continue;

            short slot = (short) i;
            ItemStack current = inventory.getItemStack(slot);
            if (current != null && !current.isEmpty() && itemId.equals(current.getItemId())) continue;

            inventory.setItemStackForSlot(slot, new ItemStack(itemId, 1));
        }
    }
}
