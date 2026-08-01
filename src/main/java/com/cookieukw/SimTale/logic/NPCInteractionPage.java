package com.cookieukw.SimTale.logic;
import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.SimTale;
import com.cookieukw.SimTale.ai.RoutineAIComponent;
import com.cookieukw.SimTale.ai.RoutineAIComponent.TaskType;
import com.cookieukw.SimTale.core.Child;
import com.cookieukw.SimTale.core.Mood;
import com.cookieukw.SimTale.core.NPCPreferences;
import com.cookieukw.SimTale.core.Profession;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.SimNPCComponent;
import com.cookieukw.SimTale.core.Trait;
import com.cookieukw.SimTale.core.lifecycle.GrowthComponent;
import com.cookieukw.SimTale.core.lifecycle.LifecycleManager;
import com.cookieukw.SimTale.db.SimNPCPersistence;
import com.cookieukw.SimTale.systems.NPCMovementHelper;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.cookieukw.SimTale.core.NpcFreezeUtil;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.cookieukw.SimTale.core.lifecycle.BabyCareManager;
import com.cookieukw.SimTale.core.RelationshipStatus;
import com.hypixel.hytale.protocol.AttachedToType;
import com.hypixel.hytale.protocol.ApplyLookType;
import com.hypixel.hytale.protocol.CanMoveType;
import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.MouseInputTargetType;
import com.hypixel.hytale.protocol.MouseInputType;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.PositionDistanceOffsetType;
import com.hypixel.hytale.protocol.RotationType;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.protocol.ApplyMovementType;
import com.hypixel.hytale.protocol.PositionType;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;

import java.util.Objects;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import javax.annotation.Nonnull;

@SuppressWarnings("null")
public class NPCInteractionPage extends InteractiveCustomUIPage<String> {

    private final SimNPCComponent npc;
    private final Player player;
    private final PlayerRef playerRefComp;
    private com.hypixel.hytale.math.vector.Rotation3f originalRotation;

    public NPCInteractionPage(@Nonnull PlayerRef playerRefComp, Player player, SimNPCComponent npc) {
        BuilderCodec<String> codec = BuilderCodec.builder(String.class, String::new).build();
        super(playerRefComp, CustomPageLifetime.CanDismiss, codec);
        this.npc = npc;
        this.player = player;
        this.playerRefComp = playerRefComp;
    }

    /**
     * Toggled at runtime with {@code /simtale camdebug}. When on, every camera setup dumps its
     * inputs and computed values to the player's chat and to the server console.
     */
    public static boolean CAMERA_DEBUG = false;

