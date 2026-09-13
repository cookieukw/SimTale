package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.SimLog;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.SimNPCFactory;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Gatilho automatico de fantasias sazonais (Natal/Halloween) por calendario, mais o codigo
 * compartilhado de aplicar/remover uma fantasia -- usado tanto por este gatilho automatico
 * quanto pelo comando manual "/simtale costume" em SimTaleCommand.java, pra garantir que os
 * dois nunca discordem sobre o que uma NPC esta realmente vestindo (um so mapa de backup, um
 * so mapa de "evento ativo").
 *
 * O calendario vem de {@link WorldTimeResource#getGameDateTime()}, ja usado em
 * NPCSleepHelper/InteractionManager -- ver docs/experimentos.md / wiki/dev/experiments.md
 * (secao "gatilho automatico por calendario", 13/09) pro raciocinio completo, inclusive as duas
 * pegadinhas: getMoonPhase() nao e uma fase nomeada, e isYearWithinRange() tem um TODO nunca
 * implementado (por isso as datas abaixo sao comparadas na mao por mes/dia, nao por esse metodo).
 *
 * Cada NPC fantasiada usa um asset gerado por scripts/generate_costume_assets.py, cujo "Parent"
 * aponta pro proprio id de modelo daquela NPC especifica -- ver a mesma secao dos docs pra saber
 * por que (evita toda NPC fantasiada ficar com a mesma cara generica).
 */
public final class SeasonalCostumeHelper {

    private SeasonalCostumeHelper() {}

    private static final SimLog LOGGER = SimLog.forClass(SeasonalCostumeHelper.class);

    /**
     * So roda a leitura do calendario 1x a cada tantos ticks -- as outras NPCs no mesmo tick
     * global so conferem o cache abaixo e saem, em vez de repetir a leitura do relogio.
     * ~1200 ticks = ~1 min de tempo real a 20 ticks/s; nao precisa ser mais frequente que isso,
     * data nao muda rapido.
     */
    private static final long CHECK_INTERVAL_TICKS = 1200;

    private static long lastCheckedTick = -1;

    /** entityId -> id do modelo de ANTES da fantasia, pra "off"/reversao restaurar certo. */
    public static final Map<UUID, String> COSTUME_BACKUP_MODEL = new HashMap<>();

    /**
     * entityId -> evento da fantasia ATUALMENTE aplicada ("Christmas"/"Halloween"), ausente se
     * a NPC nao esta fantasiada. Mantido separado do backup acima pra o gatilho automatico saber
     * sem reler o PersistentModel se aquela NPC ja esta no evento certo.
     */
    public static final Map<UUID, String> ACTIVE_COSTUME_EVENT = new HashMap<>();

    /**
     * Decide qual evento (se algum) esta ativo pra data dada.
     *
     * Datas de EXEMPLO, ajuste livremente aqui: dezembro inteiro pro Natal, ultima semana de
     * outubro pro Halloween. De proposito sem cruzar virada de mes/ano, pra manter a comparacao
     * simples de auditar sem compilador.
     */
    @Nullable
    public static String resolveEvent(@Nonnull LocalDateTime date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();

        if (month == 12) return "Christmas"; // dezembro inteiro
        if (month == 10 && day >= 25) return "Halloween"; // 25-31 de outubro

        return null;
    }

    /**
     * Chamado a cada tick de NPC por SimTaleTickSystem. Barato quando nao ha nada a fazer: uma
     * comparacao de long e, na esmagadora maioria dos ticks, retorno imediato.
     */
    public static void tick(@Nonnull World world, @Nonnull Store<EntityStore> store, long absoluteTick) {
        if (absoluteTick % CHECK_INTERVAL_TICKS != 0 || absoluteTick == lastCheckedTick) return;
        lastCheckedTick = absoluteTick;

        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        if (time == null) return;

        String currentEvent = resolveEvent(time.getGameDateTime());
        reconcileAll(store, currentEvent);
    }

    /**
     * Passa por toda NPC ativa e garante que ela esta vestindo o que deveria: aplica o evento
     * atual em quem ainda nao tem, remove de quem tem um evento errado/antigo, e nao mexe em
     * quem ja esta correta. Publico tambem pra poder ser chamado manualmente (ex: um comando de
     * debug futuro) sem esperar o proximo intervalo do tick automatico.
     */
    public static void reconcileAll(@Nonnull Store<EntityStore> store, @Nullable String currentEvent) {
        for (SimNPCComponent npc : SimTale.ACTIVE_NPCS) {
            if (npc.entityRef == null || !npc.entityRef.isValid() || npc.isReaper) continue;

            String activeEvent = ACTIVE_COSTUME_EVENT.get(npc.entityId);
            if (Objects.equals(activeEvent, currentEvent)) continue; // ja esta certa, nada a fazer

            Ref<EntityStore> npcRef = npc.entityRef;

            if (activeEvent != null) {
                removeCostume(store, npcRef, npc);
            }
            if (currentEvent != null) {
                applyCostume(store, npcRef, npc, currentEvent);
            }
        }
    }

    /**
     * Aplica a fantasia do evento dado nesta NPC, guardando o modelo original pra "off"/reversao
     * depois. Retorna false (sem mudar nada) se o asset gerado nao existir -- por exemplo, uma
     * NPC com um id de modelo fora do padrao que scripts/generate_costume_assets.py cobre
     * (deveria ser raro: o script cobre todo id encontrado em Generated/ na hora que rodou).
     */
    public static boolean applyCostume(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> npcRef,
            @Nonnull SimNPCComponent npc, @Nonnull String event) {
        PersistentModel pm = store.getComponent(npcRef, PersistentModel.getComponentType());
        if (pm == null) return false;

        String currentId = pm.getModelReference().getModelAssetId();
        float scale = pm.getModelReference().getScale();
        String costumeId = currentId + "_" + event;

        boolean ok = SimNPCFactory.applyModel(store, npcRef, costumeId, scale, new HashMap<>());
        if (ok) {
            COSTUME_BACKUP_MODEL.putIfAbsent(npc.entityId, currentId);
            ACTIVE_COSTUME_EVENT.put(npc.entityId, event);
            LOGGER.debug("[SimTale] " + npc.name + " fantasiada automaticamente pro evento '" + event + "'.");
        } else {
            LOGGER.warn("[SimTale] Falha ao fantasiar " + npc.name + " pro evento '" + event
                    + "' -- asset '" + costumeId + "' nao encontrado. Rode scripts/generate_costume_assets.py.");
        }
        return ok;
    }

    /**
     * Restaura o modelo original desta NPC, se ela tiver um backup guardado. Sem-op silencioso
     * (retorna false) se nao houver backup -- por exemplo, uma remocao chamada quando a NPC ja
     * nao estava fantasiada.
     */
    public static boolean removeCostume(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> npcRef,
            @Nonnull SimNPCComponent npc) {
        String original = COSTUME_BACKUP_MODEL.remove(npc.entityId);
        ACTIVE_COSTUME_EVENT.remove(npc.entityId);
        if (original == null) return false;

        PersistentModel pm = store.getComponent(npcRef, PersistentModel.getComponentType());
        float scale = pm != null ? pm.getModelReference().getScale() : 1f;

        boolean ok = SimNPCFactory.applyModel(store, npcRef, original, scale, new HashMap<>());
        if (!ok) {
            LOGGER.warn("[SimTale] Falha ao remover fantasia de " + npc.name + " -- modelo original '"
                    + original + "' nao encontrado.");
        }
        return ok;
    }
}
