package com.example.estatex.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PriceAdviceTest {
    @Test
    void mostViewersSayingTooHighMeansReviewTowardTheRecommendation() {
        PriceAdvice.Advice a = PriceAdvice.advise(4, 2, 3, 10, 25_000_000, 23_000_000L, "USD");
        assertEquals(PriceAdvice.Kind.REVIEW_DOWN, a.kind());
        assertTrue(a.text().contains("2 of 4") && a.text().contains("US$230,000"), a.text());
    }

    @Test
    void withoutARecommendationTheReviewTargetIsFivePercentLower() {
        assertTrue(PriceAdvice.advise(3, 3, 0, 5, 20_000_000, null, "USD").text().contains("US$190,000"));
    }

    @Test
    void quietListingAfterTwoWeeksNeedsARefresh() {
        assertEquals(PriceAdvice.Kind.REFRESH, PriceAdvice.advise(1, 0, 0, 15, 20_000_000, null, "USD").kind());
    }

    @Test
    void askingWellAboveComparablesIsFlagged() {
        assertEquals(PriceAdvice.Kind.ABOVE_MARKET, PriceAdvice.advise(2, 0, 2, 5, 30_000_000, 25_000_000L, "USD").kind());
    }

    @Test
    void otherwiseHold() {
        assertEquals(PriceAdvice.Kind.HOLD, PriceAdvice.advise(2, 1, 2, 5, 25_000_000, 25_000_000L, "USD").kind());
        // fewer than 3 viewings never triggers a price cut on its own
        assertEquals(PriceAdvice.Kind.HOLD, PriceAdvice.advise(2, 2, 1, 3, 25_000_000, null, "USD").kind());
    }
}
