package com.cookieukw.SimTale.core.lifecycle;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.Personality;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.cookieukw.SimTale.core.SimNPCNameGenerator;
import com.cookieukw.SimTale.core.Trait;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Orquestrador central do sistema de gravidez e ciclo de vida.
 *
 * Responsável por:
 * - Iniciar gravidez
 * - Realizar nascimento (spawn do filho)
 * - Atualizar crescimento
 * - Aplicar genética
 * - Converter filho adulto em NPC independente
 *
 * Muitas funções são stubs para implementação futura.
 */
public class LifecycleManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Lista global de filhos ativos no mundo. */
    public static final List<GrowthComponent> ACTIVE_CHILDREN = new ArrayList<>();

    // =========================================================================
    // GRAVIDEZ
    // =========================================================================

    /**
     * Tenta iniciar uma gravidez em um NPC feminino.
     *
     * @param mother     componente da mãe (deve ser FEMALE)
     * @param fatherId   UUID do pai
     * @param worldTick  tick atual do mundo
     * @return true se a gravidez foi iniciada com sucesso
     */
    public static boolean startPregnancy(SimNPCComponent mother, UUID fatherId, long worldTick) {
        if (mother.gender != Gender.FEMALE) {
            LOGGER.atWarning().log("SimTale: Tentativa de gravidez em NPC não-feminino: " + mother.name);
            return false;
        }

        if (mother.pregnancy != null && mother.pregnancy.pregnant) {
            LOGGER.atInfo().log("SimTale: " + mother.name + " já está grávida.");
            return false;
        }

        // Verifica se está casada com o pai
        if (!mother.family.isMarried || !fatherId.equals(mother.family.spouseId)) {
            LOGGER.atInfo().log("SimTale: " + mother.name + " não é casada com o pai.");
            return false;
        }

        // Verifica romance mínimo
        Relationship rel = mother.getRelationship(fatherId);
        if (rel.romance < 50) {
            LOGGER.atInfo().log("SimTale: Romance insuficiente para gravidez (" + rel.romance + "/50)");
            return false;
        }

        // Iniciar!
        if (mother.pregnancy == null) {
            mother.pregnancy = new PregnancyComponent();
        }
        mother.pregnancy.start(fatherId, worldTick);

        LOGGER.atInfo().log("SimTale: " + mother.name + " está grávida! Pai: " + fatherId);
        return true;
    }

    // =========================================================================
    // NASCIMENTO
    // =========================================================================

    /**
     * Realiza o nascimento de um bebê.
     * Chamado pelo PregnancyTickSystem quando a gravidez expira.
     *
     * @param mother    componente da mãe
     * @param store     store de entidades
     * @param world     mundo atual
     * @param worldTick tick atual
     * @return o GrowthComponent do filho criado, ou null se falhou
     */
    public static GrowthComponent birthBaby(SimNPCComponent mother, Store<EntityStore> store,
                                             World world, long worldTick) {
        if (mother.pregnancy == null || !mother.pregnancy.pregnant) {
            return null;
        }

        UUID fatherId = mother.pregnancy.fatherId;

        // Encontrar o componente do pai para genética
        SimNPCComponent father = findNPCById(fatherId);
        GeneticsData motherGenetics = getOrCreateGenetics(mother);
        GeneticsData fatherGenetics = father != null ? getOrCreateGenetics(father) : new GeneticsData();

        // Combinar genética
        GeneticsData childGenetics = GeneticsData.combine(motherGenetics, fatherGenetics);

        // Determinar gênero aleatoriamente
        Gender childGender = Math.random() < 0.5 ? Gender.MALE : Gender.FEMALE;

        // Gerar nome
        String childFirstName = SimNPCNameGenerator.generate();
        // Se o nome gerado tem sobrenome, tiramos para usar herança
        if (childFirstName.contains(" ")) {
            childFirstName = childFirstName.substring(0, childFirstName.indexOf(' '));
        }

        // Herdar sobrenome
        String fatherName = father != null ? father.name : "";
        String childSurname = GeneticsData.inheritSurname(mother.name, fatherName);

        // Criar GrowthComponent
        GrowthComponent child = new GrowthComponent(
            mother.entityId,
            fatherId,
            worldTick,
            childGender,
            childGenetics,
            childFirstName,
            childSurname
        );

        // Spawnar a entidade do bebê no mundo
        // Por enquanto usa o modelo de criança existente
        SimNPCFactory.NPCType childType = childGender == Gender.MALE
            ? SimNPCFactory.NPCType.CHILD_MALE
            : SimNPCFactory.NPCType.CHILD_FEMALE;

        try {
            // Posição: ao lado da mãe
            Vector3d spawnPos = getEntityPosition(mother, store);
            if (spawnPos == null) {
                spawnPos = new Vector3d(0, 64, 0); // fallback
            }

            Ref<EntityStore> childRef = SimNPCFactory.spawnNPC(store, spawnPos, childType);

            // Registrar o filho na família da mãe
            com.cookieukw.SimTale.core.Child familyChild = new com.cookieukw.SimTale.core.Child(child.getFullName());
            mother.family.children.add(familyChild);

            // Registrar na família do pai também
            if (father != null) {
                com.cookieukw.SimTale.core.Child fatherFamilyChild = new com.cookieukw.SimTale.core.Child(child.getFullName());
                father.family.children.add(fatherFamilyChild);
            }

            // Mãe pega o bebê no colo automaticamente
            child.pickUp(mother.entityId);

            // Registrar na lista global
            ACTIVE_CHILDREN.add(child);

            LOGGER.atInfo().log("SimTale: Nasceu " + child.getFullName() + " ("
                + childGender.getDisplayName() + ") — filho(a) de " + mother.name);

        } catch (Exception e) {
            LOGGER.atWarning().log("SimTale: Falha ao spawnar bebê: " + e.getMessage());
            return null;
        }

        // Resetar gravidez
        mother.pregnancy.reset();

        return child;
    }

    // =========================================================================
    // CRESCIMENTO
    // =========================================================================

    /**
     * Tick de crescimento. Chamado pelo GrowthTickSystem para cada filho ativo.
     *
     * @param child     componente de crescimento
     * @param worldTick tick atual
     */
    public static void tickGrowth(GrowthComponent child, long worldTick) {
        // Atualizar estágio
        boolean stageChanged = child.updateStage(worldTick);

        if (stageChanged) {
            LOGGER.atInfo().log("SimTale: " + child.getFullName() + " cresceu para "
                + child.stage.getDisplayName() + " (escala: " + child.currentScale + ")");

            // TODO: Atualizar modelo/escala visual da entidade
            onStageChanged(child);
        }

        // Se ainda precisa de cuidados, decair necessidades
        if (child.needsCare() && child.babyNeeds != null) {
            child.babyNeeds.tickDecay();
        }
    }

    /**
     * Callback quando o estágio de crescimento muda.
     * Stub — será implementado para trocar modelo/escala.
     */
    private static void onStageChanged(GrowthComponent child) {
        // TODO: Atualizar PersistentModel com nova escala
        // TODO: Trocar modelo para versão adequada ao estágio
        // TODO: Se atingiu ADULT, converter em NPC independente
        if (child.isAdult()) {
            onBecameAdult(child);
        }
    }

    /**
     * Callback quando o filho se torna adulto.
     * Stub — será implementado para converter em NPC completo.
     */
    private static void onBecameAdult(GrowthComponent child) {
        LOGGER.atInfo().log("SimTale: " + child.getFullName() + " se tornou adulto!");
        // TODO: Converter a entidade de "filho" em um NPC adulto independente
        // TODO: Gerar personalidade baseada nas experiências (babyNeeds.getPersonalityTendency())
        // TODO: Remover da lista ACTIVE_CHILDREN
        // TODO: Aplicar modelo adulto
    }

    // =========================================================================
    // ESCALA
    // =========================================================================

    /**
     * Retorna a escala visual para um determinado estágio.
     */
    public static float getScaleForStage(GrowthStage stage) {
        return stage.getScale();
    }

    // =========================================================================
    // COMPORTAMENTO DA GRÁVIDA (STUBS)
    // =========================================================================

    /**
     * Aplica modificações de comportamento durante a gravidez.
     * Stub vazio — será implementado futuramente.
     *
     * Comportamentos planejados:
     * - Dorme mais
     * - Come mais
     * - Anda mais devagar
     * - Animação segurando barriga
     * - Diálogo diferente
     * - Evita combate
     */
    public static void applyPregnancyBehavior(SimNPCComponent mother) {
        // TODO: Implementar comportamento de grávida
    }

    /**
     * Retorna o multiplicador de velocidade durante a gravidez.
     * Stub — retorna 1.0 por enquanto.
     */
    public static float getPregnancySpeedMultiplier(PregnancyComponent pregnancy, long currentTick) {
        if (pregnancy == null || !pregnancy.pregnant) return 1.0f;
        // Mais lenta conforme avança a gravidez
        // TODO: Implementar curva real
        return 1.0f;
    }

    // =========================================================================
    // IA DA MÃE (STUBS)
    // =========================================================================

    /**
     * Lógica de IA para a mãe cuidando do bebê.
     * Stub vazio.
     *
     * Comportamentos planejados:
     * - Se bebê longe → ir até bebê
     * - Se bebê no chão → pegar bebê
     * - Se bebê chorando → consolar
     * - Se noite → dormir com bebê
     * - Se player segurando → esperar
     */
    public static void tickMotherAI(SimNPCComponent mother, GrowthComponent baby, long worldTick) {
        // TODO: Implementar IA materna
    }

    // =========================================================================
    // UTILITÁRIOS
    // =========================================================================

    /**
     * Encontra um NPC na lista de ativos pelo UUID.
     */
    private static SimNPCComponent findNPCById(UUID id) {
        if (id == null) return null;
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (id.equals(npc.entityId)) {
                return npc;
            }
        }
        return null;
    }

    /**
     * Obtém ou cria dados genéticos para um NPC existente.
     * Necessário porque NPCs adultos não tinham genética antes deste sistema.
     */
    private static GeneticsData getOrCreateGenetics(SimNPCComponent npc) {
        // NPCs antigos não têm dados genéticos — criamos baseado no entityId como seed
        if (npc.entityId != null) {
            return new GeneticsData(npc.entityId.getMostSignificantBits(), npc.entityId.getLeastSignificantBits());
        }
        return new GeneticsData();
    }

    /**
     * Obtém a posição de um NPC no mundo.
     */
    private static Vector3d getEntityPosition(SimNPCComponent npc, Store<EntityStore> store) {
        if (npc.entityRef == null) return null;
        try {
            com.hypixel.hytale.server.core.modules.entity.component.TransformComponent transform =
                store.getComponent(npc.entityRef,
                    com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
            if (transform != null) {
                return transform.getPosition();
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    /**
     * Encontra um filho ativo pela UUID da mãe.
     */
    public static List<GrowthComponent> findChildrenOfMother(UUID motherId) {
        List<GrowthComponent> result = new ArrayList<>();
        for (GrowthComponent child : ACTIVE_CHILDREN) {
            if (motherId.equals(child.motherId)) {
                result.add(child);
            }
        }
        return result;
    }

    /**
     * Encontra filhos que precisam de cuidado (BABY ou TODDLER).
     */
    public static List<GrowthComponent> findChildrenNeedingCare(UUID parentId) {
        List<GrowthComponent> result = new ArrayList<>();
        for (GrowthComponent child : ACTIVE_CHILDREN) {
            if (child.needsCare() && (parentId.equals(child.motherId) || parentId.equals(child.fatherId))) {
                result.add(child);
            }
        }
        return result;
    }
}