    /** Sends a debug line to the player and mirrors it to the console. */
    private void debug(String line) {
        if (!CAMERA_DEBUG) return;
        playerRefComp.sendMessage(Message.raw("[cam] " + line));
        HytaleLogger.forEnclosingClass().atInfo().log("[SimTale-CAM] " + line);
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private void applyNpcCloseUpCamera(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        if (npc == null || npc.entityRef == null || !npc.entityRef.isValid()) {
            debug("ABORTOU: npc/entityRef nulo ou invalido");
            return;
        }

        TransformComponent pTrans = store.getComponent(playerRef, TransformComponent.getComponentType());
        TransformComponent nTrans = store.getComponent(npc.entityRef, TransformComponent.getComponentType());

        if (pTrans == null || nTrans == null) {
            debug("ABORTOU: transform nulo (player=" + (pTrans != null) + " npc=" + (nTrans != null) + ")");
            return;
        }

        // Save original player rotation to restore it later
        this.originalRotation = new com.hypixel.hytale.math.vector.Rotation3f(pTrans.getRotation());

        Vector3d pPos = pTrans.getPosition();
        Vector3d nPos = nTrans.getPosition();

        // Vector from NPC to player
        double dx = pPos.x - nPos.x;
        double dz = pPos.z - nPos.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.01) {
            dx = 0.0;
            dz = 1.0;
            length = 1.0;
        }

        // Normalized vector from NPC to player
        double ux = dx / length;
        double uz = dz / length;

        // Perpendicular vector (pointing to the right of the NPC-to-player vector)
        double rx = -uz;
        double rz = ux;

        // Head height read from the actual bounding box instead of a hardcoded 1.45. The mod
        // spawns babies, toddlers and children at reduced scale, and on those the fixed value
        // aimed the camera well above the head. Same source PlumbobSystem already uses.
        double headHeight = 1.45;
        BoundingBox npcBox = store.getComponent(npc.entityRef, BoundingBox.getComponentType());
        if (npcBox != null && npcBox.getBoundingBox() != null) {
            headHeight = npcBox.getBoundingBox().height() * 0.8;
        }

        // Camera position: 2.0 meters from NPC towards player, offset by 0.55 meters to the right
        // This shifts the NPC to the left side of the player's screen
        double camX = nPos.x + ux * 2.0 + rx * 0.55;
        double camZ = nPos.z + uz * 2.0 + rz * 0.55;
        double camY = nPos.y + headHeight;

        // Target (where the camera is looking): NPC's head
        double targetX = nPos.x;
        double targetY = nPos.y + headHeight;
        double targetZ = nPos.z;

        // Direction vector from camera to NPC head
        double dirX = targetX - camX;
        double dirY = targetY - camY;
        double dirZ = targetZ - camZ;
        double distH = Math.sqrt(dirX * dirX + dirZ * dirZ);

        // Yaw and Pitch in RADIANS.
        //
        // protocol.Direction is radians, not degrees: PlayerInput$SetHead reads Direction's
        // yaw/pitch/roll straight into Rotation3f.set(f,f,f) with no unit conversion, and
        // Rotation3f is radians everywhere in this codebase. Of the 55 server classes that
        // touch Direction, only 3 convert units, and none of those are rotation-related.
        //
        // This used to call Math.toDegrees(), so a yaw of 1.5 rad was sent as 85.9 — read back
        // as 85.9 radians, i.e. ~13.7 full turns. That is why the camera never pointed at the NPC.
        // Hytale's forward axis is -Z, so to look along (dirX, dirZ) the yaw is atan2 of the
        // NEGATED direction. With atan2(dirX, dirZ) the camera was aimed exactly 180° away —
        // which is why the shot was an empty field with the NPC behind the lens.
        //
        // Two independent symptoms pinned this down: the camera never framed the NPC, and the
        // NPC-facing code below (same convention) left the NPC with its back to the player.
        // A self-consistency check on atan2 alone cannot catch this: inverting the angle
        // reproduces the input vector either way. Only the engine's axis convention decides.
        double yaw = Math.atan2(-dirX, -dirZ);
        double pitch = Math.atan2(dirY, distH);

        ServerCameraSettings settings = new ServerCameraSettings();
        settings.isFirstPerson = false;
        
        // Use custom camera position and rotation in the world (not attached to any entity)
        settings.attachedToType = AttachedToType.None;
        settings.positionType = PositionType.Custom;
        settings.position = new Position(camX, camY, camZ);
        
        settings.rotationType = RotationType.Custom;
        settings.rotation = new Direction((float) yaw, (float) pitch, 0f);

        // Smooth cinematic transition
        settings.positionLerpSpeed = 0.15f;
        settings.rotationLerpSpeed = 0.15f;

        // UI mode camera settings: lock orientation to the server-defined rotation
        settings.applyLookType = ApplyLookType.Rotation;
        settings.canMoveType = CanMoveType.AttachedToLocalPlayer;
        settings.applyMovementType = ApplyMovementType.CharacterController;

        // Pitch input is disabled because applyLookType already pins the orientation to the
        // server-sent rotation; leaving it on just lets the client fight that.
        //
        // NOTE: do NOT set skipCharacterPhysics here. It was tried once to stop the camera
        // drifting and it dropped the player through the world — the resulting void death
        // opened the death screen, which dismissed this page from inside Store.tick. The drift
        // it was meant to fix was really the degrees/radians bug above.
        settings.allowPitchControls = false;

        // Hide default UI overlays
        settings.displayCursor = false;
        settings.displayReticle = false;
        settings.hideHeldItem = true;

        if (CAMERA_DEBUG) {
            debug("NPC  pos=(" + fmt(nPos.x) + ", " + fmt(nPos.y) + ", " + fmt(nPos.z) + ")  head=+" + fmt(headHeight));
            debug("PLR  pos=(" + fmt(pPos.x) + ", " + fmt(pPos.y) + ", " + fmt(pPos.z) + ")  dist=" + fmt(length));
            debug("CAM  pos=(" + fmt(camX) + ", " + fmt(camY) + ", " + fmt(camZ) + ")");
            debug("ALVO pos=(" + fmt(targetX) + ", " + fmt(targetY) + ", " + fmt(targetZ) + ")");
            debug("DIR  (" + fmt(dirX) + ", " + fmt(dirY) + ", " + fmt(dirZ) + ")  distH=" + fmt(distH));
            debug("YAW  " + fmt(yaw) + " rad  =  " + fmt(Math.toDegrees(yaw)) + " graus");
            debug("PIT  " + fmt(pitch) + " rad  =  " + fmt(Math.toDegrees(pitch)) + " graus");
            // Both axis conventions are printed because a self-consistency check cannot tell
            // them apart — inverting atan2 reproduces the input vector for either one. Look at
            // the game: whichever line matches what you actually see is the engine's.
            double nlen = Math.sqrt(dirX * dirX + dirZ * dirZ);
            double fxA = Math.sin(yaw), fzA = Math.cos(yaw);
            double dotA = nlen < 1e-6 ? 0 : (fxA * dirX + fzA * dirZ) / nlen;
            debug("CONV +Z: forward=(" + fmt(fxA) + ", " + fmt(fzA) + ")  dot=" + fmt(dotA));
            debug("CONV -Z: forward=(" + fmt(-fxA) + ", " + fmt(-fzA) + ")  dot=" + fmt(-dotA)
                    + "   <- convencao em uso agora");
        }

        playerRefComp.getPacketHandler().writeNoCache(
            new SetServerCamera(ClientCameraView.Custom, true, settings)
        );
        debug("pacote SetServerCamera enviado (Custom, enabled=true)");
    }

