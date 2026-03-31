package com.cookieukw.SimTale.db;

import com.cookie.caskara.Caskara;
import com.cookieukw.SimTale.core.Relationship;
import com.cookieukw.SimTale.core.SimNPCComponent;
import java.util.Map;
import java.util.UUID;

/**
 * Handles persistence for SimTale NPCs using the Caskara database.
 */
public class SimNPCPersistence {

    public static void saveNPC(SimNPCComponent component) {
        if (component.entityId == null)
            return;

        SimNPCData data = new SimNPCData(
                component.entityId,
                component.name,
                component.personality,
                component.needs,
                component.stats);

        // Convert Map<UUID, Relationship> to Map<String, Relationship> for Caskara
        for (Map.Entry<UUID, Relationship> entry : component.relationships.entrySet()) {
            data.relationships.put(entry.getKey().toString(), entry.getValue());
        }

        Caskara.save(data);
    }

    public static void loadNPC(SimNPCComponent component) {
        if (component.entityId == null)
            return;

        SimNPCData data = Caskara.load(component.entityId.toString(), SimNPCData.class);
        if (data != null) {
            component.name = data.name;
            component.personality = data.personality;
            component.needs = data.needs;
            component.stats = data.stats;

            // Reconstruct relationships
            for (Map.Entry<String, Relationship> entry : data.relationships.entrySet()) {
                component.relationships.put(UUID.fromString(entry.getKey()), entry.getValue());
            }
        }
    }
}
