package com.example.primenestprop.market;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Polls the ZSE listed-securities price sheet and caches quotes for Zimbabwe's
 * listed REITs (Tigere Property Fund, Revitus Property Opportunities REIT).
 * There is no real-time tick feed for the ZSE, so "live" here means the cache
 * is refreshed on a timer and the frontend polls this cache.
 */
@Service
public class ZimbabweReitMarketService {
    private static final Logger log = LoggerFactory.getLogger(ZimbabweReitMarketService.class);
    private static final String SOURCE_URL = "https://afx.kwayisi.org/zse/";
    private static final Map<String, String> TRACKED_REITS = Map.of(
            "TIG", "Tigere Property Fund REIT",
            "REV", "Revitus Property Opportunities REIT"
    );
    private static final long STALE_AFTER_MILLIS = 15 * 60 * 1000L;

    private final AtomicReference<List<MarketQuote>> cache = new AtomicReference<>(List.of());
    private final AtomicReference<Instant> lastSuccess = new AtomicReference<>();

    @Scheduled(initialDelay = 0, fixedRate = 120_000)
    public void refresh() {
        try {
            List<MarketQuote> quotes = fetch();
            if (!quotes.isEmpty()) {
                cache.set(quotes);
                lastSuccess.set(Instant.now());
            } else {
                log.warn("ZSE REIT market refresh returned no matching rows");
            }
        } catch (Exception e) {
            log.warn("Failed to refresh Zimbabwe REIT market quotes: {}", e.getMessage());
        }
    }

    public MarketSnapshot snapshot() {
        if (cache.get().isEmpty()) {
            refresh();
        }
        Instant success = lastSuccess.get();
        boolean stale = success == null || Instant.now().isAfter(success.plusMillis(STALE_AFTER_MILLIS));
        return new MarketSnapshot(cache.get(), success, stale, SOURCE_URL);
    }

    private List<MarketQuote> fetch() throws IOException {
        Document doc = Jsoup.connect(SOURCE_URL)
                .userAgent("Mozilla/5.0 (compatible; PrimeNestPropBot/1.0; +https://primenest.example)")
                .timeout(10_000)
                .get();

        for (Element table : doc.select("table")) {
            Elements headers = table.select("thead th");
            if (headers.size() < 5 || !headers.get(0).text().trim().equalsIgnoreCase("Ticker")) {
                continue;
            }

            Map<String, MarketQuote> byTicker = new LinkedHashMap<>();
            for (Element row : table.select("tbody tr")) {
                Elements cells = row.select("td");
                if (cells.size() < 5) continue;

                String ticker = cells.get(0).text().trim().toUpperCase();
                if (!TRACKED_REITS.containsKey(ticker)) continue;

                String name = cells.get(1).text().trim();
                BigDecimal volume = parseNumber(cells.get(2).text());
                BigDecimal price = parseNumber(cells.get(3).text());
                BigDecimal change = parseNumber(cells.get(4).text());
                BigDecimal changePercent = changePercent(price, change);

                byTicker.put(ticker, new MarketQuote(
                        ticker,
                        name.isBlank() ? TRACKED_REITS.get(ticker) : name,
                        "ZSE",
                        "ZWG",
                        price,
                        change,
                        changePercent,
                        volume == null ? null : volume.longValue(),
                        Instant.now()
                ));
            }
            if (!byTicker.isEmpty()) {
                return new ArrayList<>(byTicker.values());
            }
        }
        return List.of();
    }

    private BigDecimal changePercent(BigDecimal price, BigDecimal change) {
        if (price == null || change == null) return null;
        BigDecimal previousClose = price.subtract(change);
        if (previousClose.compareTo(BigDecimal.ZERO) == 0) return null;
        return change.divide(previousClose, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal parseNumber(String raw) {
        String cleaned = raw.trim().replace(",", "").replace("+", "");
        if (cleaned.isBlank() || cleaned.equals("-")) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}