    private void resetNpcCamera() {
        playerRefComp.getPacketHandler().writeNoCache(
            new SetServerCamera(ClientCameraView.Custom, false, null)
        );
    }

    /**
     * Puts the player's own rotation back where it was before the close-up.
     * <p>
     * {@code originalRotation} was being captured in {@link #applyNpcCloseUpCamera} and then
     * never read — the field was dead and the player was left facing wherever the dialogue
     * had turned them.
     */
    private void restorePlayerRotation(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        if (originalRotation == null) return;

        TransformComponent pTrans = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (pTrans == null) return;

        // teleportRotation mutates the live component in place and flags it for sync.
        // store.putComponent() must NOT be used here: onDismiss can fire from inside a system
        // tick (the death screen opens a page over this one), and any structural store write
        // during processing throws "Store is currently processing!".
        pTrans.teleportRotation(originalRotation);
        originalRotation = null;
    }

    /**
     * Hands the Movement animation slot back to the engine.
     * <p>
     * {@code build()} pins that slot to "Idle" so the NPC stops its walk cycle while the
     * dialogue is open. The Movement slot is what drives locomotion animation, so leaving it
     * pinned meant that from the first interaction onward the NPC's body kept being moved by
     * the AI while its legs stayed frozen in the idle pose — the "sliding on ice" bug, which
     * is exactly why it only ever affected NPCs that had been talked to at least once.
     */
    private void releaseMovementAnimation(Store<EntityStore> store) {
        if (npc == null || npc.entityRef == null || !npc.entityRef.isValid()) return;

        // stopAnimation() alone is not enough: it stops playback but leaves the slot's entry in
        // ActiveAnimationComponent still pointing at "Idle", so the NPC's legs stayed in the
        // idle pose while the AI kept moving its body. That component is pure runtime state,
        // which is why leaving and re-entering the world "fixed" the NPC.
        //
        // This is the same clearing sequence MoodAnimationSystem uses for the Face slot, minus
        // the commandBuffer write: store.getComponent returns the live instance, so nulling the
        // entry mutates it directly (and onDismiss cannot do structural store writes anyway).
        ActiveAnimationComponent animComp =
                store.getComponent(npc.entityRef, ActiveAnimationComponent.getComponentType());
        if (animComp != null) {
            animComp.getActiveAnimations()[AnimationSlot.Movement.ordinal()] = null;
        }
        AnimationUtils.playAnimation(npc.entityRef, AnimationSlot.Movement, null, store);
    }

