package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.cookieukw.SimTale.core.HouseBlockPos;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.MemoryEvent;
import com.cookieukw.SimTale.core.NeedsHelper;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.WorldUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.asset.type.model.config.Model.ModelReference;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.LinkedHashMap;
import java.util.Map;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.cookieukw.SimTale.core.SimLog;

public class NPCWorkHelper {

    private static final SimLog LOGGER = SimLog.forClass(NPCWorkHelper.class);

    private static final int GATHER_WORK_DURATION_TICKS = 40; // 2 seconds
    private static final double WORK_REACH_DISTANCE_SQ = 2.5 * 2.5;

    /**
     * Give up walking to a crop/animal/chest after 30s. None of the MOVING_* states had a
     * deadline, so an unreachable target parked the NPC permanently in that state.
     */
    private static final int MOVE_TIMEOUT_TICKS = 600;

    private static final String ANIM_WALK = "Characters/Animations/Actions/Walk.blockyanim";
    private static final String ANIM_SMITH = "Characters/Animations/Actions/Smith.blockyanim";
    private static final String ANIM_IDLE = "Characters/Animations/Actions/Idle.blockyanim";

    // keyword -> value, checked in insertion order; falls back to the first entry's value if nothing matches.
    //
    // NOTE: Hytale item ids carry no namespace — `item_ids.txt` lists 3690 entries and not one
    // uses a "hytale:" prefix. Every id below used to be prefixed, so none of them resolved:
    // harvesting handed out a nonexistent item and planting placed a nonexistent block. The
    // food ids were doubly wrong ("food_carrot" does not exist in any casing); the real
    // harvested crop is `Plant_Crop_<Name>_Item`.
    private static final Map<String, String> CROP_TO_FOOD = orderedMap(
            "carrot", "Plant_Crop_Carrot_Item",
            "wheat", "Plant_Crop_Wheat_Item",
            "tomato", "Plant_Crop_Tomato_Item",
            "corn", "Plant_Crop_Corn_Item"
    );

    private static final Map<String, String> CROP_TO_SEED = orderedMap(
            "carrot", "Plant_Seeds_Carrot",
            "wheat", "Plant_Seeds_Wheat",
            "tomato", "Plant_Seeds_Tomato",
            "corn", "Plant_Seeds_Corn"
    );

    private static final Map<String, String> SEED_TO_CROP_BLOCK = orderedMap(
            "carrot", "Plant_Crop_Carrot_Block",
            "wheat", "Plant_Crop_Wheat_Block",
            "tomato", "Plant_Crop_Tomato_Block",
            "corn", "Plant_Crop_Corn_Block"
    );

    /** Hunt expedition reward table — no real animal is tracked anymore, so this is just a
     *  weighted roll instead of a model-id lookup. */
    private static final String[] HUNT_ITEMS = {
            "Food_Pork_Raw", "Food_Beef_Raw", "Food_Chicken_Raw", "Food_Wildmeat_Raw"
    };

    /** Mining expedition reward table, ordered common to rare. */
    private static final String[] ORE_ITEMS = {
            "Ore_Copper", "Ore_Copper", "Ore_Copper",
            "Ore_Iron", "Ore_Iron", "Ore_Iron",
            "Ore_Silver", "Ore_Silver",
            "Ore_Gold",
            "Ore_Cobalt",
            "Ore_Mithril",
            "Ore_Thorium",
            "Ore_Adamantite"
    };

    /** Block id used to clear a position, matching the convention in ConstructionSystem. */
    private static final String EMPTY_BLOCK = "Empty";

    /** Fish catch table, ordered common to rare — see {@link #rollFish()}. */
    private static final String[] FISH_ITEMS = {
            "Food_Fish_Raw", "Food_Fish_Raw", "Food_Fish_Raw",
            "Food_Fish_Raw_Uncommon", "Food_Fish_Raw_Uncommon",
            "Food_Fish_Raw_Rare",
            "Food_Fish_Raw_Epic"
    };

    /** Weighted random catch — mostly common fish, occasionally something better. */
    private static String rollFish() {
        return FISH_ITEMS[(int) (Math.random() * FISH_ITEMS.length)];
    }

    private static String rollHunt() {
        return HUNT_ITEMS[(int) (Math.random() * HUNT_ITEMS.length)];
    }

    private static String rollOre() {
        return ORE_ITEMS[(int) (Math.random() * ORE_ITEMS.length)];
    }

