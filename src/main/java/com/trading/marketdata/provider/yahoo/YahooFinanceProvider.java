package com.trading.marketdata.provider.yahoo;

import com.trading.marketdata.domain.*;
import com.trading.marketdata.provider.MarketDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Component
public class YahooFinanceProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceProvider.class);

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final WebClient webClient;
    private final YahooResponseParser parser;

    public YahooFinanceProvider(
            WebClient.Builder builder,
            YahooResponseParser parser,
            @Value("${trading.market-data.yahoo-base-url:https://query1.finance.yahoo.com}") String baseUrl) {
        this.webClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
        this.parser = parser;
    }

    @Override
    public List<OhlcvBar> fetchOhlcv(String symbol, BarInterval interval, int limit) {
        log.debug("Fetching OHLCV for {} interval={}", symbol, interval);
        String json = webClient.get()
                .uri("/v8/finance/chart/{symbol}?interval={interval}&range={range}",
                        symbol, interval.getYahooInterval(), interval.getYahooRange())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();
        List<OhlcvBar> bars = parser.parseOhlcv(json, symbol, interval);
        // 仅返回最近 limit 根
        int from = Math.max(0, bars.size() - limit);
        return bars.subList(from, bars.size());
    }

    @Override
    public TickQuote fetchTick(String symbol) {
        log.debug("Fetching tick for {}", symbol);
        String json = webClient.get()
                .uri("/v10/finance/quoteSummary/{symbol}?modules=price", symbol)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();
        return parser.parseTick(json, symbol);
    }

    @Override
    public FundamentalData fetchFundamental(String symbol) {
        log.debug("Fetching fundamental for {}", symbol);
        String json = webClient.get()
                .uri("/v10/finance/quoteSummary/{symbol}?modules=defaultKeyStatistics,"
                        + "financialData,summaryDetail,assetProfile", symbol)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();
        return parser.parseFundamental(json, symbol);
    }
}
