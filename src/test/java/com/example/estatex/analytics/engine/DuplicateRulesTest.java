package com.example.estatex.analytics.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DuplicateRulesTest {
    private static DataQualityChecker.Live live(long id, long landlord, String title, long price) {
        return new DataQualityChecker.Live(id, landlord, null, "RENT", "Avondale", "Harare", 3, 2, BigDecimal.valueOf(price),
                "USD", title, "HOUSE", true);
    }

    @Test
    void sameListerSimilarPriceIsFlagged() {
        assertThat(DataQualityChecker.reason(live(1, 7, "A", 1000), live(2, 7, "B", 1025))).contains("Same lister");
        assertThat(DataQualityChecker.reason(live(1, 7, "A", 1000), live(2, 7, "B", 1100))).isNull();
    }

    @Test
    void differentListersNeedTheSameTitleAndAVeryClosePrice() {
        assertThat(DataQualityChecker.reason(live(1, 7, "Lovely 3-bed home!", 1000), live(2, 8, "lovely 3 bed home", 1005)))
                .contains("different listers");
        assertThat(DataQualityChecker.reason(live(1, 7, "Lovely 3-bed home", 1000), live(2, 8, "Other home", 1000))).isNull();
        assertThat(DataQualityChecker.reason(live(1, 7, "Same", 1000), live(2, 8, "Same", 1030))).isNull();
    }
}
