package com.trading.marketdata.service;

import com.trading.marketdata.cache.MarketDataCacheService;
import com.trading.marketdata.domain.*;
import com.trading.marketdata.indicator.TechnicalIndicatorCalculator;
import com.trading.marketdata.provider.MarketDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

@Service
public class MarketDataServiceImpl implements MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataServiceImpl.class);
    private static final int INDICATOR_BAR_COUNT = 200;

    private final MarketDataProvider         provider;
    private final MarketDataCacheService     cache;
    private final TechnicalIndicatorCalculator calculator;

    public MarketDataServiceImpl(MarketDataProvider provider,
                                  MarketDataCacheService cache,
                                  TechnicalIndicatorCalculator calculator) {
        this.provider   = provider;
        this.cache      = cache;
        this.calculator = calculator;
    }

    @Override
    public List<OhlcvBar> getOhlcv(String symbol, BarInterval interval, int limit) {
        return cache.getOhlcv(symbol, interval).orElseGet(() -> {
            log.debug("Cache miss OHLCV {}:{}", symbol, interval);
            List<OhlcvBar> bars = provider.fetchOhlcv(symbol, interval, limit);
            cache.putOhlcv(symbol, interval, bars);
            return bars;
        });
    }

    @Override
    public TickQuote getTick(String symbol) {
        return cache.getTick(symbol).orElseGet(() -> {
            log.debug("Cache miss Tick {}", symbol);
            TickQuote tick = provider.fetchTick(symbol);
            if (tick != null) cache.putTick(symbol, tick);
            return tick;
        });
    }

    @Override
    public Map<String, TickQuote> getBatchTick(Set<String> symbols) {
        Map<String, TickQuote> result = new ConcurrentSkipListMap<>();
        symbols.parallelStream().forEach(symbol -> {
            try {
                TickQuote tick = getTick(symbol);
                if (tick != null) result.put(symbol, tick);
            } catch (Exception e) {
                log.warn("Failed to fetch tick for {} in batch request: {}", symbol, e.getMessage());
            }
        });
        return new TreeMap<>(result);
    }

    @Override
    public TechnicalIndicators getIndicators(String symbol, BarInterval interval) {
        return cache.getIndicators(symbol, interval).orElseGet(() -> {
            log.debug("Cache miss Indicators {}:{}", symbol, interval);
            List<OhlcvBar> bars = getOhlcv(symbol, interval, INDICATOR_BAR_COUNT);
            TechnicalIndicators ti = calculator.calculate(bars, symbol, interval);
            cache.putIndicators(symbol, interval, ti);
            return ti;
        });
    }

    @Override
    public FundamentalData getFundamental(String symbol) {
        return cache.getFundamental(symbol).orElseGet(() -> {
            log.debug("Cache miss Fundamental {}", symbol);
            FundamentalData fd = provider.fetchFundamental(symbol);
            if (fd != null) cache.putFundamental(symbol, fd);
            return fd;
        });
    }

    @Override
    public void refresh(String symbol) {
        log.info("Refreshing market data for {}", symbol);
        cache.evictAll(symbol);
        for (BarInterval interval : BarInterval.values()) {
            try {
                List<OhlcvBar> bars = provider.fetchOhlcv(symbol, interval, INDICATOR_BAR_COUNT);
                cache.putOhlcv(symbol, interval, bars);
                TechnicalIndicators ti = calculator.calculate(bars, symbol, interval);
                cache.putIndicators(symbol, interval, ti);
            } catch (Exception e) {
                log.warn("Failed to refresh OHLCV/Indicators for {}:{} - {}", symbol, interval, e.getMessage());
            }
        }
        try {
            TickQuote tick = provider.fetchTick(symbol);
            if (tick != null) cache.putTick(symbol, tick);
        } catch (Exception e) {
            log.warn("Failed to refresh Tick for {} - {}", symbol, e.getMessage());
        }
    }
}
