package com.example.primenestprop.market;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Reads live quotes from the MyStocks Africa partner API (https://mystocks.africa/partners/docs).
 * Only active when {@code app.market.mystocks.api-key} is set - without one, {@link #fetchQuote}
 * always returns null and {@link ZimbabweReitMarketService} falls back to its own ZSE price-sheet
 * scrape, the same graceful-degradation pattern {@code AiAnthropicConfig} uses for the AI assistant.
 *
 * <p>The exact response envelope for GET /stocks isn't fully documented publicly, so parsing here
 * is defensive: it accepts either a bare quote object, an array of quotes, or a {@code data} array
 * wrapper, and reads several plausible field-name variants for change/volume/timestamp.
 */
@Component
public class MystocksMarketClient {
    private static final Logger log = LoggerFactory.getLogger(MystocksMarketClient.class);

    private final RestClient restClient;

    public MystocksMarketClient(
            @Value("${app.market.mystocks.api-key:}") String apiKey,
            @Value("${app.market.mystocks.base-url:https://mystocks.africa/api/sandbox/v1/partner}") String baseUrl
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("app.market.mystocks.api-key is not set; live ZSE quotes will fall back to the price-sheet scrape.");
            this.restClient = null;
        } else {
            this.restClient = RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .build();
        }
    }

    public boolean isConfigured() {
        return restClient != null;
    }

    /** Fetches a single quote for a ZSE ticker (e.g. "TIG"), or null if unavailable. */
    public MarketQuote fetchQuote(String ticker, String fallbackName) {
        if (restClient == null || ticker == null || ticker.isBlank()) return null;
        try {
            JsonNode root = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/stocks")
                            .queryParam("search", ticker + ".ZW")
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode quote = firstQuoteNode(root);
            return quote == null ? null : toMarketQuote(quote, ticker, fallbackName);
        } catch (Exception e) {
            log.warn("MyStocks Africa quote lookup failed for {}: {}", ticker, e.getMessage());
            return null;
        }
    }

    private JsonNode firstQuoteNode(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) return null;
        if (root.isArray()) {
            return root.isEmpty() ? null : root.get(0);
        }
        for (String wrapperField : new String[] {"data", "results", "stocks", "items"}) {
            JsonNode wrapped = root.path(wrapperField);
            if (wrapped.isArray()) {
                return wrapped.isEmpty() ? null : wrapped.get(0);
            }
        }
        return root.has("symbol") || root.has("ticker") ? root : null;
    }

    private MarketQuote toMarketQuote(JsonNode node, String ticker, String fallbackName) {
        String name = textOrDefault(node, fallbackName, "name", "companyName");
        String exchange = textOrDefault(node, "ZSE", "exchange", "exchangeCode");
        String currency = textOrDefault(node, "ZWG", "currency");
        BigDecimal price = decimalOf(node, "price", "lastPrice", "close");
        BigDecimal change = decimalOf(node, "change", "changeAmount", "priceChange");
        BigDecimal changePercent = decimalOf(node, "changePercent", "changePercentage", "percentChange");
        if (changePercent == null) changePercent = derivedChangePercent(price, change);
        Long volume = longOf(node, "volume", "tradedVolume");

        return new MarketQuote(ticker, name, exchange, currency, price, change, changePercent, volume, Instant.now());
    }

    private BigDecimal derivedChangePercent(BigDecimal price, BigDecimal change) {
        if (price == null || change == null) return null;
        BigDecimal previousClose = price.subtract(change);
        if (previousClose.compareTo(BigDecimal.ZERO) == 0) return null;
        return change.divide(previousClose, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String textOrDefault(JsonNode node, String fallback, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return fallback;
    }

    private BigDecimal decimalOf(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isNumber()) return value.decimalValue();
            if (value.isTextual() && !value.asText().isBlank()) {
                try {
                    return new BigDecimal(value.asText());
                } catch (NumberFormatException ignored) {
                    // try the next field name
                }
            }
        }
        return null;
    }

    private Long longOf(JsonNode node, String... fields) {
        BigDecimal value = decimalOf(node, fields);
        return value == null ? null : value.longValue();
    }
}
