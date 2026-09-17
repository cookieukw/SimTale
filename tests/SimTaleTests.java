package com.cookieukw.SimTale.tests;

import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.core.Gender;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.RelationshipStatus;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.lifecycle.LifecycleManager;
import com.cookieukw.SimTale.core.lifecycle.PregnancyComponent;
import com.cookieukw.SimTale.db.SimNPCData;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.core.lifecycle.BabyCareData;

import com.cookieukw.SimTale.core.lifecycle.GrowthComponent;
import com.cookieukw.SimTale.core.lifecycle.GrowthStage;
import com.cookieukw.SimTale.core.lifecycle.GeneticsData;
import com.cookieukw.SimTale.core.lifecycle.BabyNeeds;
import com.cookieukw.SimTale.core.Memory;
import com.cookieukw.SimTale.core.MemoryEvent;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.systems.NPCSocialHelper;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.Trait;
import com.cookieukw.SimTale.core.NPCPreferences;
import com.cookieukw.SimTale.logic.InteractionManager;
import com.cookieukw.SimTale.logic.InteractionManager.InteractionOutcome;
import com.hypixel.hytale.server.core.Message;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.HouseData;
import com.cookieukw.SimTale.db.ChestData;
import com.cookieukw.SimTale.db.SimBedData;
import com.cookieukw.SimTale.systems.ChairRegistry;
import com.cookieukw.SimTale.systems.ChestRegistry;
import com.cookieukw.SimTale.systems.HouseManager;
import com.cookieukw.SimTale.systems.SimTaleJuiceHelper;
import com.cookieukw.SimTale.systems.VillageManager;
import com.cookieukw.SimTale.systems.VillageStockManager;
import org.joml.Vector3i;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Test runner: {@code ./gradlew runTests}.
 *
 * <p>Only pure logic is covered. Anything needing a {@code World}, a {@code Store} or a live entity
 * is out of scope here and belongs in {@code testing_checklist.md} as an in-game check — that split
 * is deliberate, not a gap.
 */
public class SimTaleTests {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("SimTale — unit tests");
        System.out.println("========================================");

