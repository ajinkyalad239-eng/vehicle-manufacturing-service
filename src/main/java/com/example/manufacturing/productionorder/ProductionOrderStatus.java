package com.example.manufacturing.productionorder;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Life-cycle of a production order.
 * <pre>
 * PLANNED ──► IN_PROGRESS ──► COMPLETED
 *    │          │    ▲
 *    │          ▼    │
 *    │        ON_HOLD
 *    │          │
 *    └──────────┴──────► CANCELLED
 * </pre>
 * COMPLETED and CANCELLED are final.
 */
public enum ProductionOrderStatus {
    PLANNED,
    IN_PROGRESS,
    ON_HOLD,
    COMPLETED,
    CANCELLED;

    private static final Map<ProductionOrderStatus, Set<ProductionOrderStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(ProductionOrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(PLANNED, EnumSet.of(IN_PROGRESS, CANCELLED));
        ALLOWED_TRANSITIONS.put(IN_PROGRESS, EnumSet.of(ON_HOLD, COMPLETED, CANCELLED));
        ALLOWED_TRANSITIONS.put(ON_HOLD, EnumSet.of(IN_PROGRESS, CANCELLED));
        ALLOWED_TRANSITIONS.put(COMPLETED, EnumSet.noneOf(ProductionOrderStatus.class));
        ALLOWED_TRANSITIONS.put(CANCELLED, EnumSet.noneOf(ProductionOrderStatus.class));
    }

    public boolean canTransitionTo(ProductionOrderStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    public boolean isFinal() {
        return ALLOWED_TRANSITIONS.get(this).isEmpty();
    }
}
