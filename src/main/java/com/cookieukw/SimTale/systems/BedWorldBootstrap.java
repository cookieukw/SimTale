package com.cookieukw.SimTale.systems;

import com.cookieukw.SimTale.core.SimLog;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3d;
import org.joml.Vector3i;


public final class BedWorldBootstrap {
    private static final SimLog LOGGER = SimLog.forClass(BedWorldBootstrap.class);
    private BedWorldBootstrap() {
    }

    public static void bootstrapLoadedRadius(World world, Vector3d center, int radius) {
        int px = (int) Math.floor(center.x);
        int py = (int) Math.floor(center.y);
        int pz = (int) Math.floor(center.z);
        
        int bedsFound = BedRegistry.size();
        LOGGER.debug("[SimTale-DEBUG] Starting simple radius scan around (" + px + "," + py + "," + pz + ") with radius " + radius);
        
        // Scan a cube around the player position
        for (int x = px - radius; x <= px + radius; x++) {
            for (int z = pz - radius; z <= pz + radius; z++) {
                for (int y = Math.max(0, py - 16); y <= Math.min(319, py + 16); y++) {
                    BlockType type = world.getBlockType(x, y, z);
                    if (type == null || type.getId() == null) continue;
                    if (!BedRegistry.isBedId(type.getId())) continue;
                    
                    // Uma cama ocupa SEIS blocos. Registrar cada um como cama independente e o
                    // que fazia o /simtale debugnear reportar doze camas onde havia duas — e,
                    // pior, fazia a NPC ser montada num bloco qualquer do movel em vez da ancora.
                    // Como o ponto de montagem do asset e medido a partir da ancora, montar num
                    // filler desloca a pose de dormir pela distancia daquele filler ate ela.
                    //
                    // A heuristica anterior (isPrimaryBedBlock, "o vizinho esta em +X ou +Z")
                    // tentava adivinhar a ancora pela vizinhanca. Agora quem responde e o proprio
                    // motor, pelo dado de filler que o /inspectfiller do jogo tambem le.
                    Vector3i ancora = FurnitureAnchorHelper.anchorOf(world, x, y, z);

                    float yaw = 0f;
                    if (isBed(world.getBlockType(ancora.x + 1, ancora.y, ancora.z))
                            || isBed(world.getBlockType(ancora.x - 1, ancora.y, ancora.z))) {
                        yaw = (float) (Math.PI / 2.0); // deitado ao longo do eixo X
                    } else if (isBed(world.getBlockType(ancora.x, ancora.y, ancora.z + 1))
                            || isBed(world.getBlockType(ancora.x, ancora.y, ancora.z - 1))) {
                        yaw = 0f; // deitado ao longo do eixo Z
                    }

                    // addOrReplace deduplica por posicao, entao os seis blocos do movel
                    // convergem para um unico registro.
                    BedRegistry.addOrReplace(ancora.x, ancora.y, ancora.z, yaw);
                }
            }
        }
        
        int newBeds = BedRegistry.size() - bedsFound;
        LOGGER.debug("[SimTale-DEBUG] Simple scan finished: found " + newBeds + " new beds. Total beds: " + BedRegistry.size());
    }

    public static boolean isPrimaryBedBlock(World world, int x, int y, int z) {
        BlockType type = world.getBlockType(x, y, z);
        if (!isBed(type)) {
            return false;
        }

        boolean posX = isBed(world.getBlockType(x + 1, y, z));
        boolean negX = isBed(world.getBlockType(x - 1, y, z));
        boolean posZ = isBed(world.getBlockType(x, y, z + 1));
        boolean negZ = isBed(world.getBlockType(x, y, z - 1));

        int adjacentBeds = 0;
        if (posX) adjacentBeds++;
        if (negX) adjacentBeds++;
        if (posZ) adjacentBeds++;
        if (negZ) adjacentBeds++;

        if (adjacentBeds != 1) {
            return false;
        }

        return posX || posZ;
    }

    private static boolean isBed(BlockType type) {
        return type != null && type.getId() != null && BedRegistry.isBedId(type.getId());
    }
}