    private void freezeNpc(Store<EntityStore> store) {
        if (npc != null) NpcFreezeUtil.freeze(store, npc.entityRef);
    }

    private void unfreezeNpc(Store<EntityStore> store) {
        if (npc != null) NpcFreezeUtil.unfreeze(store, npc.entityRef);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> playerRef, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        if (npc != null) {
            npc.isInteractingViaUI = true;
            // RoutineAISystem re-applies the facing every tick from this; a one-shot rotation
            // gets steered away by the role's own Idle motion.
            npc.uiInteractionPlayer = playerRefComp.getUuid();
            if (npc.entityRef != null && npc.entityRef.isValid()) {
                // 1. Rotate NPC to face the player
                TransformComponent pTrans = store.getComponent(playerRef, TransformComponent.getComponentType());
                TransformComponent nTrans = store.getComponent(npc.entityRef, TransformComponent.getComponentType());
                if (pTrans != null && nTrans != null) {
                    Vector3d pPos = pTrans.getPosition();
                    Vector3d nPos = nTrans.getPosition();
                    double dx = pPos.x - nPos.x;
                    double dz = pPos.z - nPos.z;
                    // Same -Z forward convention as the camera: without the negation the NPC
                    // turned its back on the player instead of facing them.
                    double yaw = Math.atan2(-dx, -dz);
                    // In-place mutation, no store.putComponent(): build() also runs inside a
                    // system tick when a page is opened from one (see unfreezeNpc).
                    nTrans.teleportRotation(new com.hypixel.hytale.math.vector.Rotation3f(0f, (float) yaw, 0f));
                }
                
                // 2. Reset NPC movement animation to Idle
                AnimationUtils.playAnimation(npc.entityRef, com.hypixel.hytale.protocol.AnimationSlot.Movement, "Idle", store);

                // 3. Cancel any pending movement BEFORE freezing.
                // The NPC keeps its leash point (its walk destination) while frozen. On
                // unfreeze the engine resumes gliding toward that stale point, but the walk
                // animation has been replaced by Idle above — which reads in-game as the NPC
                // sliding around on ice. clearMoveTarget pins the leash to where it is standing.
                RoutineAIComponent ai = store.getComponent(npc.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
                if (ai != null) {
                    NPCMovementHelper.clearMoveTarget(npc.entityRef, ai);
                    ai.currentTask = RoutineAIComponent.TaskType.IDLE;
                    ai.targetBlockPosition = null;
                    ai.socializeTargetId = null;
                    ai.socializeHost = false;
                    ai.wanderTimer = 0;
                }

                // 4. Freeze NPC visually and logically
                freezeNpc(store);
            }
            applyNpcCloseUpCamera(playerRef, store);
        }

        // Clear any active chat conversation so the timeout system doesn't
        // fire "Você parece distraído" while the player is using the UI
        if (npc != null) {
            npc.currentConversationPartner = null;
        }

        commandBuilder.append("NPCInteraction/NPCInteraction.ui");

        if (npc == null) {
            // Was an `assert`, which is disabled at runtime; the page then NPE'd on the next line.
            return;
        }
        commandBuilder.set("#NpcName.Text", npc.name);

        
        Message profMsg = npc.profession != null && npc.profession != Profession.UNEMPLOYED 
            ? Message.translation("ui.prof." + npc.profession.name().toLowerCase()) 
            : Message.translation("ui.prof.unemployed");
        commandBuilder.set("#NpcProfession.TextSpans", Message.translation("ui.job").insert(Message.raw(" ")).insert(profMsg));
        
        Mood currentMood = npc.getMood();
        String moodEmoji = switch (currentMood) {
            case HAPPY -> " :)";
            case ANGRY -> " >:(";
            case SAD -> " :(";
            case SCARED -> " D:";
            case SLEEPY -> " -.-";
            case EXCITED -> " :D";
            default -> "";
        };
        // Mood isn't fully translated yet, fallback to raw text but we can add it later
        Message moodMsg = Message.translation("ui.mood." + currentMood.name().toLowerCase());
        commandBuilder.set("#NpcMood.TextSpans", Message.translation("ui.mood").insert(Message.raw(" ")).insert(moodMsg).insert(Message.raw(moodEmoji)));

        if (currentMood == Mood.ANGRY) {
            commandBuilder.set("#NpcName.Style.TextColor", "#FF6666");
        } else if (currentMood == Mood.SAD) {
            commandBuilder.set("#NpcName.Style.TextColor", "#6688CC");
        }

        // --- Info Panel Static UI Overrides ---
        commandBuilder.set("#InfoHeader.TextSpans", Message.translation("ui.info"));
        commandBuilder.set("#ChatButtonText.TextSpans", Message.translation("ui.button.chat"));
        commandBuilder.set("#JokeButtonText.TextSpans", Message.translation("ui.button.joke"));
        commandBuilder.set("#FlirtButtonText.TextSpans", Message.translation("ui.button.flirt"));
        commandBuilder.set("#GiftButtonText.TextSpans", Message.translation("ui.button.gift"));
        commandBuilder.set("#InsultButtonText.TextSpans", Message.translation("ui.button.insult"));
        commandBuilder.set("#AssignProfessionButtonText.TextSpans", Message.translation("ui.button.prof"));
        commandBuilder.set("#PregnancyButtonText.TextSpans", Message.translation("ui.button.pregnancy"));

        // Traits
        if (npc.personality != null && npc.personality.traits != null && !npc.personality.traits.isEmpty()) {
            Message traitsMsg = Message.raw("");
            boolean first = true;
            for (Trait t : npc.personality.traits) {
                if (!first) {
                    traitsMsg = traitsMsg.insert(Message.raw(", "));
                }
                traitsMsg = traitsMsg.insert(Message.translation("ui.trait." + t.name().toLowerCase()));
                first = false;
            }
            commandBuilder.set("#NpcTraits.TextSpans", Message.translation("ui.traits").insert(Message.raw(" ")).insert(traitsMsg));
        }
        
        // Preferences
        if (npc.preferences != null) {
            List<String> allLikes = new ArrayList<>();
            if (npc.preferences.getFavoriteFoods() != null) allLikes.addAll(npc.preferences.getFavoriteFoods());
            if (npc.preferences.getFavoriteItems() != null) allLikes.addAll(npc.preferences.getFavoriteItems());
            
            if (!allLikes.isEmpty()) {
                Message likesMsg = Message.raw("");
                boolean first = true;
                for (String itemId : allLikes) {
                    if (!first) likesMsg = likesMsg.insert(Message.raw(", "));
                    likesMsg = likesMsg.insert(Message.translation("ui." + itemId));
                    first = false;
                }
                commandBuilder.set("#NpcLikes.TextSpans", Message.translation("ui.likes").insert(Message.raw(" ")).insert(likesMsg));
            }

            List<String> allHates = new ArrayList<>();
            if (npc.preferences.getHatedFoods() != null) allHates.addAll(npc.preferences.getHatedFoods());
            if (npc.preferences.getHatedItems() != null) allHates.addAll(npc.preferences.getHatedItems());
            
            if (!allHates.isEmpty()) {
                Message hatesMsg = Message.raw("");
                boolean first = true;
                for (String itemId : allHates) {
                    if (!first) hatesMsg = hatesMsg.insert(Message.raw(", "));
                    hatesMsg = hatesMsg.insert(Message.translation("ui." + itemId));
                    first = false;
                }
                commandBuilder.set("#NpcHates.TextSpans", Message.translation("ui.hates").insert(Message.raw(" ")).insert(hatesMsg));
            }

            Message hobbyMsg = Message.translation("ui.hobby." + npc.preferences.getHobby().name().toLowerCase());
            commandBuilder.set("#NpcHobby.TextSpans", Message.translation("ui.hobby").insert(Message.raw(" ")).insert(hobbyMsg));
            
            String seasonKey = "season." + (npc.preferences.getFavoriteSeason() != null ? npc.preferences.getFavoriteSeason().name().toLowerCase() : "spring");
            commandBuilder.set("#NpcSeason.TextSpans", Message.translation("ui.season").insert(Message.raw(" ")).insert(Message.translation("ui." + seasonKey)));
        }
        
        Relationship rel = npc.getRelationship(playerRefComp.getUuid());
        Message statusMsg = Message.translation("ui.rel." + rel.getStatusName().toLowerCase());
        Message relValues = Message.translation("ui.relationship.values")
            .param("status", statusMsg)
            .param("friendship", String.valueOf(rel.friendship))
            .param("affinity", String.valueOf(rel.affinity));
        commandBuilder.set("#NpcRelationship.TextSpans", 
            Message.translation("ui.relationship").insert(Message.raw(" ")).insert(relValues));

        // --- Family Info Panel Population ---
        Message parentsMsg;
        GrowthComponent npcGrowth = Caskara.load("child_" + npc.entityId.toString(), GrowthComponent.class);
        if (npcGrowth != null) {
            String motherName = getParentName(npcGrowth.motherId);
            String fatherName = getParentName(npcGrowth.fatherId);
            parentsMsg = Message.translation("ui.parents").insert(Message.raw(" " + motherName + " & " + fatherName));
        } else {
            parentsMsg = Message.translation("ui.parents").insert(Message.raw(" —"));
        }
        commandBuilder.set("#NpcFamilyParents.TextSpans", parentsMsg);

        Message childrenMsg = Message.translation("ui.children").insert(Message.raw(" "));
        if (npc.family.children == null || npc.family.children.isEmpty()) {
            childrenMsg = childrenMsg.insert(Message.raw("—"));
        } else {
            boolean first = true;
            for (Child c : npc.family.children) {
                if (c.id == null) continue;
                if (!first) childrenMsg = childrenMsg.insert(Message.raw(", "));
                GrowthComponent gc = Caskara.load("child_" + c.id, GrowthComponent.class);
                Message stageName = gc != null 
                    ? Message.translation("ui.stage." + gc.stage.name().toLowerCase()) 
                    : Message.translation("ui.stage.adult");
                childrenMsg = childrenMsg.insert(Message.raw(c.name + " (")).insert(stageName).insert(Message.raw(")"));
                first = false;
            }
            if (first) childrenMsg = childrenMsg.insert(Message.raw("—"));
        }
        commandBuilder.set("#NpcFamilyChildren.TextSpans", childrenMsg);

        // --- Child Verification to Hide Flirt Button ---
        boolean isChild = false;
        if (npc.entityRef != null) {
            NPCEntity npcEntity = 
                store.getComponent(npc.entityRef, Objects.requireNonNull(NPCEntity.getComponentType()));
            if (npcEntity != null && npcEntity.getRoleName() != null && npcEntity.getRoleName().toLowerCase().contains("child")) {
                isChild = true;
            }
        }
        for (GrowthComponent child : LifecycleManager.ACTIVE_CHILDREN) {
            if (npc.entityId != null && npc.entityId.equals(child.childId) && !child.isAdult()) {
                isChild = true;
                break;
            }
        }

        if (isChild) {
            commandBuilder.set("#FlirtButton.Visible", false);
            commandBuilder.set("#AssignProfessionButton.Visible", false);
            commandBuilder.set("#PregnancyButton.Visible", false);
        }
        if (!isChild && npc.pregnancy != null && npc.pregnancy.pregnant) {
            commandBuilder.set("#PregnancyButton.Visible", true);
        }

        boolean isMarried = rel.status == RelationshipStatus.MARRIED;
        commandBuilder.set("#InventoryButton.Visible", isMarried);

        // --- Button Event Bindings ---
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ChatButton", new EventData().append("button", "ChatButton"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#JokeButton", new EventData().append("button", "JokeButton"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#FlirtButton", new EventData().append("button", "FlirtButton"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#InsultButton", new EventData().append("button", "InsultButton"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#GiftButton", new EventData().append("button", "GiftButton"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#AssignProfessionButton", new EventData().append("button", "AssignProfessionButton"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#PregnancyButton", new EventData().append("button", "PregnancyButton"), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#InventoryButton", new EventData().append("button", "InventoryButton"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> storeRef, @Nonnull Store<EntityStore> store, @Nonnull String eventData) {
        HytaleLogger.forEnclosingClass().atInfo().log("SimTale [DEBUG UI EVENT]: payload = " + eventData);
        
        player.getPageManager().setPage(storeRef, store, Page.None);

        if (npc.name.equals("Dona Morte")) {
            RoutineAIComponent reaperAi = store.getComponent(npc.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
            if (reaperAi != null && reaperAi.currentTask == TaskType.REAPING && reaperAi.dyingEntityId != null) {
                if (Math.random() < 0.5) {
                    playerRefComp.sendMessage(Message.raw("Dona Morte acenou a cabeca. A vida foi poupada... desta vez."));
                    World w = null;
                    for (World world : Universe.get().getWorlds().values()) { w = world; break; }
                    if (w != null) {
                        Ref<EntityStore> dyingRef = w.getEntityStore().getRefFromUUID(reaperAi.dyingEntityId);
                        if (dyingRef != null) {
                            SimNPCComponent dyingNpc = store.getComponent(dyingRef, SimTale.SIM_NPC_COMPONENT_TYPE);
                            if (dyingNpc != null) {
                                dyingNpc.needs.hunger = 50f;
                                store.putComponent(dyingRef, SimTale.SIM_NPC_COMPONENT_TYPE, dyingNpc);
                            }
                            RoutineAIComponent dyingAi = store.getComponent(dyingRef, SimTale.ROUTINE_AI_COMPONENT_TYPE);
                            if (dyingAi != null) {
                                dyingAi.currentTask = TaskType.IDLE;
                                store.putComponent(dyingRef, SimTale.ROUTINE_AI_COMPONENT_TYPE, dyingAi);
                                AnimationUtils.playAnimation(dyingRef, AnimationSlot.Action, "Characters/Animations/Actions/Idle.blockyanim", "Idle", store);
                            }
                        }
                    }
                    if (w != null) {
                        TransformComponent t = store.getComponent(npc.entityRef, TransformComponent.getComponentType());
                        if (t != null) {
                            t.setPosition(new Vector3d(0, -1000, 0));
                            store.putComponent(npc.entityRef, TransformComponent.getComponentType(), t);
                        }
                    }
                } else {
                    playerRefComp.sendMessage(Message.raw("Dona Morte te ignorou friamente..."));
                    reaperAi.reapTimer = 0;
                    store.putComponent(npc.entityRef, SimTale.ROUTINE_AI_COMPONENT_TYPE, reaperAi);
                }
            }
            return;
        }

        if (eventData.contains("ChatButton")) {
            Message resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.FRIENDLY);
            playerRefComp.sendMessage(resp);
        } else if (eventData.contains("JokeButton")) {
            Message resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.FUNNY);
            playerRefComp.sendMessage(resp);
        } else if (eventData.contains("FlirtButton")) {
            Message resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.ROMANTIC);
            playerRefComp.sendMessage(resp);
        } else if (eventData.contains("InsultButton")) {
            Message resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.MEAN);
            playerRefComp.sendMessage(resp);
        } else if (eventData.contains("GiftButton")) {
            Message resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.GIFT);
            playerRefComp.sendMessage(resp);
        } else if (eventData.contains("AssignProfessionButton")) {
            Message resp = InteractionManager.performInteraction(npc, playerRefComp.getUuid(), playerRefComp, InteractionType.ASSIGN_PROFESSION);
            playerRefComp.sendMessage(resp);
        } else if (eventData.contains("PregnancyButton")) {
            player.getPageManager().openCustomPage(storeRef, store, new NPCPregnancyPage(playerRefComp, player, npc));
        } else if (eventData.contains("InventoryButton")) {
            openNpcInventory(storeRef, store);
        }
    }

    private void openNpcInventory(Ref<EntityStore> playerRef, Store<EntityStore> store) {
        if (npc.entityRef == null || !npc.entityRef.isValid()) {
            playerRefComp.sendMessage(Message.translation("general.npc.invalid"));
            return;
        }

        InventoryComponent.Storage storage = store.getComponent(npc.entityRef, InventoryComponent.Storage.getComponentType());
        if (storage == null) {
            playerRefComp.sendMessage(Message.translation("general.npc.no_storage"));
            return;
        }

        ItemContainer container = storage.getInventory();
        if (container == null) {
            playerRefComp.sendMessage(Message.translation("general.npc.no_container"));
            return;
        }

        // Sync carried babies to inventory
        BabyCareManager.syncCarriedBabiesToInventory(npc.entityId, container);
        
        // Register inventory change listener to sync baby custody back
        BabyCareManager.registerInventoryListener(npc.entityId, container, playerRefComp.getUuid());

        ContainerWindow window = new ContainerWindow(container);
        player.getPageManager().setPageWithWindows(playerRef, store, Page.Bench, true, new Window[]{window});
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        super.onDismiss(playerRef, store);

        // NPC state is released FIRST, and in a finally, so that nothing below can strand it.
        //
        // This ordering is not cosmetic. Previously the camera/rotation work ran first; when
        // restorePlayerRotation threw (illegal store write mid-tick), the lines that clear
        // isInteractingViaUI and remove Frozen never executed. That leaves the NPC permanently
        // frozen — and because RoutineAISystem's self-heal only strips Frozen when
        // !isInteractingViaUI, the AI could not recover it either. The NPC kept receiving leash
        // updates while frozen, which is what "sliding on ice" looks like.
        try {
            if (npc != null) {
                npc.isInteractingViaUI = false;
                npc.uiInteractionPlayer = null;
                releaseMovementAnimation(store);
                unfreezeNpc(store);
            }
        } finally {
            resetNpcCamera();
            restorePlayerRotation(playerRef, store);
        }
    }

    private String getParentName(UUID parentId) {
        if (parentId == null) return "Desconhecido";
        for (SimNPCComponent other : SimTale.ACTIVE_NPCS) {
            if (other.entityId != null && other.entityId.equals(parentId)) {
                return other.name;
            }
        }
        for (PlayerRef pRef : Universe.get().getPlayers()) {
            if (pRef.getUuid().equals(parentId)) {
                return pRef.getUsername();
            }
        }
        SimNPCComponent temp = new SimNPCComponent(parentId, "Parent");
        SimNPCPersistence.loadNPC(temp);
        if (!temp.name.equals("Parent")) {
            return temp.name;
        }
        return "Desconhecido";
    }
}
