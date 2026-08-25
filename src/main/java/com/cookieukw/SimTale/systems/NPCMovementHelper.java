package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import org.joml.Vector3d;
import org.joml.Vector3i;
import com.cookieukw.SimTale.core.SimLog;

import com.hypixel.hytale.server.npc.role.support.StateSupport;
import java.util.Objects;

public class NPCMovementHelper {
    private static final SimLog LOGGER = SimLog.forClass(NPCMovementHelper.class);
    private static final double LEASH_UPDATE_THRESHOLD_SQ = 0.5 * 0.5;
    /**
     * State the NPC roles enter to walk to their leash point.
     * <p>
     * This must match a state the role actually declares. It used to be {@code "Moving"},
     * which no SimTale role defined — every call logged
     * {@code "State 'Moving.null' ... does not exist and was set by an external call"} and the
     * NPC kept its idle animation while the leash dragged it around ("sliding on ice").
     * <p>
     * {@code ReturnHome} is vanilla Hytale's own name for this behaviour — see the state of the
     * same name in {@code _Core/Templates/Template_Intelligent.json}, which pairs a
     * {@code Leash} sensor with a pathfinding {@code Seek}. The SimTale roles gained an
     * equivalent block via {@code scripts/add_returnhome_state.py}; renaming this constant
     * without re-running that script will break NPC movement again.
     */
    public static final String STATE_MOVING = "ReturnHome";

    public static void moveTo(Ref<EntityStore> ref, RoutineAIComponent ai, World world, Vector3d targetPos) {
        boolean needsUpdate;

        if (ai.lastLeashPos == null) {
            needsUpdate = true;
        } else {
            double d2 = ai.lastLeashPos.distanceSquared(targetPos);
            needsUpdate = d2 > LEASH_UPDATE_THRESHOLD_SQ;
        }

        if (needsUpdate) {
            // Without the NPC's name here, this line is useless for telling two NPCs' movement
            // apart in a busy log — which one is dragging around near (X,Y,Z) was previously a
            // guessing game whenever more than one NPC was active at once.
            SimNPCComponent npc = ref.getStore().getComponent(ref, SimTale.SIM_NPC_COMPONENT_TYPE);
            // currentTask alongside the name: nine different call sites across five helper
            // classes all funnel through here, and a moveTo firing for a task the caller wasn't
            // expecting (e.g. an interrupt nobody logged) was previously invisible.
            LOGGER.debug("[SimTale] moveTo({}, task={}) updating leash point to ({},{},{})",
                    npc != null ? npc.name : "?", ai.currentTask, targetPos.x, targetPos.y, targetPos.z);
            ai.lastLeashPos = new Vector3d(targetPos);
            ai.lastLeashTick = world.getTick();
            
            NPCEntity npcEntity = ref.getStore().getComponent(ref, Objects.requireNonNull(NPCEntity.getComponentType()));
            if (npcEntity != null) {
                npcEntity.setLeashPoint(new Vector3d(targetPos.x, targetPos.y, targetPos.z));
                StateSupport stateSupport = StateSupport.get(ref, ref.getStore());
                if (stateSupport != null) {
                    stateSupport.setState(ref, STATE_MOVING, null, ref.getStore());
                }
            }
        }
    }

    /**
     * Prende o leash numa posicao explicita, sem tocar no estado do role.
     *
     * <p>Existe por causa de uma diferenca sutil em relacao ao {@link #clearMoveTarget}: aquele
     * fixa o leash onde o NPC <em>esta</em> e forca o estado {@code Idle}. Isso serve para quem
     * acabou de chegar a um destino a pe, mas nao para quem vai ser teleportado logo em seguida
     * — e nem para quem precisa ficar num estado proprio, como {@code Sleep}.
     *
     * <p>O caso concreto e a cama. O NPC caminha ate o bloco <em>ao lado</em> da cama, o
     * clearMoveTarget prende o leash ali, e so entao ele e teleportado para cima do colchao.
     * O leash continuava apontando para o bloco vizinho, entao a propria IA do role puxava o NPC
     * de volta: ele escorregava da cama para o chao a noite inteira.
     */
    public static void pinLeashAt(Ref<EntityStore> npcRef, RoutineAIComponent ai, Vector3d pos) {
        if (npcRef == null || pos == null) return;

        if (ai != null) {
            // Mantido em sincronia com o leash real, senao o proximo moveTo compara com um valor
            // antigo e pode concluir que nao precisa atualizar nada.
            ai.lastLeashPos = new Vector3d(pos);
        }

        NPCEntity npcEntity = npcRef.getStore().getComponent(npcRef, Objects.requireNonNull(NPCEntity.getComponentType()));
        if (npcEntity != null) {
            npcEntity.setLeashPoint(new Vector3d(pos));
        }
    }

