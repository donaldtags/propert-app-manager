package com.example.estatex.report;

/**
 * Plain-language price advice for the weekly seller report, from evidence the agency actually
 * holds. Deliberately conservative and explainable, never a black box.
 */
public final class PriceAdvice {
    public enum Kind { REVIEW_DOWN, REFRESH, ABOVE_MARKET, HOLD }

    public record Advice(Kind kind, String text) {
    }

    private PriceAdvice() {
    }

    public static Advice advise(int completedViewings, int tooHigh, int enquiriesThisWeek, long daysOnMarket,
                                long askingCents, Long recommendedCents, String currency) {
        if (completedViewings >= 3 && tooHigh * 2 >= completedViewings) {
            long target = recommendedCents != null && recommendedCents < askingCents ? recommendedCents : Math.round(askingCents * 0.95);
            return new Advice(Kind.REVIEW_DOWN, tooHigh + " of " + completedViewings
                    + " viewers felt the price is too high. We recommend reviewing it toward " + money(target, currency) + ".");
        }
        if (daysOnMarket >= 14 && enquiriesThisWeek == 0) {
            return new Advice(Kind.REFRESH, "No new enquiries this week after " + daysOnMarket
                    + " days on the market. We recommend refreshing the photos and wording, and reviewing the price together.");
        }
        if (recommendedCents != null && askingCents > Math.round(recommendedCents * 1.10)) {
            return new Advice(Kind.ABOVE_MARKET, "The asking price is more than 10% above comparable asking prices (our recommendation: "
                    + money(recommendedCents, currency) + "). Expect slower interest unless the property clearly stands out.");
        }
        return new Advice(Kind.HOLD, "Interest is in line with the asking price. We recommend keeping the current strategy this week.");
    }

    static String money(long cents, String currency) {
        String symbol = "USD".equals(currency) ? "US$" : currency + " ";
        return symbol + String.format("%,.0f", cents / 100.0);
    }
}
