package com.cookieukw.SimTale.core.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class LifecycleState {

    public static final List<GrowthComponent> ACTIVE_CHILDREN = new CopyOnWriteArrayList<>();

    public static List<GrowthComponent> findChildrenOfMother(UUID motherId) {
        List<GrowthComponent> result = new ArrayList<>();
        for (GrowthComponent child : ACTIVE_CHILDREN) {
            if (motherId.equals(child.motherId)) {
                result.add(child);
            }
        }
        return result;
    }

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
