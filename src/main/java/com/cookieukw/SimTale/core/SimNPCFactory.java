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
import com.hypixel.hytale.server.core.inventory.InventoryComponent;

/**
 * Factory for creating SimTale NPCs with proper models and components.
 */
public class SimNPCFactory {

    /**
     * REAPER spawns on the "SimTale_Human_Male" role (behavior only — the role's own "Appearance"
     * is a normal human) and gets this model applied on top of it. Exposed so
     * {@code RoutineAISystem} can reassert it if the role's own appearance system stomps it back
     * to human on a later tick.
     *
     * <p>This is a <em>ModelAsset id</em>, not a file path. It was
     * {@code "Common/NPC/Void/Necromancer_Void/Models/Model.blockymodel"} — the raw geometry —
     * which is why the Reaper still looked like a villager wearing a costume.
     *
     * <p>{@code Server/Models/Void/Necromancer_Void.json} is the asset that actually describes
     * her: it points at that same {@code .blockymodel}, but also carries the texture, the four
     * default attachments (braids, bracers, chest, skull head) and every animation set. Naming the
     * geometry directly skips all of it — no texture, no attachments — and leaves the human role's
     * cosmetics layered on top, which is exactly what the client was complaining about with
     * {@code Couldn't find attachment target: R-Ear-EarAccessory}.
     *
     * <p>The id is the file name without extension or namespace, the same rule the item assets
     * follow. Confirmed against {@code Server/Models/Undead/Skeleton.json}, whose id
     * {@code /simtale debugnear} reports as exactly {@code Skeleton}.
     */
    public static final String REAPER_MODEL_ASSET_ID = "Necromancer_Void";

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
            name = "Grim Reaper";
            PersistentModel pm = new PersistentModel(
                new ModelReference(REAPER_MODEL_ASSET_ID, 1.0f, new HashMap<>())
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

        // NPCPlugin.spawnNPC attaches a Storage component backed by EmptyItemContainer
        // (capacity 0) by default — fine for vanilla NPCs, but ours need to actually carry
        // seeds, tools and harvested goods (fish, wood, ore, meat, crops).
        accessor.putComponent(ref, InventoryComponent.Storage.getComponentType(), new InventoryComponent.Storage((short) 20));

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

    /**
     * Rewrites the two labels the world shows above an NPC.
     * <p>
     * Spawning always stamps them with whatever name the factory rolled, so anything that renames
     * an NPC after the fact has to redo both — the floating nameplate and the display name — or the
     * body walks around introducing itself as someone else. Revival hits this: the record is poured
     * in after the body exists.
     */
    public static void refreshNameplate(Store<EntityStore> store, Ref<EntityStore> ref, String name) {
        if (store == null || ref == null || !ref.isValid() || name == null) return;
        store.putComponent(ref, PersistentDisplayName.getComponentType(),
                new PersistentDisplayName(Message.raw(name)));
        store.putComponent(ref, Nameplate.getComponentType(), new Nameplate(name));
    }
}
