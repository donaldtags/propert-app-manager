package com.example.estatex.analytics.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DimsTest {
    @Test
    void textIsNormalisedForGrouping() {
        Dims a = Dims.of("RENT", "Zimbabwe", " Harare ", "Mount  Pleasant", "HOUSE", 3);
        Dims b = Dims.of("RENT", "zimbabwe", "harare", "mount pleasant", "HOUSE", 3);
        assertThat(a.key()).isEqualTo(b.key()).isEqualTo("rent|zimbabwe|harare|mount pleasant|house|3");
    }

    @Test
    void bedroomBands() {
        assertThat(Dims.bedroomBand(0)).isEqualTo("0");
        assertThat(Dims.bedroomBand(4)).isEqualTo("4");
        assertThat(Dims.bedroomBand(7)).isEqualTo("5+");
        assertThat(Dims.bedroomBand(null)).isNull();
    }

    @Test
    void hourlyFiguresOnlyGetCoarseSlices() {
        Dims d = Dims.of("RENT", "Zimbabwe", "Harare", "Avondale", "FLAT", 2);
        assertThat(d.rollups(true, true)).extracting(Dims::key)
                .containsExactly("*|*|*|*|*|*", "rent|*|*|*|*|*", "rent|zimbabwe|harare|*|*|*");
        assertThat(d.rollups(false, false)).extracting(Dims::key).contains(
                "rent|zimbabwe|harare|avondale|*|*", "rent|*|*|*|flat|2", "rent|zimbabwe|harare|*|flat|*");
    }

    @Test
    void searchesWithoutACityAreNotForcedIntoOne() {
        Dims d = Dims.of("SALE", null, null, null, null, 3);
        assertThat(d.rollups(false, false)).extracting(Dims::key).containsExactly("sale|*|*|*|*|*", "sale|*|*|*|*|3");
    }
}