    public static void clearMoveTarget(Ref<EntityStore> npcRef, RoutineAIComponent ai) {
        ai.lastLeashPos = null;
        ai.lastLeashTick = 0;

        NPCEntity npcEntity = npcRef.getStore().getComponent(npcRef, Objects.requireNonNull(NPCEntity.getComponentType()));
        if (npcEntity != null) {
            TransformComponent transform = npcRef.getStore().getComponent(npcRef, TransformComponent.getComponentType());
            if (transform != null) {
                npcEntity.setLeashPoint(new Vector3d(transform.getPosition().x, transform.getPosition().y, transform.getPosition().z));
            }
            StateSupport stateSupport = StateSupport.get(npcRef, npcRef.getStore());
            if (stateSupport != null) {
                stateSupport.setState(npcRef, "Idle", null, npcRef.getStore());
            }
        }
    }

    public static void playAnim(Ref<EntityStore> ref, String anim, String name, Store<EntityStore> store) {
        playAnim(ref, AnimationSlot.Action, anim, name, store);
    }

    public static void playAnim(Ref<EntityStore> ref, AnimationSlot slot, String anim, String name, Store<EntityStore> store) {
        AnimationUtils.playAnimation(ref, slot, anim, name, store);
    }

    /**
     * Versao sem {@link CommandBuffer}, para uso fora de um sistema de tick (comandos).
     * <p>
     * {@code store.getComponent} devolve a instancia viva, entao mexer nos campos ja altera o
     * componente. O {@code replaceComponent} da versao completa existe para sinalizar a
     * atualizacao, nao para a escrita em si — e comando nao tem CommandBuffer para chamar.
     */
    public static void setSleepingState(Ref<EntityStore> ref, Store<EntityStore> store, boolean sleeping) {
        setSleepingState(ref, store, null, sleeping);
    }

    public static void setSleepingState(Ref<EntityStore> ref, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, boolean sleeping) {
        MovementStatesComponent msc = store.getComponent(ref, MovementStatesComponent.getComponentType());
        if (msc == null) return;
        MovementStates ms = msc.getMovementStates();
        ms.idle = true;
        ms.horizontalIdle = true;
        ms.walking = false;
        ms.running = false;
        ms.sprinting = false;
        ms.jumping = false;
        ms.falling = false;
        ms.mantling = false;
        ms.sliding = false;
        // sitting e uma flag SEPARADA de sleeping, e ficava intocada aqui. Se qualquer coisa a
        // tiver ligado antes, ela permanecia ligada durante o sono — e o corpo era desenhado
        // sentado em vez de deitado. Vale zerar em ambos os sentidos: ao dormir e ao acordar,
        // nunca queremos a NPC sentada.
        //
        // Nao e so pose: ModelSystems$UpdateMovementStateBoundingBox deriva a caixa de colisao
        // dessas flags, entao sitting/sleeping errados dao a hitbox errada — que e justamente a
        // origem do empurrao lateral na cama.
        ms.sitting = false;
        ms.mounting = sleeping;
        ms.sleeping = sleeping;
        // Nulo quando chamado de um comando (ver a sobrecarga acima). Os campos ja foram
        // alterados na instancia viva; o replaceComponent apenas sinaliza a mudanca.
        if (commandBuffer != null) {
            commandBuffer.replaceComponent(ref, MovementStatesComponent.getComponentType(), msc);
        }
    }

