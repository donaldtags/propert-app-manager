package com.example.estatex.analytics.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.estatex.analytics.metrics.MetricDefinition;
import org.junit.jupiter.api.Test;

class DrillDownMatchTest {
    private static final MetricDefinition M = MetricDefinition.ACTIVE_LISTINGS;

    @Test
    void withoutALocationOnlyTheExactSliceMatches() {
        assertThat(DrillDownService.matches("ACTIVE_LISTINGS#*|*|*|*|*|*", M, null, null, null)).isTrue();
        assertThat(DrillDownService.matches("ACTIVE_LISTINGS#rent|*|*|*|*|*", M, null, null, null)).isFalse();
        assertThat(DrillDownService.matches("ACTIVE_LISTINGS#rent|*|*|*|*|*", M, "RENT", null, null)).isTrue();
        assertThat(DrillDownService.matches("NEW_LISTINGS#*|*|*|*|*|*", M, null, null, null)).isFalse();
    }

    @Test
    void aLocationCollectsEveryTypesRowForThatPlaceOnly() {
        assertThat(DrillDownService.matches("ACTIVE_LISTINGS#rent|zimbabwe|harare|*|*|*", M, null, "Harare", null)).isTrue();
        assertThat(DrillDownService.matches("ACTIVE_LISTINGS#sale|zimbabwe|harare|*|*|*", M, null, "Harare", null)).isTrue();
        assertThat(DrillDownService.matches("ACTIVE_LISTINGS#rent|zimbabwe|harare|avondale|*|*", M, null, "Harare", null)).isFalse();
        assertThat(DrillDownService.matches("ACTIVE_LISTINGS#rent|zimbabwe|harare|avondale|*|*", M, null, "Harare", "Avondale")).isTrue();
        assertThat(DrillDownService.matches("ACTIVE_LISTINGS#rent|zimbabwe|harare|*|flat|*", M, null, "Harare", null)).isFalse();
        assertThat(DrillDownService.matches("ACTIVE_LISTINGS#*|*|*|*|*|*", M, null, "Harare", null)).isFalse();
    }
}
