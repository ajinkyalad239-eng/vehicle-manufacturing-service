package com.example.manufacturing.productionorder;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionOrderStatusTest {

    /** Full 5x5 transition matrix so no rule can change silently. */
    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @CsvSource({
            "PLANNED,     PLANNED,     false",
            "PLANNED,     IN_PROGRESS, true",
            "PLANNED,     ON_HOLD,     false",
            "PLANNED,     COMPLETED,   false",
            "PLANNED,     CANCELLED,   true",
            "IN_PROGRESS, PLANNED,     false",
            "IN_PROGRESS, IN_PROGRESS, false",
            "IN_PROGRESS, ON_HOLD,     true",
            "IN_PROGRESS, COMPLETED,   true",
            "IN_PROGRESS, CANCELLED,   true",
            "ON_HOLD,     PLANNED,     false",
            "ON_HOLD,     IN_PROGRESS, true",
            "ON_HOLD,     ON_HOLD,     false",
            "ON_HOLD,     COMPLETED,   false",
            "ON_HOLD,     CANCELLED,   true",
            "COMPLETED,   PLANNED,     false",
            "COMPLETED,   IN_PROGRESS, false",
            "COMPLETED,   ON_HOLD,     false",
            "COMPLETED,   COMPLETED,   false",
            "COMPLETED,   CANCELLED,   false",
            "CANCELLED,   PLANNED,     false",
            "CANCELLED,   IN_PROGRESS, false",
            "CANCELLED,   ON_HOLD,     false",
            "CANCELLED,   COMPLETED,   false",
            "CANCELLED,   CANCELLED,   false"
    })
    void canTransitionTo(ProductionOrderStatus from, ProductionOrderStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} final={1}")
    @CsvSource({
            "PLANNED,     false",
            "IN_PROGRESS, false",
            "ON_HOLD,     false",
            "COMPLETED,   true",
            "CANCELLED,   true"
    })
    void isFinal(ProductionOrderStatus status, boolean expected) {
        assertThat(status.isFinal()).isEqualTo(expected);
    }
}
