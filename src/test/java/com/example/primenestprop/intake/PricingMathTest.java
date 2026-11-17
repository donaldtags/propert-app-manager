package com.example.primenestprop.intake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.primenestprop.property.ListingType;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingMathTest {
    @Test
    void nearestRankPercentiles() {
        List<Long> p = List.of(100L, 200L, 300L, 400L, 500L);
        assertEquals(200L, IntakeService.percentile(p, 25));
        assertEquals(300L, IntakeService.percentile(p, 50));
        assertEquals(400L, IntakeService.percentile(p, 75));
        assertNull(IntakeService.percentile(List.of(), 50));
    }

    @Test
    void salesRoundToThousandsAndRentsToTens() {
        assertEquals(18_500_000L, IntakeService.round(18_456_789L, ListingType.SALE));
        assertEquals(65_000L, IntakeService.round(64_890L, ListingType.RENT));
    }
}
