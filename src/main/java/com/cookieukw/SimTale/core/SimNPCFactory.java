package com.cookieukw.SimTale.core;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.component.ComponentAccessor;
import org.joml.Vector3d;

import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.npc.systems.NewSpawnStartTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.Message;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.SimTale;
import com.hypixel.hytale.logger.HytaleLogger;
import it.unimi.dsi.fastutil.Pair;
import java.util.UUID;
import java.util.HashMap;

import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.asset.type.model.config.Model.ModelReference;

/**
 * Factory for creating SimTale NPCs with proper models and components.
 */
public class SimNPCFactory {

    public enum NPCType {
        SLOTHIAN("SimTale_Slothian"),
        TRORK("SimTale_Trork"),
        HUMAN_MALE("SimTale_Human_Male"),
        HUMAN_FEMALE("SimTale_Human_Female"),
        REAPER("SimTale_Human_Male"),
        CHILD_MALE("SimTale_Human_Child_Male"),
        CHILD_FEMALE("SimTale_Human_Child_Female");

        public final String roleId;

        NPCType(String roleId) {
            this.roleId = roleId;
        }
    }

    @SuppressWarnings("null")
    public static Ref<EntityStore> spawnNPC(Store<EntityStore> store, Vector3d position, NPCType type) {
        String roleId = type.roleId;
        
        if (type == NPCType.HUMAN_MALE) {
            int variant = 1 + (int)(Math.random() * 200);
            roleId = "SimTale_Human_Male_" + variant;
        } else if (type == NPCType.HUMAN_FEMALE) {
            int variant = 1 + (int)(Math.random() * 200);
            roleId = "SimTale_Human_Female_" + variant;
        } else if (type == NPCType.CHILD_MALE) {
            int variant = 1 + (int)(Math.random() * 200);
            roleId = "SimTale_Human_Child_Male_" + variant;
        } else if (type == NPCType.CHILD_FEMALE) {
            int variant = 1 + (int)(Math.random() * 200);
            roleId = "SimTale_Human_Child_Female_" + variant;
        }

        // 1. Spawn the NPC using the official Hytale NPC system
        Pair<Ref<EntityStore>, ?> result = NPCPlugin.get().spawnNPC(
            store, 
            roleId,
                null,
            position, 
            new Rotation3f()
        );

        if (result == null) {
            throw new NullPointerException("Spawn result is null");
        }

        Ref<EntityStore> ref = result.left();
        if (ref == null) {
            throw new NullPointerException("Spawned NPC entity reference is null");
        }
        
        ComponentAccessor<EntityStore> accessor = ref.getStore();
        
        // 2. Add SimTale custom components to the spawned entity
        UUIDComponent uuidComp = accessor.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) {
            throw new NullPointerException("NPC UUID component is null");
        }
        UUID entityId = uuidComp.getUuid();
        
        String name;
        if (type == NPCType.REAPER) {
            name = "Dona Morte";
            PersistentModel pm = new PersistentModel(
                new ModelReference("Common/NPC/Void/Necromancer_Void/Models/Model.blockymodel", 1.0f, new HashMap<>())
            );
            accessor.putComponent(ref, PersistentModel.getComponentType(), pm);
        } else {
            name = SimNPCNameGenerator.generate();
        }

        SimNPCComponent simComponent = new SimNPCComponent(entityId, name);
        simComponent.entityRef = ref;
        // Freshly rolled NPC: this object *is* the authoritative data, so it may save without
        // first reading from the database.
        simComponent.dataLoaded = true;
        simComponent.isReaper = (type == NPCType.REAPER);
        
        if (type == NPCType.HUMAN_MALE || type == NPCType.CHILD_MALE) {
            simComponent.gender = Gender.MALE;
        } else if (type == NPCType.HUMAN_FEMALE || type == NPCType.CHILD_FEMALE) {
            simComponent.gender = Gender.FEMALE;
        } else {
            simComponent.gender = Math.random() > 0.5 ? Gender.MALE : Gender.FEMALE;
        }

        // Try to load existing data if available
        if (type != NPCType.REAPER) {
            SimNPCPersistence.loadNPC(simComponent);
        }

        accessor.addComponent(ref, SimTale.SIM_NPC_COMPONENT_TYPE, simComponent);
        
        // 3. Add overhead name plate and make entity interactable
        accessor.putComponent(ref, PersistentDisplayName.getComponentType(), new PersistentDisplayName(Message.raw(simComponent.name)));
        accessor.putComponent(ref, Nameplate.getComponentType(), new Nameplate(simComponent.name));
        accessor.putComponent(ref, Interactable.getComponentType(), Interactable.INSTANCE);

        // 4. ACTIVATE AI: Queue for ticking
        // This is mandatory for NPCs spawned via API to start their AI logic.
        NewSpawnStartTickingSystem.queueNewSpawn(ref, store);
        
        // 5. Track for chat system
        SimTale.trackNpc(simComponent);

        // 6. Grava no banco IMEDIATAMENTE.
        //
        // Antes, um NPC recem-criado so existia em memoria. Ele so ganhava registro no banco se,
        // mais tarde, alguma rotina de IA por acaso chamasse saveNPC — dormir, comer, conversar.
        // Ate la ele estava vivo no mundo e invisivel para a persistencia.
        //
        // Duas consequencias, as duas observadas em jogo:
        //   1. Sair e voltar ao mundo fazia os NPCs recem-criados sumirem, porque nunca foram
        //      salvos.
        //   2. As contagens nao batiam: /simtale forcespawn quatro vezes seguido de clearall
        //      limpava "3 registros", porque os quatro novos nao tinham registro nenhum.
        //
        // O Reaper fica de fora de proposito: e uma entidade temporaria de cerimonia de morte,
        // nao um morador, e nem sequer passa por loadNPC acima.
        if (type != NPCType.REAPER) {
            try {
                SimNPCPersistence.saveNPC(simComponent);
            } catch (Exception e) {
                HytaleLogger.forEnclosingClass().atWarning()
                        .log("SimTale: NPC " + entityId + " criado mas nao persistido: " + e);
            }
        }

        return ref;
    }
}
