package com.cookieukw.SimTale.tests;

import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.Needs;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.RelationshipStatus;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.lifecycle.LifecycleManager;
import com.cookieukw.SimTale.core.lifecycle.PregnancyComponent;
import com.cookieukw.SimTale.db.SimNPCData;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.core.lifecycle.BabyCareData;
import com.cookieukw.SimTale.core.lifecycle.BabyCareManager;

import com.cookieukw.SimTale.core.lifecycle.GrowthComponent;
import com.cookieukw.SimTale.core.lifecycle.GrowthStage;
import com.cookieukw.SimTale.core.lifecycle.GeneticsData;
import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.lifecycle.LifecycleManager;

import java.util.HashSet;
import java.util.UUID;

public class SimTaleTests {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("Iniciando Testes Unitários do SimTale...");
        System.out.println("========================================");

        try {
            testPregnancyComponent();
            testLifecycleManagerPregnancy();
            testRelationships();
            testNeedsDecay();
            testBabyCareSharing();
            testChildGrowth();
            testDatabaseShellIsolation();
            
            System.out.println("========================================");
            System.out.println("Todos os testes passaram com sucesso!");
            System.out.println("========================================");
        } catch (Throwable t) {
            System.out.println("========================================");
            System.out.println("FALHA NOS TESTES!");
            t.printStackTrace();
            System.out.println("========================================");
            System.exit(1);
        }
    }

    private static void testDatabaseShellIsolation() {
        System.out.print("Testando Isolamento da Shell do Banco (simtale.db)... ");
        
        // Inicializa o Caskara temporário
        java.io.File testFolder = new java.io.File("scratch/test_db");
        com.cookie.caskara.Caskara.init(testFolder);
        
        // Valida instanciamento do Shell do SimTale
        assertEqual(SimNPCPersistence.DB_SHELL != null, true, "DB_SHELL deve estar instanciado");
        assertEqual(SimNPCPersistence.DB_SHELL.getFile().getName(), "simtale.db", "Nome do banco");

        // Prepara dados fictícios
        UUID npcId = UUID.randomUUID();
        SimNPCData npcData = new SimNPCData();
        npcData.id = npcId.toString();
        npcData.name = "Test NPC Name";

        // Grava no Core de SimNPCData do DB_SHELL
        String savedId = SimNPCPersistence.DB_SHELL.core(SimNPCData.class).preserve(npcId.toString(), npcData);
        assertEqual(savedId, npcId.toString(), "ID retornado pelo salvamento");

        // Carrega do Core
        SimNPCData loaded = SimNPCPersistence.DB_SHELL.core(SimNPCData.class).extract(npcId.toString()).sync().orElse(null);
        assertEqual(loaded != null, true, "NPC carregado");
        assertEqual(loaded.name, "Test NPC Name", "Validação do nome gravado");

        // Deleta
        SimNPCPersistence.DB_SHELL.core(SimNPCData.class).discard(npcId.toString());
        SimNPCData deleted = SimNPCPersistence.DB_SHELL.core(SimNPCData.class).extract(npcId.toString()).sync().orElse(null);
        assertEqual(deleted == null, true, "NPC removido do banco");

        System.out.println("OK");
    }

    private static void testPregnancyComponent() {
        System.out.print("Testando PregnancyComponent... ");
        PregnancyComponent preg = new PregnancyComponent();
        
        // Estado inicial
        assertEqual(preg.pregnant, false, "Inicialmente grávida");
        assertEqual(preg.trimester, 0, "Trimestre inicial");

        // Início
        UUID fatherId = UUID.randomUUID();
        long startTick = 1000L;
        preg.start(fatherId, startTick);

        assertEqual(preg.pregnant, true, "Grávida após start");
        assertEqual(preg.trimester, 1, "Trimestre inicial de gravidez");
        assertEqual(preg.fatherId, fatherId, "ID do pai gravado");

        // Trimestre 2 (33% a 66% de progresso)
        // 5 dias = 120.000 ticks. T2 começa após 120000 * 0.33 = ~39600 ticks
        long tickT2 = startTick + 45000;
        boolean t2Changed = preg.updateTrimester(tickT2);
        assertEqual(t2Changed, true, "Trimestre mudou para T2");
        assertEqual(preg.trimester, 2, "Trimester deve ser 2");

        // Trimestre 3 (66% a 100% de progresso)
        long tickT3 = startTick + 85000;
        boolean t3Changed = preg.updateTrimester(tickT3);
        assertEqual(t3Changed, true, "Trimestre mudou para T3");
        assertEqual(preg.trimester, 3, "Trimester deve ser 3");

        // Pronto para nascer
        assertEqual(preg.isReadyToBirth(startTick + 120000), true, "Pronto para nascer");
        assertEqual(preg.isReadyToBirth(startTick + 119000), false, "Não deve estar pronto antes do tempo");

        // Reset
        preg.reset();
        assertEqual(preg.pregnant, false, "Reset limpou a gravidez");
        assertEqual(preg.trimester, 0, "Reset zerou trimestre");
        System.out.println("OK");
    }

    private static void testLifecycleManagerPregnancy() {
        System.out.print("Testando LifecycleManager (Gravidez)... ");
        
        SimNPCComponent mother = new SimNPCComponent(UUID.randomUUID(), "Maria");
        mother.gender = Gender.FEMALE;

        UUID fatherId = UUID.randomUUID();

        // Falha 1: Não casada
        boolean start1 = LifecycleManager.startPregnancy(mother, fatherId, 100L);
        assertEqual(start1, false, "Permitiu gravidez sem casamento");

        // Casar
        mother.family.marry(fatherId, null);

        // Falha 2: Sem romance suficiente (relacionamento romance padrão é < 50)
        boolean start2 = LifecycleManager.startPregnancy(mother, fatherId, 100L);
        assertEqual(start2, false, "Permitiu gravidez com romance baixo");

        // Romance alto
        mother.getRelationship(fatherId).romance = 75;

        // Sucesso
        boolean startSuccess = LifecycleManager.startPregnancy(mother, fatherId, 100L);
        assertEqual(startSuccess, true, "Falhou ao iniciar gravidez válida");
        assertEqual(mother.pregnancy.pregnant, true, "PregnancyComponent não marcado grávido");
        System.out.println("OK");
    }

    private static void testRelationships() {
        System.out.print("Testando Relacionamentos... ");
        UUID targetId = UUID.randomUUID();
        Relationship rel = new Relationship(targetId);

        // Romance e Amizade inicial
        assertEqual(rel.romance, 0, "Romance inicial");
        assertEqual(rel.friendship, 0, "Amizade inicial");
        assertEqual(rel.status, RelationshipStatus.STRANGER, "Status inicial");

        // Alterações
        rel.romance = 60;
        rel.friendship = 80;
        rel.status = RelationshipStatus.DATING;

        assertEqual(rel.romance, 60, "Romance alterado");
        assertEqual(rel.friendship, 80, "Amizade alterada");
        assertEqual(rel.status, RelationshipStatus.DATING, "Status alterado");
        System.out.println("OK");
    }

    private static void testNeedsDecay() {
        System.out.print("Testando Decaimento de Necessidades... ");
        Needs needs = new Needs();

        // Inicial
        assertEqual(needs.hunger, 100f, "Fome inicial");
        assertEqual(needs.energy, 100f, "Energia inicial");

        // Decaimento normal (sem traits)
        needs.tickDecay(new HashSet<>());
        // hunger cai 0.0001f, energy cai 0.0002f
        assertFloatEqual(needs.hunger, 99.9999f, "Decaimento fome");
        assertFloatEqual(needs.energy, 99.9998f, "Decaimento energia");

        // Decaimento gravidez acelerado
        SimNPCComponent mother = new SimNPCComponent(UUID.randomUUID(), "Maria");
        mother.gender = Gender.FEMALE;
        mother.needs = new Needs();
        mother.pregnancy = new PregnancyComponent();
        mother.pregnancy.start(UUID.randomUUID(), 100L);
        mother.pregnancy.trimester = 3; // Trimestre 3 = Multiplicador máximo

        // Aplicar comportamento de gravidez
        float preHunger = mother.needs.hunger;
        float preEnergy = mother.needs.energy;

        LifecycleManager.applyPregnancyBehavior(mother);

        // Fome extra decai: 0.0001f * (mult - 1.0f) onde mult = 1.0f + (3 * 0.3f) = 1.9f
        // Então decai 0.0001f * 0.9 = 0.00009f
        assertFloatEqual(mother.needs.hunger, preHunger - 0.00009f, "Decaimento fome gravidez T3");
        assertFloatEqual(mother.needs.energy, preEnergy - 0.00018f, "Decaimento energia gravidez T3");
        System.out.println("OK");
    }

    private static void testBabyCareSharing() {
        System.out.print("Testando Cuidado Compartilhado (BabyCare)... ");
        
        UUID childId = UUID.randomUUID();
        UUID motherId = UUID.randomUUID();
        UUID fatherId = UUID.randomUUID();

        // 1. Inicializacao
        BabyCareData care = new BabyCareData(childId.toString(), motherId.toString(), fatherId.toString());
        assertEqual(care.childId, childId.toString(), "ID do filho");
        assertEqual(care.motherId, motherId.toString(), "ID da mae");
        assertEqual(care.fatherId, fatherId.toString(), "ID do pai");
        assertEqual(care.currentHolderId, motherId.toString(), "Portador inicial");
        assertEqual(care.currentTurnOwnerId, motherId.toString(), "Dono do turno inicial");

        // 2. Testar troca permitida (cooldown)
        long now = System.currentTimeMillis();
        assertEqual(now < care.nextSwapAllowedTime, true, "Cooldown ativo inicialmente");

        // Forcar tempo passar e testar toggle manual de turno
        // Trocando turno: da mae para o pai
        care.currentTurnOwnerId = fatherId.toString();
        care.currentHolderId = fatherId.toString();
        assertEqual(care.currentTurnOwnerId, fatherId.toString(), "Turno alterado para o pai");
        assertEqual(care.currentHolderId, fatherId.toString(), "Portador alterado para o pai");

        System.out.println("OK");
    }

    private static void testChildGrowth() {
        System.out.print("Testando Crescimento Infantil (Visual Scale)... ");

        UUID motherId = UUID.randomUUID();
        UUID fatherId = UUID.randomUUID();

        GrowthComponent child = new GrowthComponent(
            motherId,
            fatherId,
            0L, // birthTick
            Gender.MALE,
            new GeneticsData(),
            "Enzo",
            "SimTale"
        );

        // 1. Testar escalas iniciais nas fases de crescimento
        child.birthTick = 0L;
        child.stage = GrowthStage.BABY;
        assertFloatEqual(LifecycleManager.calculateTargetScale(child, 0L), 0.35f, "Escala do Bebe");

        child.birthTick = -4 * 24000L;
        child.stage = GrowthStage.TODDLER;
        assertFloatEqual(LifecycleManager.calculateTargetScale(child, 0L), 0.45f, "Escala do Toddler Inicial");

        child.birthTick = -8 * 24000L;
        child.stage = GrowthStage.TODDLER;
        assertFloatEqual(LifecycleManager.calculateTargetScale(child, 0L), 0.55f, "Escala do Toddler Final");

        child.birthTick = -9 * 24000L;
        child.stage = GrowthStage.CHILD;
        assertFloatEqual(LifecycleManager.calculateTargetScale(child, 0L), 0.55f, "Escala da Crianca Inicial");

        child.birthTick = -20 * 24000L;
        child.stage = GrowthStage.CHILD;
        assertFloatEqual(LifecycleManager.calculateTargetScale(child, 0L), 0.85f, "Escala da Crianca Final");

        child.birthTick = -21 * 24000L;
        child.stage = GrowthStage.TEEN;
        assertFloatEqual(LifecycleManager.calculateTargetScale(child, 0L), 0.75f, "Escala do Adolescente Inicial");

        child.birthTick = -40 * 24000L;
        child.stage = GrowthStage.TEEN;
        assertFloatEqual(LifecycleManager.calculateTargetScale(child, 0L), 0.95f, "Escala do Adolescente Final");

        child.birthTick = -41 * 24000L;
        child.stage = GrowthStage.ADULT;
        assertFloatEqual(LifecycleManager.calculateTargetScale(child, 0L), 1.00f, "Escala do Adulto");

        System.out.println("OK");
    }

    private static void assertEqual(Object actual, Object expected, String message) {
        if (actual == null && expected == null) return;
        if (actual == null || !actual.equals(expected)) {
            throw new AssertionError(message + " - Esperado: " + expected + ", Encontrado: " + actual);
        }
    }

    private static void assertFloatEqual(float actual, float expected, String message) {
        if (Math.abs(actual - expected) > 0.00001f) {
            throw new AssertionError(message + " - Esperado: " + expected + ", Encontrado: " + actual);
        }
    }
}