    /** How long an EXPEDITION lasts — long enough to read as "went out and came back", short
     *  enough not to seriously delay sleep even though the state is protected from interruption. */
    private static final int EXPEDITION_DURATION_TICKS = 2400; // ~2 minutes

    /** Scale swapped in for the duration — there is no invisibility flag on the engine, so a
     *  near-zero model stands in for "not here" instead. */
    private static final float EXPEDITION_SCALE = 0.001f;
    private static final float NORMAL_NPC_SCALE = 1.0f;

    /**
     * Hunter and Miner share this: no real-time chase/dig, no risk of the NPC wandering into a
     * hostile mob or falling down a hole — it just leaves for a while and comes back with
     * something. Doesn't start right as the sleep window is opening, so it can't seriously delay
     * bedtime; once started it runs to completion (protected in RoutineAISystem's interrupt
     * check), because an interrupt mid-expedition would leave the shrink applied permanently.
     */
    private static void startExpedition(Ref<EntityStore> ref, RoutineAIComponent ai, SimNPCComponent npc, World world, Store<EntityStore> store) {
        if (NPCSleepHelper.isSleepPeriod(npc, world)) return;

        PersistentModel pm = store.getComponent(ref, PersistentModel.getComponentType());
        if (pm != null) {
            ModelReference oldRef = pm.getModelReference();
            // replaceComponent is a structural write — same "Store is currently processing!"
            // issue as spawnNPC earlier this session. This runs from inside RoutineAISystem's own
            // tick, so it has to be deferred, not called straight from here.
            WorldUtil.execute(() -> store.replaceComponent(ref, PersistentModel.getComponentType(),
                    new PersistentModel(new ModelReference(oldRef.getModelAssetId(), EXPEDITION_SCALE, new LinkedHashMap<>()))));
        }

        NPCMovementHelper.clearMoveTarget(ref, ai);
        ai.currentTask = TaskType.EXPEDITION;
        ai.taskStartTime = world.getTick();
    }

    /**
     * Mood payoff for finishing a task. An NPC whose hobby lines up with its profession
     * genuinely enjoys the work and gets a little {@code fun} out of it; one whose hobby has
     * nothing to do with the job just gets it over with.
     * <p>
     * This is the only place where the two systems meet — before it, an NPC could spend its
     * whole life farming while its rolled hobby said it would rather be fishing, and nothing
     * in the simulation noticed.
     */
    private static void applyWorkSatisfaction(SimNPCComponent npc, long tick) {
        // Work naturally drops fun, unless it's a good mood/traits combo
        if (npc.activeEmotion == Mood.HAPPY) {
            NeedsHelper.setNeed(null, npc.entityRef, NeedsHelper.FUN_ID, Math.min(100f, NeedsHelper.getNeed(null, npc.entityRef, NeedsHelper.FUN_ID) + 6f));
        } else {
            NeedsHelper.setNeed(null, npc.entityRef, NeedsHelper.FUN_ID, Math.max(0f, NeedsHelper.getNeed(null, npc.entityRef, NeedsHelper.FUN_ID) - 1.5f));
        }
    }

