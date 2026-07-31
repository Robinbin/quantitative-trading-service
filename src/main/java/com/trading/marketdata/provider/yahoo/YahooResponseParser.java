package com.trading.marketdata.provider.yahoo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.marketdata.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class YahooResponseParser {

    private static final Logger log = LoggerFactory.getLogger(YahooResponseParser.class);

    private final ObjectMapper mapper = new ObjectMapper();

    public List<OhlcvBar> parseOhlcv(String json, String symbol, BarInterval interval) {
        List<OhlcvBar> bars = new ArrayList<>();
        try {
            JsonNode root   = mapper.readTree(json);
            JsonNode result = root.path("chart").path("result").get(0);
            if (result == null || result.isMissingNode()) return bars;

            JsonNode timestamps = result.path("timestamp");
            JsonNode quote      = result.path("indicators").path("quote").get(0);
            if (quote == null) return bars;

            JsonNode opens   = quote.path("open");
            JsonNode highs   = quote.path("high");
            JsonNode lows    = quote.path("low");
            JsonNode closes  = quote.path("close");
            JsonNode volumes = quote.path("volume");

            for (int i = 0; i < timestamps.size(); i++) {
                if (closes.get(i).isNull()) continue;
                bars.add(new OhlcvBar(
                        symbol,
                        Instant.ofEpochSecond(timestamps.get(i).asLong()),
                        interval,
                        bd(opens.get(i)),
                        bd(highs.get(i)),
                        bd(lows.get(i)),
                        bd(closes.get(i)),
                        volumes.get(i).isNull() ? 0L : volumes.get(i).asLong()
                ));
            }
        } catch (Exception e) {
            log.error("Failed to parse OHLCV for {}: {}", symbol, e.getMessage());
        }
        return bars;
    }

    public TickQuote parseTick(String json, String symbol) {
        try {
            JsonNode root  = mapper.readTree(json);
            JsonNode price = root.path("quoteSummary").path("result").get(0).path("price");

            return new TickQuote(
                    symbol,
                    Instant.now(),
                    rawBd(price, "regularMarketPrice"),
                    rawBd(price, "regularMarketOpen"),
                    rawBd(price, "regularMarketDayHigh"),
                    rawBd(price, "regularMarketDayLow"),
                    rawBd(price, "regularMarketPreviousClose"),
                    rawBd(price, "regularMarketChange"),
                    rawBd(price, "regularMarketChangePercent"),
                    price.path("regularMarketVolume").path("raw").asLong(0),
                    rawBd(price, "marketCap"),
                    price.path("currency").asText("USD"),
                    price.path("exchangeName").asText("")
            );
        } catch (Exception e) {
            log.error("Failed to parse tick for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    public FundamentalData parseFundamental(String json, String symbol) {
        try {
            JsonNode root   = mapper.readTree(json);
            JsonNode result = root.path("quoteSummary").path("result").get(0);
            JsonNode stats  = result.path("defaultKeyStatistics");
            JsonNode finData = result.path("financialData");
            JsonNode summary = result.path("summaryDetail");
            JsonNode profile = result.path("assetProfile");

            return new FundamentalData(
                    symbol,
                    profile.path("longName").asText(""),
                    profile.path("sector").asText(""),
                    profile.path("industry").asText(""),
                    rawBd(summary, "trailingPE"),
                    rawBd(summary, "forwardPE"),
                    rawBd(stats, "priceToBook"),
                    rawBd(finData, "priceToSalesTrailing12Months"),
                    rawBd(finData, "totalRevenue"),
                    rawBd(finData, "netIncomeToCommon"),
                    rawBd(stats, "trailingEps"),
                    rawBd(summary, "dividendYield"),
                    rawBd(stats, "beta"),
                    rawBd(summary, "fiftyTwoWeekHigh"),
                    rawBd(summary, "fiftyTwoWeekLow"),
                    LocalDate.now()
            );
        } catch (Exception e) {
            log.error("Failed to parse fundamental for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    private BigDecimal bd(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return new BigDecimal(node.asText());
    }

    private BigDecimal rawBd(JsonNode parent, String field) {
        JsonNode node = parent.path(field).path("raw");
        if (node.isMissingNode() || node.isNull()) return null;
        return new BigDecimal(node.asText());
    }
}