    /**
     * A spot beside the bed the NPC can stand on, or null when there is none.
     *
     * <p>Only the four orthogonal neighbours of the anchor used to be considered. A bed spans six
     * blocks, so the neighbours along the bed's own axis are its filler blocks and never standable
     * — which leaves very few options, and a bed pushed against a wall can leave none at all.
     * Diagonals and the row one block further out are searched too.
     */
    public static Vector3i findStandableBeside(Vector3i bedPos, TransformComponent transform, World world) {
        Vector3i[] candidates = {
            // Orthogonal neighbours first: they read as "getting out of bed" rather than a hop.
            new Vector3i(bedPos.x + 1, bedPos.y, bedPos.z),
            new Vector3i(bedPos.x - 1, bedPos.y, bedPos.z),
            new Vector3i(bedPos.x, bedPos.y, bedPos.z + 1),
            new Vector3i(bedPos.x, bedPos.y, bedPos.z - 1),
            // Diagonals.
            new Vector3i(bedPos.x + 1, bedPos.y, bedPos.z + 1),
            new Vector3i(bedPos.x + 1, bedPos.y, bedPos.z - 1),
            new Vector3i(bedPos.x - 1, bedPos.y, bedPos.z + 1),
            new Vector3i(bedPos.x - 1, bedPos.y, bedPos.z - 1),
            // Two blocks out, to clear the far end of the bed itself.
            new Vector3i(bedPos.x + 2, bedPos.y, bedPos.z),
            new Vector3i(bedPos.x - 2, bedPos.y, bedPos.z),
            new Vector3i(bedPos.x, bedPos.y, bedPos.z + 2),
            new Vector3i(bedPos.x, bedPos.y, bedPos.z - 2)
        };

        Vector3d npcPos = transform.getPosition();
        Vector3i best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Vector3i c : candidates) {
            if (!isStandable(c, world)) continue;
            double dx = (c.x + 0.5) - npcPos.x;
            double dz = (c.z + 0.5) - npcPos.z;
            double d2 = dx * dx + dz * dz;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                best = c;
            }
        }

        return best;
    }

    /**
     * Walk target for reaching the bed. Falls back to the bed itself, which is fine here: this is
     * only a destination to walk toward, and arriving on top of the bed still triggers mounting.
     *
     * <p>Do NOT reuse this for the wake-up teleport — see {@link #findStandableBeside}.
     */
    public static Vector3i getBedApproachPosition(Vector3i bedPos, TransformComponent transform, World world) {
        Vector3i standable = findStandableBeside(bedPos, transform, world);
        return standable != null ? standable : bedPos;
    }

    /**
     * Whether a straight line from {@code from} to the centre of {@code target} is free of solid
     * blocks.
     *
     * <p>Exists because "is the NPC close enough to get into bed?" used to be a flat XZ distance
     * test. An NPC standing outside the house, with nothing but a wall between it and the bed,
     * passed that test and mounted straight through the wall — it looked like the NPC vanished.
     *
     * <p>Deliberately cheap: it samples the segment rather than doing a proper voxel traversal, and
     * ignores the endpoints. It only has to reject a wall, not be exact.
     */
    public static boolean hasClearPath(World world, Vector3d from, Vector3i target) {
        if (world == null) return false;

        double tx = target.x + 0.5;
        double ty = target.y + 0.5;
        double tz = target.z + 0.5;

        double dx = tx - from.x;
        double dy = ty - from.y;
        double dz = tz - from.z;

        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) Math.ceil(distance * 2);
        if (steps <= 1) return true;

        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            int bx = (int) Math.floor(from.x + dx * t);
            int by = (int) Math.floor(from.y + dy * t);
            int bz = (int) Math.floor(from.z + dz * t);

            if (bx == target.x && by == target.y && bz == target.z) continue;

            BlockType block = world.getBlockType(bx, by, bz);
            if (block == null || block.getId() == null) continue;
            String id = block.getId();
            if (id.equalsIgnoreCase("Empty")) continue;
            // Furniture on the way is not a wall; the bed itself is the destination.
            if (BedRegistry.isBedId(id)) continue;

            return false;
        }
        return true;
    }

    private static boolean isAir(BlockType type) {
        if (type == null || type.getId() == null) return true;
        String id = type.getId();
        return id.equalsIgnoreCase("Empty") || id.equalsIgnoreCase("Air");
    }

    public static boolean isStandable(Vector3i pos, World world) {
        BlockType atPos = world.getBlockType(pos.x, pos.y, pos.z);
        if (!isAir(atPos)) {
            return false;
        }

        BlockType above = world.getBlockType(pos.x, pos.y + 1, pos.z);
        if (!isAir(above)) {
            return false;
        }

        BlockType below = world.getBlockType(pos.x, pos.y - 1, pos.z);
        return !isAir(below);
    }
}
