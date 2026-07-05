package com.cookieukw.SimTale.core;

import com.cookieukw.SimTale.core.lifecycle.GrowthComponent;
import com.cookieukw.SimTale.core.lifecycle.PregnancyComponent;
import org.joml.Vector3d;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gerencia casamento, casa compartilhada e lista de filhos de um NPC.
 *
 * A gravidez agora é gerenciada pelo {@link PregnancyComponent} no SimNPCComponent,
 * e o crescimento pelo {@link GrowthComponent} no LifecycleManager.
 */
public class FamilySystem {
    public boolean isMarried = false;
    public UUID spouseId = null;
    
    public boolean hasSharedHome = false;
    public double homeX, homeY, homeZ;
    
    /** Lista de filhos (dados simplificados para persistência). */
    public List<Child> children = new ArrayList<>();
    
    public FamilySystem() {}
    
    /**
     * Realiza o casamento com um NPC.
     */
    public void marry(UUID newSpouseId, Vector3d sharedHome) {
        this.isMarried = true;
        this.spouseId = newSpouseId;
        if (sharedHome != null) {
            this.hasSharedHome = true;
            this.homeX = sharedHome.x;
            this.homeY = sharedHome.y;
            this.homeZ = sharedHome.z;
        }
    }

    /**
     * Desfaz o casamento.
     */
    public void divorce() {
        this.isMarried = false;
        this.spouseId = null;
    }

    /**
     * Retorna a localização da casa compartilhada, ou null se não tiver.
     */
    public Vector3d getSharedHomeLocation() {
        if (!hasSharedHome) return null;
        return new Vector3d(homeX, homeY, homeZ);
    }

    /**
     * Define a localização da casa compartilhada.
     */
    public void setSharedHome(Vector3d pos) {
        if (pos != null) {
            this.hasSharedHome = true;
            this.homeX = pos.x;
            this.homeY = pos.y;
            this.homeZ = pos.z;
        }
    }

    /**
     * Número total de filhos.
     */
    public int getChildCount() {
        return children.size();
    }

    /**
     * Verifica se pode ter mais filhos (limite de 4 por casal).
     */
    public boolean canHaveMoreChildren() {
        return children.size() < 4;
    }
}