    public static void handleWorkLogic(
            Ref<EntityStore> ref,
            SimNPCComponent npc,
            RoutineAIComponent ai,
            TransformComponent transform,
            World world,
            Store<EntityStore> store
    ) {
        // Evaluate Transition to Work/Deposit from IDLE
        if (ai.currentTask == TaskType.IDLE && (npc.profession == Profession.FARMER || npc.profession == Profession.HUNTER || npc.profession == Profession.FISHERMAN || npc.profession == Profession.LUMBERJACK || npc.profession == Profession.MINER)) {
            ItemContainer inventory = getInventory(store, ref);
            boolean hasItemsToDeposit = hasAnyItem(inventory);

            if (hasItemsToDeposit) {
                // Find a chest owned by NPC to deposit items
                HouseBlockPos depositChest = findHomeChest(npc);
                if (depositChest != null) {
                    ai.targetBlockPosition = new Vector3i(depositChest.x, depositChest.y, depositChest.z);
                    ai.currentTask = TaskType.MOVING_TO_DEPOSIT;
                    // Stamp the entry tick; the state had no deadline of its own before.
                    ai.taskStartTime = world.getTick();
                    playWalk(ref, store);
                    return;
                }
            }

            // Stagger scans (e.g. random chance or time check). Bypassed immediately when a debug
            // command just forced this NPC — reset centrally in RoutineAISystem right after this
            // call returns, not here, so every caller of the flag gets cleared, not just this one.
            if (world.getTick() % 100 == 0 || ai.forcedByDebug) {
                if (npc.profession == Profession.FARMER) {
                    // A registered Deco_Scarecrow plot lets her find work from anywhere, not only
                    // within scanning range of wherever she happens to be standing. Falls back to
                    // scanning around her own position when no plot is registered (or every plot
                    // is already claimed), so farming still works without placing a scarecrow.
                    Vector3d npcPos = transform.getPosition();
                    Vector3d scanCenter = npcPos;
                    FarmPostRegistry.FarmPost claimedPost = FarmPostRegistry.claimNearest(npcPos.x, npcPos.y, npcPos.z, npc.entityId);
                    if (claimedPost != null) {
                        scanCenter = new Vector3d(claimedPost.postX() + 0.5, claimedPost.postY(), claimedPost.postZ() + 0.5);
                    }

                    // Try to harvest first
                    Vector3i cropPos = scanForCrops(scanCenter);
                    if (cropPos != null) {
                        ai.targetBlockPosition = cropPos;
                        if (claimedPost != null) {
                            ai.claimedWorkPost = new Vector3i(claimedPost.postX(), claimedPost.postY(), claimedPost.postZ());
                        }
                        ai.currentTask = TaskType.MOVING_TO_WORK;
                        ai.taskStartTime = world.getTick();
                        playWalk(ref, store);
                    } else {
                        String seed = findSeedInInventory(inventory);
                        Vector3i farmPos = seed != null ? scanForFarmland(scanCenter, world) : null;
                        if (farmPos != null) {
                            ai.targetBlockPosition = farmPos;
                            if (claimedPost != null) {
                                ai.claimedWorkPost = new Vector3i(claimedPost.postX(), claimedPost.postY(), claimedPost.postZ());
                            }
                            ai.currentTask = TaskType.MOVING_TO_WORK;
                            ai.taskStartTime = world.getTick();
                            playWalk(ref, store);
                        } else if (claimedPost != null) {
                            // Nothing to do at this plot right now — don't sit on the claim,
                            // another farmer (or this one, next cycle) might find work there.
                            FarmPostRegistry.release(claimedPost.postX(), claimedPost.postY(), claimedPost.postZ(), npc.entityId);
                        }
                    }
                } else if (npc.profession == Profession.HUNTER || npc.profession == Profession.MINER) {
                    startExpedition(ref, ai, npc, world, store);
                } else if (npc.profession == Profession.FISHERMAN) {
                    // Water was already resolved once, when the fishing post was placed — no
                    // scan needed here, just look the post up. One NPC per post at a time.
                    Vector3d pos = transform.getPosition();
                    FishingPostRegistry.FishingPost post = FishingPostRegistry.claimNearest(pos.x, pos.y, pos.z, npc.entityId);
                    if (post != null) {
                        ai.targetBlockPosition = new Vector3i(post.waterX(), post.waterY(), post.waterZ());
                        ai.claimedWorkPost = new Vector3i(post.postX(), post.postY(), post.postZ());
                        ai.currentTask = TaskType.MOVING_TO_WORK;
                        ai.taskStartTime = world.getTick();
                        playWalk(ref, store);
                    }
                } else if (npc.profession == Profession.LUMBERJACK) {
                    // Same trade as fishing: the tree was already found when the lumbermill was
                    // placed, so this is a lookup, not a scan.
                    Vector3d pos = transform.getPosition();
                    LumberPostRegistry.LumberPost post = LumberPostRegistry.claimNearest(pos.x, pos.y, pos.z, npc.entityId);
                    if (post != null) {
                        ai.targetBlockPosition = new Vector3i(post.treeX(), post.treeY(), post.treeZ());
                        ai.claimedWorkPost = new Vector3i(post.postX(), post.postY(), post.postZ());
                        ai.currentTask = TaskType.MOVING_TO_WORK;
                        ai.taskStartTime = world.getTick();
                        playWalk(ref, store);
                    }
                }
            }
        }

        // MOVING_TO_WORK
        if (ai.currentTask == TaskType.MOVING_TO_WORK) {
            if (world.getTick() - ai.taskStartTime > MOVE_TIMEOUT_TICKS) {
                LOGGER.debug("[SimTale] NPC {} desistiu de chegar ao alvo de trabalho", npc.name);
                abandonTask(ref, ai, npc);
                return;
            }
            Vector3d npcPos = transform.getPosition();
            if (npc.profession == Profession.FARMER) {
                if (ai.targetBlockPosition == null) {
                    // Previously silent — an NPC could drop from MOVING_TO_WORK straight to IDLE
                    // (and from there get picked up by the 2% WANDERING roll seconds later) with
                    // no trace in the log of why the target it had just been given vanished.
                    LOGGER.debug("[SimTale] Farmer NPC {} lost its work target mid-walk (targetBlockPosition null), tick={}", npc.name, world.getTick());
                    ai.currentTask = TaskType.IDLE;
                    return;
                }
                if (isNear(npcPos, ai.targetBlockPosition.x + 0.5, ai.targetBlockPosition.z + 0.5)) {
                    NPCMovementHelper.clearMoveTarget(ref, ai);
                    // Determine if harvesting or planting
                    BlockType blockType = world.getBlockType(ai.targetBlockPosition.x, ai.targetBlockPosition.y, ai.targetBlockPosition.z);
                    String blockId = blockType != null ? blockType.getId() : null;
                    if (blockId != null && blockId.toLowerCase().contains("crop")) {
                        ai.currentTask = TaskType.FARMING;
                    } else {
                        ai.currentTask = TaskType.PLANTING;
                    }
                    LOGGER.debug("[SimTale] Farmer NPC {} arrived at target {} (blockId='{}') -> entering {}", npc.name, ai.targetBlockPosition, blockId, ai.currentTask);
                    ai.taskStartTime = world.getTick();
                } else {
                    NPCMovementHelper.moveTo(ref, ai, world, new Vector3d(ai.targetBlockPosition.x + 0.5, npcPos.y, ai.targetBlockPosition.z + 0.5));
                }
            } else if (npc.profession == Profession.FISHERMAN) {
                if (ai.targetBlockPosition == null) { ai.currentTask = TaskType.IDLE; return; }
                // Approach from beside the water, not standing inside it.
                if (isNear(npcPos, ai.targetBlockPosition.x + 0.5, ai.targetBlockPosition.z + 0.5)) {
                    NPCMovementHelper.clearMoveTarget(ref, ai);
                    ai.currentTask = TaskType.FISHING;
                    ai.taskStartTime = world.getTick();
                } else {
                    NPCMovementHelper.moveTo(ref, ai, world, new Vector3d(ai.targetBlockPosition.x + 0.5, npcPos.y, ai.targetBlockPosition.z + 0.5));
                }
            } else if (npc.profession == Profession.LUMBERJACK) {
                if (ai.targetBlockPosition == null) { ai.currentTask = TaskType.IDLE; return; }
                if (isNear(npcPos, ai.targetBlockPosition.x + 0.5, ai.targetBlockPosition.z + 0.5)) {
                    NPCMovementHelper.clearMoveTarget(ref, ai);
                    ai.currentTask = TaskType.CHOPPING;
                    ai.taskStartTime = world.getTick();
                } else {
                    NPCMovementHelper.moveTo(ref, ai, world, new Vector3d(ai.targetBlockPosition.x + 0.5, npcPos.y, ai.targetBlockPosition.z + 0.5));
                }
            }
        }

        // FARMING State
        if (ai.currentTask == TaskType.FARMING) {
            if (ai.targetBlockPosition == null) { ai.currentTask = TaskType.IDLE; return; }
            if (world.getTick() - ai.taskStartTime == 1) {
                playSmith(ref, store);
            }

            if (world.getTick() - ai.taskStartTime >= GATHER_WORK_DURATION_TICKS) {
                Vector3i cropPos = ai.targetBlockPosition;
                BlockType blockType = world.getBlockType(cropPos.x, cropPos.y, cropPos.z);
                if (blockType != null && blockType.getId() != null && blockType.getId().toLowerCase().contains("crop")) {
                    String cropId = blockType.getId();
                    // Replace with empty
                    world.setBlock(cropPos.x, cropPos.y, cropPos.z, EMPTY_BLOCK);
                    CropRegistry.removeAt(cropPos.x, cropPos.y, cropPos.z);

                    // Map to food item & seed item
                    String meatOrVeg = lookup(CROP_TO_FOOD, cropId, "Plant_Crop_Carrot_Item");
                    String seedItem = lookup(CROP_TO_SEED, cropId, "Plant_Seeds_Carrot");
                    ItemContainer inv = getInventory(store, ref);
                    if (inv != null) {
                        // Checked, not blind-added: an add into a full inventory silently drops
                        // the item while this log line still claimed success every time.
                        ItemStack produce = new ItemStack(meatOrVeg, 1);
                        if (inv.canAddItemStack(produce)) {
                            inv.addItemStack(produce);
                        } else {
                            LOGGER.debug("[SimTale] Farmer NPC {} harvested {} but inventory is full — lost", npc.name, meatOrVeg);
                        }

                        // 100% chance to drop 1-2 seeds
                        int seedAmount = 1 + (int)(Math.random() * 2);
                        ItemStack seeds = new ItemStack(seedItem, seedAmount);
                        if (inv.canAddItemStack(seeds)) {
                            inv.addItemStack(seeds);
                        } else {
                            LOGGER.debug("[SimTale] Farmer NPC {} harvested {} seeds but inventory is full — lost", npc.name, seedAmount);
                        }

                        LOGGER.debug("[SimTale] Farmer NPC {} harvested crop {} (gained {} seeds)", npc.name, cropId, seedAmount);
                    }
                }
                applyWorkSatisfaction(npc, world.getTick());
                releaseWorkPost(ai, npc);
                ai.currentTask = TaskType.IDLE;
                playIdleAnim(ref, store);
            }
        }

        // PLANTING State
        if (ai.currentTask == TaskType.PLANTING) {
            if (ai.targetBlockPosition == null) { ai.currentTask = TaskType.IDLE; return; }
            if (world.getTick() - ai.taskStartTime == 1) {
                playSmith(ref, store);
            }

            if (world.getTick() - ai.taskStartTime >= GATHER_WORK_DURATION_TICKS) {
                Vector3i plantPos = ai.targetBlockPosition;
                ItemContainer inv = getInventory(store, ref);
                if (inv != null) {
                    String seed = findSeedInInventory(inv);
                    if (seed != null) {
                        short slot = findSeedSlot(inv, seed);
                        if (slot != -1) {
                            inv.removeItemStackFromSlot(slot, 1);
                            String cropBlock = getCropBlockFromSeed(seed);
                            world.setBlock(plantPos.x, plantPos.y, plantPos.z, cropBlock);
                            // world.setBlock is a raw storage write with no event dispatch —
                            // unlike a hand-placed crop (registered by BedPlaceBlockEventSystem
                            // reacting to the engine's PlaceBlockEvent), this block is invisible
                            // to CropRegistry unless registered here explicitly. Without this,
                            // the plot was farmland (empty tile) right up until the NPC's own
                            // planting made it neither farmland nor a trackable crop — permanently
                            // dead after the first auto-replant.
                            CropRegistry.add(plantPos.x, plantPos.y, plantPos.z);
                            LOGGER.debug("[SimTale] Farmer NPC {} planted {} at {}", npc.name, cropBlock, plantPos);
                        } else {
                            LOGGER.debug("[SimTale] Farmer NPC {} PLANTING failed at {}: findSeedInInventory returned '{}' but findSeedSlot could not locate it", npc.name, plantPos, seed);
                        }
                    } else {
                        LOGGER.debug("[SimTale] Farmer NPC {} PLANTING failed at {}: no seed found in inventory", npc.name, plantPos);
                    }
                } else {
                    LOGGER.debug("[SimTale] Farmer NPC {} PLANTING failed at {}: getInventory returned null", npc.name, plantPos);
                }
                applyWorkSatisfaction(npc, world.getTick());
                releaseWorkPost(ai, npc);
                ai.currentTask = TaskType.IDLE;
                playIdleAnim(ref, store);
            }
        }


        // FISHING State
        if (ai.currentTask == TaskType.FISHING) {
            if (ai.targetBlockPosition == null) { ai.currentTask = TaskType.IDLE; return; }
            if (world.getTick() - ai.taskStartTime == 1) {
                playSmith(ref, store);
            }

            if (world.getTick() - ai.taskStartTime >= GATHER_WORK_DURATION_TICKS) {
                String fishId = rollFish();
                ItemContainer inv = getInventory(store, ref);
                if (inv != null) {
                    ItemStack fish = new ItemStack(fishId, 1);
                    if (inv.canAddItemStack(fish)) {
                        inv.addItemStack(fish);
                        LOGGER.debug("[SimTale] Fisherman NPC {} caught {}", npc.name, fishId);
                    } else {
                        LOGGER.debug("[SimTale] Fisherman NPC {} caught {} but inventory is full — lost", npc.name, fishId);
                    }
                }
                applyWorkSatisfaction(npc, world.getTick());
                releaseWorkPost(ai, npc);
                ai.currentTask = TaskType.IDLE;
                playIdleAnim(ref, store);
            }
        }

        // CHOPPING State
        if (ai.currentTask == TaskType.CHOPPING) {
            if (ai.targetBlockPosition == null) { ai.currentTask = TaskType.IDLE; return; }
            if (world.getTick() - ai.taskStartTime == 1) {
                playSmith(ref, store);
            }

            if (world.getTick() - ai.taskStartTime >= GATHER_WORK_DURATION_TICKS) {
                Vector3i treePos = ai.targetBlockPosition;
                BlockType blockType = world.getBlockType(treePos.x, treePos.y, treePos.z);
                if (LumberPostRegistry.isTreeTrunk(blockType)) {
                    // The trunk block id doubles as the item id — no CROP_TO_FOOD-style lookup
                    // table exists per species, and none is needed.
                    String logId = blockType.getId();
                    world.setBlock(treePos.x, treePos.y, treePos.z, EMPTY_BLOCK);
                    // The post this NPC used pointed at exactly this trunk; it's gone now, so the
                    // post has to go looking again next time (a re-placed or new lumbermill).
                    LumberPostRegistry.removeByTree(treePos.x, treePos.y, treePos.z);

                    ItemContainer inv = getInventory(store, ref);
                    if (inv != null) {
                        ItemStack log = new ItemStack(logId, 1);
                        if (inv.canAddItemStack(log)) {
                            inv.addItemStack(log);
                            LOGGER.debug("[SimTale] Lumberjack NPC {} chopped {}", npc.name, logId);
                        } else {
                            LOGGER.debug("[SimTale] Lumberjack NPC {} chopped {} but inventory is full — lost", npc.name, logId);
                        }
                    }
                } else {
                    // Someone else got here first (player, or another lumberjack before the
                    // registry caught up) — nothing to chop, just stop cleanly.
                    LOGGER.debug("[SimTale] Lumberjack NPC {} arrived at ({},{},{}) but it wasn't a tree anymore", npc.name, treePos.x, treePos.y, treePos.z);
                }
                applyWorkSatisfaction(npc, world.getTick());
                releaseWorkPost(ai, npc);
                ai.currentTask = TaskType.IDLE;
                playIdleAnim(ref, store);
            }
        }

        // EXPEDITION State — Hunter/Miner, see startExpedition() for why there's no travel here.
        if (ai.currentTask == TaskType.EXPEDITION) {
            if (world.getTick() - ai.taskStartTime >= EXPEDITION_DURATION_TICKS) {
                PersistentModel pm = store.getComponent(ref, PersistentModel.getComponentType());
                if (pm != null) {
                    ModelReference oldRef = pm.getModelReference();
                    WorldUtil.execute(() -> store.replaceComponent(ref, PersistentModel.getComponentType(),
                            new PersistentModel(new ModelReference(oldRef.getModelAssetId(), NORMAL_NPC_SCALE, new LinkedHashMap<>()))));
                }

                String rewardId = npc.profession == Profession.MINER ? rollOre() : rollHunt();
                ItemContainer inv = getInventory(store, ref);
                if (inv != null) {
                    ItemStack reward = new ItemStack(rewardId, 1);
                    if (inv.canAddItemStack(reward)) {
                        inv.addItemStack(reward);
                        LOGGER.debug("[SimTale] NPC {} returned from expedition with {}", npc.name, rewardId);
                    } else {
                        LOGGER.debug("[SimTale] NPC {} returned from expedition with {} but inventory is full — lost", npc.name, rewardId);
                    }
                }
                applyWorkSatisfaction(npc, world.getTick());
                ai.currentTask = TaskType.IDLE;
                playIdleAnim(ref, store);
            }
        }

        // MOVING_TO_DEPOSIT
        if (ai.currentTask == TaskType.MOVING_TO_DEPOSIT) {
            if (ai.targetBlockPosition == null) { ai.currentTask = TaskType.IDLE; return; }
            if (world.getTick() - ai.taskStartTime > MOVE_TIMEOUT_TICKS) {
                LOGGER.debug("[SimTale] NPC {} desistiu de chegar ao bau de deposito", npc.name);
                abandonTask(ref, ai, npc);
                return;
            }
            Vector3d npcPos = transform.getPosition();
            if (isNear(npcPos, ai.targetBlockPosition.x + 0.5, ai.targetBlockPosition.z + 0.5)) {
                NPCMovementHelper.clearMoveTarget(ref, ai);

                // Deposit items to chest
                Vector3i chestPos = ai.targetBlockPosition;
                ItemContainerBlock cb = BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, chestPos.x, chestPos.y, chestPos.z);
                if (cb != null) {
                    ItemContainer chestInv = cb.getItemContainer();
                    ItemContainer npcInv = getInventory(store, ref);
                    if (chestInv != null && npcInv != null) {
                        int moved = 0;
                        for (short slot = 0; slot < npcInv.getCapacity(); slot++) {
                            ItemStack item = npcInv.getItemStack(slot);
                            if (item == null || item.isEmpty()) continue;

                            // Copy first and check the chest has room, then clear the NPC slot.
                            // The old order removed the item from the NPC and handed the
                            // now-emptied reference to the chest, destroying the loot whenever
                            // the chest was full.
                            ItemStack toDeposit = new ItemStack(item.getItemId(), item.getQuantity());
                            if (!chestInv.canAddItemStack(toDeposit)) continue;

                            chestInv.addItemStack(toDeposit);
                            npcInv.removeItemStackFromSlot(slot, item.getQuantity());
                            moved++;
                        }
                        LOGGER.debug("[SimTale] NPC {} deposited {} stack(s) into chest at {}", npc.name, moved, chestPos);
                    }
                }
                ai.currentTask = TaskType.IDLE;
            } else {
                NPCMovementHelper.moveTo(ref, ai, world, new Vector3d(ai.targetBlockPosition.x + 0.5, npcPos.y, ai.targetBlockPosition.z + 0.5));
            }
        }
    }

    /** Drops the current movement goal and returns the NPC to IDLE. */
    private static void abandonTask(Ref<EntityStore> ref, RoutineAIComponent ai, SimNPCComponent npc) {
        NPCMovementHelper.clearMoveTarget(ref, ai);
        ai.targetBlockPosition = null;
        ai.workTargetEntityId = null;
        releaseWorkPost(ai, npc);
        ai.currentTask = TaskType.IDLE;
    }

    /** Releases whatever work post this NPC is holding, if any — called on both give-up and
     *  successful completion so a claimed post never outlives the task that claimed it. Also
     *  called from {@code RoutineAISystem} the moment an NPC enters the death flow, so a killed
     *  fisherman doesn't leave its post permanently reserved. */
    public static void releaseWorkPost(RoutineAIComponent ai, SimNPCComponent npc) {
        if (ai.claimedWorkPost == null) return;
        if (npc != null && npc.entityId != null) {
            // claimedWorkPost doesn't record which registry it came from, and release() on the
            // wrong one is a harmless no-op (it only removes an entry that matches both the exact
            // position and this NPC's id) — simpler than threading the profession through here.
            FishingPostRegistry.release(ai.claimedWorkPost.x, ai.claimedWorkPost.y, ai.claimedWorkPost.z, npc.entityId);
            LumberPostRegistry.release(ai.claimedWorkPost.x, ai.claimedWorkPost.y, ai.claimedWorkPost.z, npc.entityId);
            FarmPostRegistry.release(ai.claimedWorkPost.x, ai.claimedWorkPost.y, ai.claimedWorkPost.z, npc.entityId);
        }
        ai.claimedWorkPost = null;
    }

    // ── Animation shortcuts ──────────────────────────────────────────────────

    private static void playWalk(Ref<EntityStore> ref, Store<EntityStore> store) {
        NPCMovementHelper.playAnim(ref, ANIM_WALK, "Walk", store);
    }

    private static void playSmith(Ref<EntityStore> ref, Store<EntityStore> store) {
        NPCMovementHelper.playAnim(ref, ANIM_SMITH, "Smith", store);
    }

    private static void playIdleAnim(Ref<EntityStore> ref, Store<EntityStore> store) {
        NPCMovementHelper.playAnim(ref, ANIM_IDLE, "Idle", store);
    }

    // ── Inventory / proximity helpers ────────────────────────────────────────

    /** NPCs spawned by the engine's own NPCPlugin get a Storage component backed by
     *  EmptyItemContainer (capacity 0) — fine for vanilla mobs, useless for a profession that
     *  needs to carry seeds/tools/harvest. SimNPCFactory now allocates real capacity for NPCs
     *  created from here on, but existing NPCs (persisted entities from before that fix, or ones
     *  created some other way) keep whatever Storage they were spawned with. Self-heal it here
     *  the same way BedWorldBootstrap self-heals the work-post registries. */
    public static ItemContainer getInventory(Store<EntityStore> store, Ref<EntityStore> ref) {
        InventoryComponent.Storage storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        if (storage == null) return null;
        ItemContainer inv = storage.getInventory();
        if (inv != null && inv.getCapacity() <= 0) {
            InventoryComponent.Storage fixed = new InventoryComponent.Storage((short) 20);
            WorldUtil.execute(() -> store.replaceComponent(ref, InventoryComponent.Storage.getComponentType(), fixed));
            LOGGER.debug("[SimTale] Healed zero-capacity inventory for entity {}", ref);
            return fixed.getInventory();
        }
        return inv;
    }

    private static boolean hasAnyItem(ItemContainer container) {
        if (container == null) return false;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack item = container.getItemStack(slot);
            if (item != null && !item.isEmpty()) return true;
        }
        return false;
    }

    /**
     *
     */
    private static boolean isNear(Vector3d pos, double targetX, double targetZ) {
        double dx = targetX - pos.x;
        double dz = targetZ - pos.z;
        return dx * dx + dz * dz < NPCWorkHelper.WORK_REACH_DISTANCE_SQ;
    }

    // ── Crop / seed / animal keyword lookups ─────────────────────────────────

    private static Map<String, String> orderedMap(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    /** Returns the value of the first entry whose key is contained in {@code id} (case-insensitive), or {@code fallback}. */
    private static String lookup(Map<String, String> table, String id, String fallback) {
        String lower = id.toLowerCase();
        for (Map.Entry<String, String> entry : table.entrySet()) {
            if (lower.contains(entry.getKey())) return entry.getValue();
        }
        return fallback;
    }

    // ── Scanning helpers ──────────────────────────────────────────────────────

    private static HouseBlockPos findHomeChest(SimNPCComponent npc) {
        synchronized (ChestRegistry.CHESTS) {
            for (HouseBlockPos cp : ChestRegistry.CHESTS) {
                if (HouseManager.canOpenChest(npc.entityId, cp)) {
                    return cp;
                }
            }
        }
        return null;
    }

    private static Vector3i scanForCrops(Vector3d center) {
        Vector3i closest = null;
        double minDistSq = 15.0 * 15.0;
        synchronized (CropRegistry.CROPS) {
            for (HouseBlockPos cp : CropRegistry.CROPS) {
                double dx = cp.x - center.x;
                double dy = cp.y - center.y;
                double dz = cp.z - center.z;
                double distSq = dx*dx + dy*dy + dz*dz;
                if (distSq < minDistSq) {
                    minDistSq = distSq;
                    closest = new Vector3i(cp.x, cp.y, cp.z);
                }
            }
        }
        return closest;
    }

    public static Vector3i scanForFarmland(Vector3d center, World world) {
        Vector3i closest = null;
        double minDistSq = 15.0 * 15.0;

        synchronized (FarmlandRegistry.FARMLAND) {
            for (HouseBlockPos fp : FarmlandRegistry.FARMLAND) {
                // Check if block above is empty (so we can plant something)
                BlockType above = world.getBlockType(fp.x, fp.y + 1, fp.z);
                if (above == null || above.getId() == null || above.getId().equalsIgnoreCase(EMPTY_BLOCK)) {
                    double dx = fp.x + 0.5 - center.x;
                    double dy = fp.y + 1.5 - center.y;
                    double dz = fp.z + 0.5 - center.z;
                    double distSq = dx*dx + dy*dy + dz*dz;
                    if (distSq < minDistSq) {
                        minDistSq = distSq;
                        closest = new Vector3i(fp.x, fp.y + 1, fp.z);
                    }
                }
            }
        }
        return closest;
    }

    public static String findSeedInInventory(ItemContainer container) {
        if (container == null) return null;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack item = container.getItemStack(slot);
            if (item != null && !item.isEmpty()) {
                String id = item.getItemId();
                if (id.toLowerCase(java.util.Locale.ROOT).contains("plant_seeds_")) {
                    return id;
                }
            }
        }
        return null;
    }

    public static short findSeedSlot(ItemContainer container, String seedId) {
        if (container == null || seedId == null) return -1;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack item = container.getItemStack(slot);
            if (item != null && !item.isEmpty() && item.getItemId().equals(seedId)) {
                return slot;
            }
        }
        return -1;
    }

    /** Kept public: called from outside this class. */
    public static String getCropBlockFromSeed(String seedId) {
        return lookup(SEED_TO_CROP_BLOCK, seedId, "Plant_Crop_Carrot_Block");
    }
}