        try {
            System.out.println("Asset ids");
            AssetIdsTests.run();

            System.out.println("Geometry");
            GeometryTests.run();

            System.out.println("Mood");
            MoodTests.run();

            System.out.println("Names");
            NameTests.run();

            System.out.println("Lifecycle");
            testPregnancyComponent();
            testLifecycleManagerPregnancy();
            testRelationships();
            testBabyCareSharing();
            testBabyNeeds();
            testChildGrowth();
            testDatabaseShellIsolation();
            testMemoryAndGossip();
            testProfessionAcceptance();
            testFacialReactionJuice();

            System.out.println("Seating & Chairs");
            testChairRegistry();

            System.out.println("Houses & Bed Independence");
            testHouseBedIndependence();

            System.out.println("Village Stock & Shared Chests");
            testVillageStockAndSharedChests();

            System.out.println("========================================");
            System.out.println("All tests passed (" + Assert.checks + " assertions)");
            System.out.println("========================================");
        } catch (Throwable t) {
            System.out.println("========================================");
            System.out.println("TEST FAILURE");
            t.printStackTrace();
            System.out.println("========================================");
            System.exit(1);
        }
    }

    private static void testDatabaseShellIsolation() {
        System.out.print("Testing Database Shell Isolation (simtale.db)... ");
        
        // Initialize temporary Caskara
        java.io.File testFolder = new java.io.File("scratch/test_db");
        com.cookie.caskara.Caskara.init(testFolder);
        
        // Validate SimTale Shell instantiation
        assertEqual(SimNPCPersistence.DB_SHELL != null, true, "DB_SHELL must be instantiated");
        assertEqual(SimNPCPersistence.DB_SHELL.getFile().getName(), "simtale.db", "Database file name");

        // Prepare dummy data
        UUID npcId = UUID.randomUUID();
        SimNPCData npcData = new SimNPCData();
        npcData.id = npcId.toString();
        npcData.name = "Test NPC Name";

        // Save to SimNPCData Core of DB_SHELL
        String savedId = SimNPCPersistence.DB_SHELL.core(SimNPCData.class).preserve(npcId.toString(), npcData);
        assertEqual(savedId, npcId.toString(), "ID returned by save");

        // Load from Core
        SimNPCData loaded = SimNPCPersistence.DB_SHELL.core(SimNPCData.class).extract(npcId.toString()).sync().orElse(null);
        assertEqual(loaded != null, true, "NPC loaded");
        assertEqual(loaded.name, "Test NPC Name", "Validation of saved name");

        // Delete
        SimNPCPersistence.DB_SHELL.core(SimNPCData.class).discard(npcId.toString());
        SimNPCData deleted = SimNPCPersistence.DB_SHELL.core(SimNPCData.class).extract(npcId.toString()).sync().orElse(null);
        assertEqual(deleted == null, true, "NPC removed from database");

        System.out.println("OK");
    }

    private static void testPregnancyComponent() {
        System.out.print("Testing PregnancyComponent... ");
        PregnancyComponent preg = new PregnancyComponent();
        
        // Initial state
        assertEqual(preg.pregnant, false, "Initially pregnant");
        assertEqual(preg.trimester, 0, "Initial trimester");

        // Start
        UUID fatherId = UUID.randomUUID();
        long startTick = 1000L;
        preg.start(fatherId, startTick);

        assertEqual(preg.pregnant, true, "Pregnant after start");
        assertEqual(preg.trimester, 1, "Initial pregnancy trimester");
        assertEqual(preg.fatherId, fatherId, "Recorded father ID");

        /* Trimester 2 (33% to 66% progress)
        5 days = 120,000 ticks. T2 starts after 120000 * 0.33 = ~39600 ticks
        */
        long tickT2 = startTick + 45000;
        boolean t2Changed = preg.updateTrimester(tickT2);
        assertEqual(t2Changed, true, "Trimester changed to T2");
        assertEqual(preg.trimester, 2, "Trimester should be 2");

        // Trimester 3 (66% to 100% progress)
        long tickT3 = startTick + 85000;
        boolean t3Changed = preg.updateTrimester(tickT3);
        assertEqual(t3Changed, true, "Trimester changed to T3");
        assertEqual(preg.trimester, 3, "Trimester should be 3");

        // Ready to birth
        assertEqual(preg.isReadyToBirth(startTick + 120000), true, "Ready to birth");
        assertEqual(preg.isReadyToBirth(startTick + 119000), false, "Must not be ready before time");

        // Reset
        preg.reset();
        assertEqual(preg.pregnant, false, "Reset cleared pregnancy");
        assertEqual(preg.trimester, 0, "Reset zeroed trimester");
        System.out.println("OK");
    }

    private static void testLifecycleManagerPregnancy() {
        System.out.print("Testing LifecycleManager (Pregnancy)... ");
        
        SimNPCComponent mother = new SimNPCComponent(UUID.randomUUID(), "Maria");
        mother.gender = Gender.FEMALE;

        UUID fatherId = UUID.randomUUID();

        // Failure 1: Not married
        boolean start1 = LifecycleManager.startPregnancy(mother, fatherId, 100L);
        assertEqual(start1, false, "Allowed pregnancy without marriage");

        // Marry
        mother.family.marry(fatherId, null);

        // Failure 2: Insufficient romance (default romance relationship is < 50)
        boolean start2 = LifecycleManager.startPregnancy(mother, fatherId, 100L);
        assertEqual(start2, false, "Allowed pregnancy with low romance");

        // High romance
        mother.getRelationship(fatherId).romance = 75;

        // Success
        boolean startSuccess = LifecycleManager.startPregnancy(mother, fatherId, 100L);
        assertEqual(startSuccess, true, "Failed to start valid pregnancy");
        assertEqual(mother.pregnancy.pregnant, true, "PregnancyComponent not marked pregnant");
        System.out.println("OK");
    }

    private static void testRelationships() {
        System.out.print("Testing Relationships... ");
        UUID targetId = UUID.randomUUID();
        Relationship rel = new Relationship(targetId);

        // Initial Romance and Friendship
        assertEqual(rel.romance, 0, "Initial romance");
        assertEqual(rel.friendship, 0, "Initial friendship");
        assertEqual(rel.status, RelationshipStatus.STRANGER, "Initial status");

        // Modifications
        rel.romance = 60;
        rel.friendship = 80;
        rel.status = RelationshipStatus.DATING;

        assertEqual(rel.romance, 60, "Modified romance");
        assertEqual(rel.friendship, 80, "Modified friendship");
        assertEqual(rel.status, RelationshipStatus.DATING, "Modified status");
        System.out.println("OK");
    }

    /* testNeedsDecay was removed, not ported.

    It exercised com.cookieukw.SimTale.core.Needs, which no longer exists: needs moved to the
    engine's native EntityStats, so reading or writing one now requires a live Store and an
    entity that carries an EntityStatMap. The same applies to applyPregnancyBehavior, which the
    old test also called — it is now three NeedsHelper calls against a real entity.

    Keeping the test compiling would have meant faking the ECS. The decay rates it guarded are
    instead checked in game, through the calibration table in testing_checklist.md.
    */

    private static void testBabyCareSharing() {
        System.out.print("Testing Shared Baby Care (BabyCare)... ");
        
        UUID childId = UUID.randomUUID();
        UUID motherId = UUID.randomUUID();
        UUID fatherId = UUID.randomUUID();

        // 1. Initialization
        BabyCareData care = new BabyCareData(childId.toString(), motherId.toString(), fatherId.toString());
        assertEqual(care.childId, childId.toString(), "Child ID");
        assertEqual(care.motherId, motherId.toString(), "Mother ID");
        assertEqual(care.fatherId, fatherId.toString(), "Father ID");
        assertEqual(care.currentHolderId, motherId.toString(), "Initial holder");
        assertEqual(care.currentTurnOwnerId, motherId.toString(), "Initial turn owner");

        // 2. Test allowed swap (cooldown)
        long now = System.currentTimeMillis();
        assertEqual(now < care.nextSwapAllowedTime, true, "Cooldown active initially");

        /* Force time to advance and test manual shift toggle
        Swapping shift: from mother to father
        */
        care.currentTurnOwnerId = fatherId.toString();
        care.currentHolderId = fatherId.toString();
        assertEqual(care.currentTurnOwnerId, fatherId.toString(), "Shift swapped to father");
        assertEqual(care.currentHolderId, fatherId.toString(), "Holder swapped to father");

        System.out.println("OK");
    }

    private static void testBabyNeeds() {
        System.out.print("Testing Baby Needs & Personality Tendencies... ");

        BabyNeeds needs = new BabyNeeds();
        assertEqual(needs.hunger, 100f, "Initial baby hunger");
        assertEqual(needs.affection, 100f, "Initial baby affection");
        assertEqual(needs.health, 100f, "Initial baby health");
        assertEqual(needs.isCrying(), false, "Initial baby should not be crying");
        assertEqual(needs.isCritical(), false, "Initial baby should not be critical");

        // Initial default score is 0.5 (1 / (1 + 1)), which maps to BALANCED
        assertFloatEqual(needs.getWellbeingScore(), 0.5f, "Initial wellbeing score");
        assertEqual(needs.getPersonalityTendency(), BabyNeeds.PersonalityTendency.BALANCED, "Initial balanced tendency");

        // Positive experiences: feeding and affection
        needs.feed(20f);
        needs.showAffection(20f);
        // positiveExperiences = 0.5 + 0.3 = 0.8 -> score = 1.8 / 2.8 ~= 0.64 -> still BALANCED
        assertEqual(needs.getPersonalityTendency(), BabyNeeds.PersonalityTendency.BALANCED, "Balanced after slight care");

        // Give lots of care -> SOCIABLE (>= 0.7)
        for (int i = 0; i < 10; i++) {
            needs.feed(10f);
            needs.showAffection(10f);
        }
        assertEqual(needs.getPersonalityTendency(), BabyNeeds.PersonalityTendency.SOCIABLE, "Tendency after extensive care");

        // Neglected baby: low stats, lots of negative experiences -> WITHDRAWN or AGGRESSIVE
        BabyNeeds neglected = new BabyNeeds();
        neglected.hunger = 10f;
        neglected.affection = 10f;
        assertEqual(neglected.isCrying(), true, "Neglected baby cries");
        neglected.negativeExperiences = 5.0f; // score = 1 / (1 + 5) = 0.166 < 0.2 -> AGGRESSIVE
        assertEqual(neglected.getPersonalityTendency(), BabyNeeds.PersonalityTendency.AGGRESSIVE, "Aggressive tendency when severely neglected");

        neglected.negativeExperiences = 2.0f; // score = 1 / (1 + 2) = 0.333 -> WITHDRAWN (0.2 - 0.4)
        assertEqual(neglected.getPersonalityTendency(), BabyNeeds.PersonalityTendency.WITHDRAWN, "Withdrawn tendency when moderately neglected");

        System.out.println("OK");
    }

    private static void testChildGrowth() {
        System.out.print("Testing Child Growth (Visual Scale)... ");

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

        // 1. Test initial scales across growth stages
        child.birthTick = 0L;
        child.stage = GrowthStage.BABY;
        assertFloatEqual(LifecycleManager.calculateTargetScale(child, 0L), 0.35f, "Baby scale");

        child.birthTick = -4 * 24000L;
        child.stage = GrowthStage.TODDLER;
        assertFloatEqual(LifecycleManager.calculateTargetScale(child, 0L), 0.45f, "Initial toddler scale");

        child.birthTick = -8 * 24000L;
        child.stage = GrowthStage.TODDLER;
        assertFloatEqual(LifecycleManager.calculateTargetScale(child, 0L), 0.55f, "Final toddler scale");

        child.birthTick = -9 * 24000L;
        child.stage = GrowthStage.CHILD;
        assertFloatEqual(LifecycleManager.calculateTargetScale(child, 0L), 0.55f, "Initial child scale");

        child.birthTick = -20 * 24000L;
        child.stage = GrowthStage.CHILD;
        assertFloatEqual(LifecycleManager.calculateTargetScale(child, 0L), 0.85f, "Final child scale");

        child.birthTick = -21 * 24000L;
        child.stage = GrowthStage.TEEN;
        assertFloatEqual(LifecycleManager.calculateTargetScale(child, 0L), 0.85f, "Initial teen scale");

        child.birthTick = -40 * 24000L;
        child.stage = GrowthStage.TEEN;
        assertFloatEqual(LifecycleManager.calculateTargetScale(child, 0L), 1.00f, "Final teen scale");

        child.birthTick = -41 * 24000L;
        child.stage = GrowthStage.ADULT;
        assertFloatEqual(LifecycleManager.calculateTargetScale(child, 0L), 1.00f, "Adult scale");

        System.out.println("OK");
    }

    private static void testMemoryAndGossip() {
        System.out.print("Testing Memory & Gossip Propagation... ");

        UUID playerUuid = UUID.randomUUID();

        // 1. Memory creation and distinction between direct and gossip
        SimNPCComponent alice = new SimNPCComponent();
        alice.name = "Alice";
        alice.entityId = UUID.randomUUID();

        alice.memory.addMemory(MemoryEvent.ATTACKED, playerUuid);
        Assert.equal(alice.memory.remembers(MemoryEvent.ATTACKED, playerUuid, 10000), true, "Alice remembers attack");
        Memory mem = alice.memory.getMemory(MemoryEvent.ATTACKED, playerUuid, 10000);
        Assert.isTrue(mem != null, "Memory object found");
        Assert.equal(mem.isGossip, false, "Direct attack is not gossip");
        Assert.equal(mem.gossipTargetName, null, "Direct attack has no gossip victim");

        // 2. Gossip propagation to Bob
        SimNPCComponent bob = new SimNPCComponent();
        bob.name = "Bob";
        bob.entityId = UUID.randomUUID();

        Assert.equal(bob.memory.remembers(MemoryEvent.ATTACKED, playerUuid, 10000), false, "Bob does not know yet");

        NPCSocialHelper.shareGossip(alice, bob, 100L);

        Assert.equal(bob.memory.remembers(MemoryEvent.ATTACKED, playerUuid, 10000), true, "Bob heard gossip about attack");
        Memory bobMem = bob.memory.getMemory(MemoryEvent.ATTACKED, playerUuid, 10000);
        Assert.isTrue(bobMem != null, "Bob has memory object");
        Assert.equal(bobMem.isGossip, true, "Bob's memory is gossip");
        Assert.equal(bobMem.gossipTargetName, "Alice", "Bob knows Alice was attacked");
        Assert.equal(bob.getMood(), Mood.SCARED, "Bob is scared of attacker");

        Relationship bobRel = bob.getRelationship(playerUuid);
        Assert.isTrue(bobRel.trust < 0, "Bob trust penalized by gossip");
        Assert.isTrue(bobRel.affinity < 0, "Bob affinity penalized by gossip");

        System.out.println("OK");
    }

    private static void testProfessionAcceptance() {
        System.out.print("Testing Profession Acceptance Rules... ");

        UUID playerUuid = UUID.randomUUID();

        // 1. Liked profession: always accepted enthusiastically
        SimNPCComponent enthusiastic = new SimNPCComponent();
        enthusiastic.name = "FarmerJohn";
        enthusiastic.personality.traits.clear();
        enthusiastic.preferences = new NPCPreferences(
                Set.of(), Set.of(), Set.of(), Set.of(),
                NPCPreferences.Season.SPRING, NPCPreferences.Weather.CLEAR, NPCPreferences.Hobby.GARDENING,
                Set.of(Profession.FARMER), Set.of(Profession.MINER)
        );
        Relationship rel = enthusiastic.getRelationship(playerUuid);
        rel.status = RelationshipStatus.ACQUAINTANCE;
        rel.friendship = 30;

        InteractionManager.ProfessionContext ctxLiked = new InteractionManager.ProfessionContext(
                enthusiastic, rel, Profession.FARMER, Message.raw("Farmer"), "Wood_Hoe", 0.5
        );
        Optional<InteractionOutcome> refusal = InteractionManager.evaluateProfessionRules(ctxLiked);
        Assert.equal(refusal.isPresent(), false, "Liked profession is never refused");

        InteractionOutcome acceptOutcome = InteractionManager.buildProfessionAcceptance(
                enthusiastic, rel, Profession.FARMER, Message.raw("Farmer"), "Wood_Hoe", Message.raw(""), ctxLiked
        );
        Assert.equal(acceptOutcome.friendship(), 15, "Liked profession grants bonus friendship");
        Assert.equal(acceptOutcome.affinity(), 25, "Liked profession grants bonus affinity");
        Assert.equal(enthusiastic.getMood(), Mood.EXCITED, "Liked profession sets EXCITED mood");

        // 2. Disliked profession: refused
        InteractionManager.ProfessionContext ctxDisliked = new InteractionManager.ProfessionContext(
                enthusiastic, rel, Profession.MINER, Message.raw("Miner"), "Wood_Pickaxe", 0.5
        );
        Optional<InteractionOutcome> refusalDisliked = InteractionManager.evaluateProfessionRules(ctxDisliked);
        Assert.equal(refusalDisliked.isPresent(), true, "Disliked profession is refused");

        // 3. Picky / Greedy: refuses non-lucrative neutral profession without close bond
        SimNPCComponent greedyNpc = new SimNPCComponent();
        greedyNpc.name = "GreedyBob";
        greedyNpc.personality.traits.clear();
        greedyNpc.personality.traits.add(Trait.GREEDY);
        greedyNpc.preferences = new NPCPreferences(
                Set.of(), Set.of(), Set.of(), Set.of(),
                NPCPreferences.Season.SUMMER, NPCPreferences.Weather.CLEAR, NPCPreferences.Hobby.MINING,
                Set.of(Profession.MINER), Set.of()
        );
        Relationship greedyRel = greedyNpc.getRelationship(playerUuid);
        greedyRel.status = RelationshipStatus.ACQUAINTANCE;
        greedyRel.friendship = 25;

        InteractionManager.ProfessionContext ctxGreedyFarmer = new InteractionManager.ProfessionContext(
                greedyNpc, greedyRel, Profession.FARMER, Message.raw("Farmer"), "Wood_Hoe", 0.5
        );
        Optional<InteractionOutcome> refusalGreedy = InteractionManager.evaluateProfessionRules(ctxGreedyFarmer);
        Assert.equal(refusalGreedy.isPresent(), true, "Greedy NPC refuses neutral farming");

        // 4. Loyal / Easy NPC: accepts neutral profession easily
        SimNPCComponent loyalNpc = new SimNPCComponent();
        loyalNpc.name = "LoyalDoggo";
        loyalNpc.personality.traits.clear();
        loyalNpc.personality.traits.add(Trait.LOYAL);
        loyalNpc.preferences = new NPCPreferences(
                Set.of(), Set.of(), Set.of(), Set.of(),
                NPCPreferences.Season.SPRING, NPCPreferences.Weather.CLEAR, NPCPreferences.Hobby.READING,
                Set.of(), Set.of()
        );
        Relationship loyalRel = loyalNpc.getRelationship(playerUuid);
        loyalRel.status = RelationshipStatus.ACQUAINTANCE;
        loyalRel.friendship = 25;

        InteractionManager.ProfessionContext ctxLoyal = new InteractionManager.ProfessionContext(
                loyalNpc, loyalRel, Profession.FISHERMAN, Message.raw("Fisherman"), "Fishing_Rod", 0.5
        );
        Optional<InteractionOutcome> refusalLoyal = InteractionManager.evaluateProfessionRules(ctxLoyal);
        Assert.equal(refusalLoyal.isPresent(), false, "Loyal NPC accepts neutral profession");

        InteractionOutcome loyalAccept = InteractionManager.buildProfessionAcceptance(
                loyalNpc, loyalRel, Profession.FISHERMAN, Message.raw("Fisherman"), "Fishing_Rod", Message.raw(""), ctxLoyal
        );
        Assert.equal(loyalAccept.friendship(), 10, "Loyal NPC gives 10 friendship on accept");
        Assert.equal(loyalNpc.getMood(), Mood.HAPPY, "Loyal NPC is HAPPY to accept");

        System.out.println("OK");
    }

    private static void testChairRegistry() {
        System.out.print("Testing ChairRegistry... ");
        ChairRegistry.clear();
        ChairRegistry.staleCheckEnabled = false;
        try {
            // 1. Block name checks
            Assert.equal(ChairRegistry.isChair("wood_chair"), true, "wood_chair is a chair");
            Assert.equal(ChairRegistry.isChair("stone_stool"), true, "stone_stool is a chair");
            Assert.equal(ChairRegistry.isChair("sofa_red"), true, "sofa_red is a chair");
            Assert.equal(ChairRegistry.isChair("couch_luxury"), true, "couch_luxury is a chair");
            Assert.equal(ChairRegistry.isChair("bench_lumbermill"), false, "bench_lumbermill is not a chair");
            Assert.equal(ChairRegistry.isChair("workbench"), false, "workbench is not a chair");
            Assert.equal(ChairRegistry.isChair("alchemybench"), false, "alchemybench is not a chair");
            Assert.equal(ChairRegistry.isChair("dirt_block"), false, "dirt_block is not a chair");

            // 2. Add and nearest search
            ChairRegistry.add(10, 64, 10);
            ChairRegistry.add(50, 64, 50);

            Vector3i nearest = ChairRegistry.findNearestUnoccupied(11, 64, 11, 10.0);
            Assert.isTrue(nearest != null, "Found nearby chair");
            Assert.equal(nearest.x, 10, "Nearest chair x");
            Assert.equal(nearest.z, 10, "Nearest chair z");

            // 3. Claim and occupancy
            UUID npc1 = UUID.randomUUID();
            UUID npc2 = UUID.randomUUID();

            boolean claimed = ChairRegistry.claimChair(nearest, npc1);
            Assert.equal(claimed, true, "NPC1 claimed chair");
            Assert.equal(ChairRegistry.isOccupied(nearest), true, "Chair is occupied");

            // Other NPC cannot claim same chair
            boolean claimFail = ChairRegistry.claimChair(nearest, npc2);
            Assert.equal(claimFail, false, "NPC2 cannot claim occupied chair");

            // Nearest search skips occupied chair
            Vector3i nextNearest = ChairRegistry.findNearestUnoccupied(11, 64, 11, 10.0);
            Assert.equal(nextNearest, null, "Occupied chair skipped in radius 10");

            // 4. Release chair
            ChairRegistry.releaseChair(nearest);
            Assert.equal(ChairRegistry.isOccupied(nearest), false, "Chair is free after release");

            // 5. Release all for NPC
            ChairRegistry.claimChair(new Vector3i(10, 64, 10), npc1);
            ChairRegistry.claimChair(new Vector3i(50, 64, 50), npc2);
            ChairRegistry.releaseAllForNpc(npc1);
            Assert.equal(ChairRegistry.isOccupied(new Vector3i(10, 64, 10)), false, "NPC1 chair released via releaseAllForNpc");
            Assert.equal(ChairRegistry.isOccupied(new Vector3i(50, 64, 50)), true, "NPC2 chair still occupied");

            ChairRegistry.clear();
            System.out.println("OK");
        } finally {
            ChairRegistry.staleCheckEnabled = true;
        }
    }

    private static void testFacialReactionJuice() {
        System.out.print("Testing Facial Reactions & Juice Helper... ");

        // Verify expression animation paths
        Assert.equal(SimTaleJuiceHelper.faceSmile(), "Characters/Animations/Expressions/Smile.blockyanim", "faceSmile path");
        Assert.equal(SimTaleJuiceHelper.faceCheerful(), "Characters/Animations/Expressions/Cheerful.blockyanim", "faceCheerful path");
        Assert.equal(SimTaleJuiceHelper.faceAngry(), "Characters/Animations/Expressions/Angry.blockyanim", "faceAngry path");
        Assert.equal(SimTaleJuiceHelper.faceFrown(), "Characters/Animations/Expressions/Frown.blockyanim", "faceFrown path");
        Assert.equal(SimTaleJuiceHelper.faceSurprised(), "Characters/Animations/Expressions/Suprised.blockyanim", "faceSurprised path");

        // Verify null safety of juice calls
        SimTaleJuiceHelper.spawnHeartParticles(null, null);
        SimTaleJuiceHelper.spawnAngryParticles(null, null);
        SimTaleJuiceHelper.playSoundSafe(null, null, null);
        SimTaleJuiceHelper.playSoundSafe("SimTale/TownBell", null, null);
        SimTaleJuiceHelper.playFlirtSuccess(null, null, null, null, 0);
        SimTaleJuiceHelper.playFlirtReject(null, null, null, null, 0);
        SimTaleJuiceHelper.playShove(null, null, null, null, null, 1.0f, 0);
        SimTaleJuiceHelper.playKiss(null, null, null);
        SimTaleJuiceHelper.playMarriageProposal(null, null, null);
        SimTaleJuiceHelper.playGreeting(null, null);
        SimTaleJuiceHelper.playJokeSuccess(null, null, null, 0);
        SimTaleJuiceHelper.playJokeFail(null, null, null, 0);
        SimTaleJuiceHelper.playGiftReaction(null, null, 25, null, 0);
        SimTaleJuiceHelper.playGiftReaction(null, null, -10, null, 0);
        SimTaleJuiceHelper.playGiftReaction(null, null, 5, null, 0);
        SimTaleJuiceHelper.playProfessionReaction(null, null, true, true, null, 0);
        SimTaleJuiceHelper.playProfessionReaction(null, null, true, false, null, 0);
        SimTaleJuiceHelper.playProfessionReaction(null, null, false, false, null, 0);
        SimTaleJuiceHelper.playDamagePanic(null, null, null, 0);

        System.out.println("OK");
    }

    private static void testHouseBedIndependence() {
        System.out.print("Testing House Independence & Bed Lifecycle... ");

        UUID houseUuid = UUID.randomUUID();
        UUID owner1 = UUID.randomUUID();
        UUID owner2 = UUID.randomUUID();

        HouseBlockPos bed1 = new HouseBlockPos(100, 65, 100);
        HouseBlockPos bed2 = new HouseBlockPos(104, 65, 100);
        HouseBlockPos door = new HouseBlockPos(102, 65, 96);
        HouseBlockPos chest = new HouseBlockPos(105, 65, 102);

        Set<HouseBlockPos> interior = new HashSet<>();
        for (int x = 98; x <= 106; x++) {
            for (int z = 97; z <= 105; z++) {
                interior.add(new HouseBlockPos(x, 65, z));
            }
        }

        // 1. HouseData creation and anchor calculation
        HouseData house = new HouseData(houseUuid, Set.of(owner1, owner2), bed1, interior, Set.of(door), Set.of(chest));
        Assert.equal(house.beds.contains(bed1), true, "Primary bed registered in beds set");
        Assert.equal(house.getAnchor() != null, true, "Anchor computed successfully");

        // 2. Add multiple beds to house
        house.addBed(bed2);
        Assert.equal(house.beds.size(), 2, "House has 2 beds");
        Assert.equal(house.beds.contains(bed2), true, "Second bed registered");

        // 3. Register house in HouseManager
        HouseManager.registerHouse(house);
        Assert.equal(HouseManager.HOUSES_BY_ID.containsKey(houseUuid), true, "House registered in HouseManager");
        Assert.equal(HouseManager.BLOCK_TO_HOUSE_ID.get(interior.iterator().next()), houseUuid, "Interior indexed to houseId");

        // 4. Setup mock NPC resident sleeping in bed1
        SimNPCComponent npc = new SimNPCComponent();
        npc.entityId = owner1;
        npc.name = "Resident";
        npc.bedLocation = new SimBedData.BedPos(bed1.x, bed1.y, bed1.z, 0f);
        npc.family.hasSharedHome = true;
        npc.family.homeX = bed1.x;
        npc.family.homeY = bed1.y;
        npc.family.homeZ = bed1.z;
        SimTale.ACTIVE_NPCS.add(npc);

        try {
            // 5. Break bed1: verify house is NOT deleted and NPC switches to alternate bed2
            boolean broken = HouseManager.handleBedBroken(bed1);
            Assert.equal(broken, true, "handleBedBroken returned true");
            Assert.equal(HouseManager.HOUSES_BY_ID.containsKey(houseUuid), true, "House STILL exists after bed broke!");
            Assert.equal(house.beds.contains(bed1), false, "bed1 removed from house.beds");
            Assert.equal(house.beds.contains(bed2), true, "bed2 remains in house.beds");
            Assert.equal(npc.bedLocation != null, true, "NPC got assigned alternate bed2");
            Assert.equal(npc.bedLocation.x == bed2.x && npc.bedLocation.z == bed2.z, true, "NPC bedLocation updated to bed2");

            // 6. Break bed2: house has 0 beds, but house remains registered!
            HouseManager.handleBedBroken(bed2);
            Assert.equal(HouseManager.HOUSES_BY_ID.containsKey(houseUuid), true, "House STILL exists with 0 beds!");
            Assert.equal(house.beds.isEmpty(), true, "House has 0 beds");
            Assert.equal(npc.bedLocation, null, "NPC has null bedLocation when no beds available");

            // 7. Place new bed3 inside house: dynamically adopted
            HouseBlockPos bed3 = new HouseBlockPos(101, 65, 101);
            boolean registeredNewBed = HouseManager.registerBedInHouse(bed3);
            Assert.equal(registeredNewBed, true, "New bed3 registered into existing house");
            Assert.equal(house.beds.contains(bed3), true, "house.beds now contains bed3");

            // 8. Door broken: door removed, house still preserved
            boolean doorBroken = HouseManager.handleDoorBroken(door);
            Assert.equal(doorBroken, true, "Door break handled");
            Assert.equal(house.doors.isEmpty(), true, "Door removed from house.doors");
            Assert.equal(HouseManager.HOUSES_BY_ID.containsKey(houseUuid), true, "House still preserved");

            // 9. Legacy syncBeds test: loads record with only bedPos
            HouseData legacy = new HouseData();
            legacy.houseId = UUID.randomUUID().toString();
            legacy.bedPos = new HouseBlockPos(200, 64, 200);
            legacy.syncBeds();
            Assert.equal(legacy.beds.contains(legacy.bedPos), true, "syncBeds populated beds set from legacy bedPos");
            Assert.equal(legacy.getAnchor() != null, true, "legacy getAnchor resolved");

            // Cleanup
            HouseManager.deleteHouse(houseUuid);
            Assert.equal(HouseManager.HOUSES_BY_ID.containsKey(houseUuid), false, "Explicit deleteHouse removed house");
            System.out.println("OK");
        } finally {
            SimTale.ACTIVE_NPCS.remove(npc);
            HouseManager.deleteHouse(houseUuid);
        }
    }

    private static void testVillageStockAndSharedChests() {
        System.out.print("Testing Village Stock & Shared Chests... ");

        // 1. ChestData with Caskara compatibility
        ChestData cdPrivate = new ChestData(10, 64, 10, false);
        Assert.equal(cdPrivate.shared, false, "Private chest data");
        Assert.equal(cdPrivate.id, "10_64_10", "ChestData id key");

        ChestData cdShared = new ChestData(12, 64, 12, true);
        Assert.equal(cdShared.shared, true, "Shared chest data");

        // 2. ChestRegistry shared state
        ChestRegistry.setShared(50, 64, 50, true);
        Assert.equal(ChestRegistry.isShared(50, 64, 50), true, "Chest (50,64,50) is shared");
        Assert.equal(ChestRegistry.isShared(new HouseBlockPos(50, 64, 50)), true, "Chest isShared via HouseBlockPos");
        Assert.equal(ChestRegistry.isShared(10, 64, 10), false, "Chest (10,64,10) is not shared");

        // 3. canOpenChest permissions
        UUID npc1 = UUID.randomUUID();
        UUID npc2 = UUID.randomUUID();

        // Shared chest can be opened by any NPC
        HouseBlockPos sharedPos = new HouseBlockPos(50, 64, 50);
        Assert.equal(HouseManager.canOpenChest(npc1, sharedPos), true, "NPC1 can open shared chest");
        Assert.equal(HouseManager.canOpenChest(npc2, sharedPos), true, "NPC2 can open shared chest");

        // Unshared chest outside any house cannot be opened
        HouseBlockPos randomPos = new HouseBlockPos(999, 64, 999);
        ChestRegistry.add(999, 64, 999);
        Assert.equal(HouseManager.canOpenChest(npc1, randomPos), false, "Random unshared chest cannot be opened");

        // 4. VillageStockManager helpers
        SimNPCComponent villager = new SimNPCComponent();
        villager.entityId = npc1;
        villager.bedLocation = new SimBedData.BedPos(52, 64, 52, 0f);

        VillageManager.Village village = new VillageManager.Village(50.0, 50.0, 30.0, 1);
        Assert.equal(VillageStockManager.isNpcInVillage(villager, village), true, "Villager is in village");

        List<HouseBlockPos> sharedInVillage = VillageStockManager.getSharedChestsInVillage(village);
        Assert.equal(sharedInVillage.contains(sharedPos), true, "Village contains shared chest");

        // 5. Cleanup
        ChestRegistry.removeAt(50, 64, 50);
        ChestRegistry.removeAt(999, 64, 999);
        Assert.equal(ChestRegistry.isShared(50, 64, 50), false, "Shared chest removed");

        System.out.println("OK");
    }

    /* Kept as thin wrappers so the pre-existing suites read unchanged, while the assertion count
    reported at the end covers everything rather than only the newer files.
    */
    private static void assertEqual(Object actual, Object expected, String message) {
        Assert.equal(actual, expected, message);
    }

    private static void assertFloatEqual(float actual, float expected, String message) {
        Assert.floatEqual(actual, expected, message);
    }

}
