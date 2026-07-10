package com.cookieukw.SimTale.core.lifecycle;

import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.UUID;

public class MotherAIManager {

    public static void tickMotherAI(SimNPCComponent parent, GrowthComponent child, long worldTick) {
        if (child == null || parent == null || parent.entityId == null || child.childId == null) return;

        BabyCareData careData = BabyCareManager.load(child.childId);
        if (careData == null) return;
        
        String parentIdStr = parent.entityId.toString();

        if (child.stage == GrowthStage.BABY) {
            handleBabyAI(parent, child, careData, parentIdStr);
        } else if (child.stage == GrowthStage.TODDLER) {
            handleToddlerAI(parent, child, careData, parentIdStr);
        }
    }

    private static void handleBabyAI(SimNPCComponent parent, GrowthComponent baby, BabyCareData careData, String parentIdStr) {
        UUID heldBabyId = BabyCareManager.NPC_CARRIED_BABIES.get(parent.entityId);
        boolean isHolding = heldBabyId != null && heldBabyId.equals(baby.childId);
        boolean isTurnOwner = parentIdStr.equals(careData.currentTurnOwnerId);
        
        if (!isHolding && isTurnOwner) {
            long now = System.currentTimeMillis();
            if (now - careData.lastInteractionTime > 15000) { 
                careData.currentHolderId = parentIdStr;
                careData.lastInteractionTime = now;
                BabyCareManager.NPC_CARRIED_BABIES.put(parent.entityId, baby.childId);
                BabyCareManager.save(careData);
                
                broadcastLocalMessage(parent, Message.translation("npc-dialogues.chat.baby.pickup").param("parent", parent.name).param("baby", baby.getFullName()));
            }
            return;
        }

        if (isHolding && isTurnOwner && baby.needsCare()) {
            if (Math.random() > 0.05) return;
            
            BabyNeeds needs = baby.babyNeeds;
            if (needs == null) return;
            
            boolean acted = false;
            
            if (needs.hunger < 50f) {
                needs.feed(30f);
                broadcastLocalMessage(parent, Message.translation("npc-dialogues.chat.baby.feed").param("parent", parent.name).param("baby", baby.getFullName()));
                acted = true;
            } else if (needs.affection < 50f) {
                needs.showAffection(30f);
                broadcastLocalMessage(parent, Message.translation("npc-dialogues.chat.baby.play").param("parent", parent.name).param("baby", baby.getFullName()));
                acted = true;
            } else if (needs.health < 50f) {
                needs.heal(30f);
                broadcastLocalMessage(parent, Message.translation("npc-dialogues.chat.baby.heal").param("parent", parent.name).param("baby", baby.getFullName()));
                acted = true;
            }
            
            if (acted) {
                careData.lastInteractionTime = System.currentTimeMillis();
                BabyCareManager.save(careData);
                Caskara.save("child_" + baby.childId.toString(), baby);
            }
        }
    }

    private static void handleToddlerAI(SimNPCComponent parent, GrowthComponent toddler, BabyCareData careData, String parentIdStr) {
        boolean isTurnOwner = parentIdStr.equals(careData.currentTurnOwnerId);
        
        if (isTurnOwner && toddler.needsCare()) {
            if (Math.random() > 0.05) return;
            
            Ref<EntityStore> parentRef = LifecycleUtils.getEntityRef(parent.entityId);
            Ref<EntityStore> toddlerRef = LifecycleUtils.getEntityRef(toddler.childId);
            
            if (parentRef == null || toddlerRef == null) return;
            
            TransformComponent pTrans = parentRef.getStore().getComponent(parentRef, TransformComponent.getComponentType());
            TransformComponent tTrans = toddlerRef.getStore().getComponent(toddlerRef, TransformComponent.getComponentType());
            
            if (pTrans == null || tTrans == null) return;
            
            double distSq = pTrans.getPosition().distanceSquared(tTrans.getPosition());
            if (distSq > 25.0) {
                return;
            }
            
            BabyNeeds needs = toddler.babyNeeds;
            if (needs == null) return;
            
            boolean acted = false;
            if (needs.hunger < 50f) {
                needs.feed(30f);
                broadcastLocalMessage(parent, Message.translation("npc-dialogues.chat.toddler.feed").param("parent", parent.name).param("baby", toddler.getFullName()));
                acted = true;
            } else if (needs.affection < 50f) {
                needs.showAffection(30f);
                broadcastLocalMessage(parent, Message.translation("npc-dialogues.chat.toddler.play").param("parent", parent.name).param("baby", toddler.getFullName()));
                acted = true;
            } else if (needs.health < 50f) {
                needs.heal(30f);
                broadcastLocalMessage(parent, Message.translation("npc-dialogues.chat.toddler.heal").param("parent", parent.name).param("baby", toddler.getFullName()));
                acted = true;
            }
            
            if (acted) {
                careData.lastInteractionTime = System.currentTimeMillis();
                BabyCareManager.save(careData);
                Caskara.save("child_" + toddler.childId.toString(), toddler);
            }
        }
    }

    private static void broadcastLocalMessage(SimNPCComponent npc, Message msg) {
        Ref<EntityStore> npcRef = LifecycleUtils.getEntityRef(npc.entityId);
        if (npcRef == null) return;
        
        TransformComponent npcTransform = npcRef.getStore().getComponent(npcRef, TransformComponent.getComponentType());
        if (npcTransform == null) return;
        
        Vector3d pos = npcTransform.getPosition();
        
        for (PlayerRef pr : Universe.get().getPlayers()) {
            Ref<EntityStore> playerRef = pr.getReference();
            if (playerRef != null) {
                TransformComponent pTrans = playerRef.getStore().getComponent(playerRef, TransformComponent.getComponentType());
                if (pTrans != null) {
                    if (pTrans.getPosition().distanceSquared(pos) <= 625.0) { // 25 blocks radius
                        pr.sendMessage(msg);
                    }
                }
            }
        }
    }
}
