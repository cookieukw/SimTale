package com.cookieukw.SimTale.core.lifecycle;

import com.cookie.caskara.Caskara;
import com.cookie.runecore.api.EffectHelper;
import com.cookie.runecore.api.StatHelper;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Child;
import com.cookieukw.SimTale.core.NeedsHelper;
import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.core.SimNPCNameGenerator;
import com.cookieukw.SimTale.core.SimPlayerComponent;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.db.SimPlayerPersistence;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;

import java.util.Objects;
import java.util.UUID;

public class PregnancyManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static boolean startPregnancy(SimNPCComponent mother, UUID fatherId, long worldTick) {
        if (mother.gender != Gender.FEMALE) {
            LOGGER.atWarning().log("SimTale: Tentativa de gravidez em NPC não-feminino: " + mother.name);
            return false;
        }

        if (mother.entityRef != null) {
            NPCEntity npcEntity = mother.entityRef.getStore().getComponent(mother.entityRef, Objects.requireNonNull(NPCEntity.getComponentType()));
            if (npcEntity != null && npcEntity.getRoleName() != null && npcEntity.getRoleName().toLowerCase().contains("child")) {
                LOGGER.atWarning().log("SimTale: Gravidez cancelada. A NPC " + mother.name + " e uma criança!");
                return false;
            }
        }

        for (GrowthComponent child : LifecycleState.ACTIVE_CHILDREN) {
            if (mother.entityId != null && mother.entityId.equals(child.childId) && !child.isAdult()) {
                LOGGER.atWarning().log("SimTale: Gravidez cancelada. A NPC " + mother.name + " e um filho em crescimento!");
                return false;
            }
        }

        if (mother.pregnancy != null && mother.pregnancy.pregnant) {
            LOGGER.atInfo().log("SimTale: " + mother.name + " já está grávida.");
            return false;
        }

        if (!mother.family.isMarried || !fatherId.equals(mother.family.spouseId)) {
            LOGGER.atInfo().log("SimTale: " + mother.name + " não é casada com o pai.");
            return false;
        }

        Relationship rel = mother.getRelationship(fatherId);
        if (rel.romance < 50) {
            LOGGER.atInfo().log("SimTale: Romance insuficiente para gravidez (" + rel.romance + "/50)");
            return false;
        }

        if (mother.pregnancy == null) {
            mother.pregnancy = new PregnancyComponent();
        }
        mother.pregnancy.start(fatherId, worldTick);

