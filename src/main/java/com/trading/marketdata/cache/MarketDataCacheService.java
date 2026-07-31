package com.trading.marketdata.cache;

import com.trading.marketdata.domain.*;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import static com.trading.marketdata.config.MarketDataCacheConfig.*;

@Service
public class MarketDataCacheService {

    private final Cache ohlcvCache;
    private final Cache tickCache;
    private final Cache indicatorsCache;
    private final Cache fundamentalCache;

    public MarketDataCacheService(CacheManager cacheManager) {
        this.ohlcvCache      = cacheManager.getCache(CACHE_OHLCV);
        this.tickCache       = cacheManager.getCache(CACHE_TICK);
        this.indicatorsCache = cacheManager.getCache(CACHE_INDICATORS);
        this.fundamentalCache = cacheManager.getCache(CACHE_FUNDAMENTAL);
    }

    // OHLCV

    public void putOhlcv(String symbol, BarInterval interval, List<OhlcvBar> bars) {
        ohlcvCache.put(key(symbol, interval), bars);
    }

    @SuppressWarnings("unchecked")
    public Optional<List<OhlcvBar>> getOhlcv(String symbol, BarInterval interval) {
        Cache.ValueWrapper v = ohlcvCache.get(key(symbol, interval));
        return Optional.ofNullable(v).map(w -> (List<OhlcvBar>) w.get());
    }

    // Tick

    public void putTick(String symbol, TickQuote tick) {
        tickCache.put(symbol, tick);
    }

    public Optional<TickQuote> getTick(String symbol) {
        Cache.ValueWrapper v = tickCache.get(symbol);
        return Optional.ofNullable(v).map(w -> (TickQuote) w.get());
    }

    // Indicators

    public void putIndicators(String symbol, BarInterval interval, TechnicalIndicators ti) {
        indicatorsCache.put(key(symbol, interval), ti);
    }

    public Optional<TechnicalIndicators> getIndicators(String symbol, BarInterval interval) {
        Cache.ValueWrapper v = indicatorsCache.get(key(symbol, interval));
        return Optional.ofNullable(v).map(w -> (TechnicalIndicators) w.get());
    }

    // Fundamental

    public void putFundamental(String symbol, FundamentalData fd) {
        fundamentalCache.put(symbol, fd);
    }

    public Optional<FundamentalData> getFundamental(String symbol) {
        Cache.ValueWrapper v = fundamentalCache.get(symbol);
        return Optional.ofNullable(v).map(w -> (FundamentalData) w.get());
    }

    // Evict

    public void evictAll(String symbol) {
        for (BarInterval interval : BarInterval.values()) {
            ohlcvCache.evict(key(symbol, interval));
            indicatorsCache.evict(key(symbol, interval));
        }
        tickCache.evict(symbol);
        fundamentalCache.evict(symbol);
    }

    private String key(String symbol, BarInterval interval) {
        return symbol + "::" + interval.name();
    }
}