        LOGGER.atInfo().log("SimTale: " + mother.name + " está grávida! Pai: " + fatherId);
        return true;
    }

    public static GrowthComponent birthBaby(SimNPCComponent mother, Store<EntityStore> store, long worldTick) {
        if (mother.pregnancy == null || !mother.pregnancy.pregnant) {
            return null;
        }

        UUID fatherId = mother.pregnancy.fatherId;
        SimNPCComponent father = LifecycleUtils.findNPCById(fatherId);
        GeneticsData motherGenetics = LifecycleUtils.getOrCreateGenetics(mother);
        GeneticsData fatherGenetics = father != null ? LifecycleUtils.getOrCreateGenetics(father) : new GeneticsData();

        GeneticsData childGenetics = GeneticsData.combine(motherGenetics, fatherGenetics);

        Gender childGender = Math.random() < 0.5 ? Gender.MALE : Gender.FEMALE;

        String fatherName = father != null ? father.name : "";
        String childSurname = GeneticsData.inheritSurname(mother.name, fatherName);

        /* generateUniqueFirstName instead of generate() plus a substring: the old code built a
        full name and threw the surname away, which meant the uniqueness check inside
        generate() was being run against a string that was then discarded. It also ran before
        the surname existed, so no uniqueness check ever applied here at all -- two unrelated
        children born around the same time could (and did) end up sharing a full name.
        */
        String childFirstName = SimNPCNameGenerator.generateUniqueFirstName(childSurname);

        GrowthComponent child = new GrowthComponent(
            mother.entityId,
            fatherId,
            worldTick,
            childGender,
            childGenetics,
            childFirstName,
            childSurname
        );

        SimNPCFactory.NPCType childType = childGender == Gender.MALE
            ? SimNPCFactory.NPCType.CHILD_MALE
            : SimNPCFactory.NPCType.CHILD_FEMALE;

        try {
            Vector3d spawnPos = LifecycleUtils.getEntityPosition(mother, store);
            if (spawnPos == null) {
                spawnPos = new Vector3d(0, 64, 0); // fallback
            }

            Ref<EntityStore> childRef = SimNPCFactory.spawnNPC(store, spawnPos, childType);
            UUIDComponent uuidComp = store.getComponent(childRef, UUIDComponent.getComponentType());
            if (uuidComp != null) {
                child.childId = uuidComp.getUuid();
            }

            store.removeEntity(childRef, RemoveReason.REMOVE);

            Child familyChild = new Child(child.childId, child.getFullName());
            mother.family.children.add(familyChild);

            if (father != null) {
                Child fatherFamilyChild = new Child(child.childId, child.getFullName());
                father.family.children.add(fatherFamilyChild);
            }

            child.pickUp(mother.entityId);

            LifecycleState.ACTIVE_CHILDREN.add(child);
            Caskara.save("child_" + child.childId.toString(), child);
            BabyCareManager.initializeForChild(child);
            /* initializeForChild only persists BabyCareData; the in-memory cache that drives the
            inventory sync needs the same starting holder or the "Baby" item never appears until
            a world/server restart forces loadCache() to read it back from disk.
            */
            BabyCareManager.addCarriedBaby(mother.entityId, child.childId);

            LOGGER.atInfo().log("SimTale: Nasceu " + child.getFullName() + " ("
                + childGender.getDisplayName() + ") — filho(a) de " + mother.name);

        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: Falha ao spawnar bebê: " + e.getMessage());
            return null;
        }

        mother.pregnancy.reset();

        if (mother.entityRef != null) {
            EffectHelper.modifyMovement(mother.entityRef, s -> s.baseSpeed = EffectHelper.DEFAULT_SPEED);
            StatHelper.subtractHealth(mother.entityRef, 50.0f);
        }

        SimNPCPersistence.saveNPC(mother);

        return child;
    }

    public static void applyPregnancyBehavior(SimNPCComponent mother) {
        if (mother.pregnancy == null || !mother.pregnancy.pregnant) return;
        float mult = 1.0f + (mother.pregnancy.trimester * 0.3f);
        NeedsHelper.setNeed(null, mother.entityRef, NeedsHelper.HUNGER_ID, Math.max(0, NeedsHelper.getNeed(null, mother.entityRef, NeedsHelper.HUNGER_ID) - 0.0001f * (mult - 1.0f)));
        NeedsHelper.setNeed(null, mother.entityRef, NeedsHelper.ENERGY_ID, Math.max(0, NeedsHelper.getNeed(null, mother.entityRef, NeedsHelper.ENERGY_ID) - 0.0002f * (mult - 1.0f)));
    }

    /**
     * Speed for a given trimester, as an absolute value rather than a subtraction.
     *
     * <p>The old version read {@code s.baseSpeed} and subtracted from it, and the callers re-run
     * this every 100 ticks. Each pass therefore subtracted again from the already-reduced value:
     * 5.5 → 4.0 → 2.5 → 1.0 in about fifteen seconds, where it stuck on the floor. Second and
     * third trimester ended up identical, both at a crawl, which is why the debuff read as "the
     * slow is broken" rather than "the slow is mild".
     *
     * <p>Reading the live value was wrong in principle too: anything else that touches movement —
     * a potion, a mount, another mod — became part of the pregnancy formula.
     */
    private static float speedForTrimester(int trimester) {
        if (trimester == 3) return EffectHelper.DEFAULT_SPEED - 3.0f;
        if (trimester == 2) return EffectHelper.DEFAULT_SPEED - 1.5f;
        return EffectHelper.DEFAULT_SPEED;
    }

    public static void applyPregnancySpeedDebuff(Ref<EntityStore> entityRef, PregnancyComponent pregnancy) {
        if (entityRef == null || pregnancy == null) return;

        final float target = pregnancy.pregnant
                ? speedForTrimester(pregnancy.trimester)
                : EffectHelper.DEFAULT_SPEED;
        EffectHelper.modifyMovement(entityRef, s -> s.baseSpeed = target);
    }

    /**
     * AUDITORIA.md #4.1 workaround for {@link com.cookieukw.SimTale.systems.NPCMovementHelper}.
     *
     * <p>{@link #applyPregnancySpeedDebuff} above only ever works on a player: it goes through
     * {@code EffectHelper.modifyMovement}, which reads the native {@code MovementManager}
     * component — and that component is assigned to players only
     * ({@code PlayerMovementManagerSystems$AssignmentSystem}, confirmed via bytecode). An NPC
     * never has it, so the call is a silent no-op for every pregnant NPC. There is also no
     * public setter anywhere for an NPC's own movement speed multiplier
     * ({@code NPCEntity} only exposes a getter and a cache-invalidator).
     *
     * <p>These two methods back a purely visual approximation instead: on a "paused" tick,
     * {@code NPCMovementHelper.moveTo} re-pins the NPC's leash to her own current position rather
     * than the real destination, which reads in-game as a stutter/waddle rather than a smooth
     * speed change. It changes nothing about actual pathing speed and has not been confirmed in
     * a live game — see AUDITORIA.md section 4.1 for the full writeup.
     */
    public static final int THROTTLE_CYCLE_TICKS = 20;

    private static int pauseTicksForTrimester(int trimester) {
        if (trimester == 3) return 10; // paused half of every cycle
        if (trimester == 2) return 4;  // paused a fifth of every cycle
        return 0;                      // trimester 1 (and non-pregnant): no debuff, matches speedForTrimester
    }

    public static boolean isPausedTick(PregnancyComponent pregnancy, long worldTick) {
        if (pregnancy == null || !pregnancy.pregnant) return false;
        int pauseTicks = pauseTicksForTrimester(pregnancy.trimester);
        if (pauseTicks <= 0) return false;
        return (worldTick % THROTTLE_CYCLE_TICKS) < pauseTicks;
    }

    public static void applyPlayerPregnancyBehavior(Ref<EntityStore> playerRef, SimPlayerComponent playerComp) {
        if (playerComp.pregnancy == null || !playerComp.pregnancy.pregnant) return;

        float mult = 1.0f + (playerComp.pregnancy.trimester * 0.3f);
        
        Store<EntityStore> store = playerRef.getStore();
        EntityStatMap statMap = 
            store.getComponent(playerRef, EntityStatMap.getComponentType());
            
        if (statMap != null) {
            EntityStatValue staminaVal = 
                statMap.get(DefaultEntityStatTypes.getStamina());
                
            if (staminaVal != null) {
                float current = staminaVal.get();
                float drainAmount = 0.05f * mult; 
                float newValue = Math.max(0, current - drainAmount);
                statMap.setStatValue(DefaultEntityStatTypes.getStamina(), newValue);
            }
        }
    }

    public static void birthPlayerBaby(Ref<EntityStore> playerRef, SimPlayerComponent playerComp, Store<EntityStore> store, long worldTick) {
        if (playerComp.pregnancy == null || !playerComp.pregnancy.pregnant) return;

        UUID fatherId = playerComp.pregnancy.fatherId;
        Gender childGender = Math.random() < 0.5 ? Gender.MALE : Gender.FEMALE;

        /* The family name comes from the NPC parent, not from a constant.

        This was hardcoded to "SimTale", so every child a player ever had shared a surname with
        every other player's children and with nobody they were actually related to — the mod's
        own name showing up as a family name across the village. The player has no surname to
        pass on, so the NPC partner's line is the only real one in the pair, and inheriting it
        puts player children on the same footing as everyone else's.
        */
        String partnerSurname = "";
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc != null && npc.entityId != null && npc.entityId.equals(fatherId)) {
                partnerSurname = SimNPCNameGenerator.extractSurname(npc.name);
                break;
            }
        }
        /* No blending here, because there is only one line to inherit from — blending a name with
        itself is either a no-op or noise.
        */
        String childSurname = partnerSurname.isEmpty()
                ? SimNPCNameGenerator.generateSurname()
                : partnerSurname;

        /* See the NPC-NPC birth flow above for why this checks uniqueness against the surname
        instead of calling generateFirstName() directly.
        */
        String childFirstName = SimNPCNameGenerator.generateUniqueFirstName(childSurname);

        GeneticsData childGenetics = GeneticsData.combine(new GeneticsData(), new GeneticsData());

        GrowthComponent child = new GrowthComponent(
            playerComp.playerUuid,
            fatherId,
            worldTick,
            childGender,
            childGenetics,
            childFirstName,
            childSurname
        );

        try {
            Vector3d spawnPos = new Vector3d(0, 64, 0);
            TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
            if (transform != null) {
                spawnPos = transform.getPosition();
            }

            SimNPCFactory.NPCType childType = childGender == Gender.MALE
                ? SimNPCFactory.NPCType.CHILD_MALE
                : SimNPCFactory.NPCType.CHILD_FEMALE;

            Ref<EntityStore> childRef = SimNPCFactory.spawnNPC(store, spawnPos, childType);
            UUIDComponent uuidComp = store.getComponent(childRef, UUIDComponent.getComponentType());
            if (uuidComp != null) {
                child.childId = uuidComp.getUuid();
            }

            store.removeEntity(childRef, RemoveReason.REMOVE);

            if (fatherId != null) {
                for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
                    if (npc.entityId != null && npc.entityId.equals(fatherId)) {
                        Child fatherFamilyChild = new Child(child.childId, child.getFullName());
                        npc.family.children.add(fatherFamilyChild);
                        SimNPCPersistence.saveNPC(npc);
                        break;
                    }
                }
            }

            child.pickUp(playerComp.playerUuid);
            LifecycleState.ACTIVE_CHILDREN.add(child);
            Caskara.save("child_" + child.childId.toString(), child);
            BabyCareManager.initializeForChild(child);

            EffectHelper.modifyMovement(playerRef, s -> s.baseSpeed = EffectHelper.DEFAULT_SPEED);
            StatHelper.subtractHealth(playerRef, 50.0f);

            ItemStack babyItem = new ItemStack("Baby", 1).withMetadata("childId", Codec.STRING, child.childId.toString());
            CombinedItemContainer combinedInventory = InventoryComponent.getCombined(playerRef.getStore(), playerRef, InventoryComponent.HOTBAR_FIRST);
            ItemStackTransaction transaction = combinedInventory.addItemStack(babyItem);
            ItemStack remainder = transaction.getRemainder();
            if (remainder != null && !remainder.isEmpty()) {
                ItemUtils.dropItem(playerRef, remainder, playerRef.getStore());
            }

            LOGGER.atInfo().log("SimTale: Player " + playerComp.playerUuid + " deu a luz a " + child.getFullName());

        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: Falha ao nascer o bebe do player: " + e.getMessage());
        }

        playerComp.pregnancy.reset();
        SimPlayerPersistence.savePlayer(playerComp);
    }
}
