package com.trading.marketdata.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.trading.marketdata.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.trading.marketdata.config.MarketDataCacheConfig.*;
import static org.assertj.core.api.Assertions.assertThat;

class MarketDataCacheServiceTest {

    private MarketDataCacheService cacheService;

    @BeforeEach
    void setUp() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheNames(List.of(CACHE_OHLCV, CACHE_TICK, CACHE_INDICATORS, CACHE_FUNDAMENTAL));
        manager.registerCustomCache(CACHE_TICK,
                Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(100).build());
        manager.registerCustomCache(CACHE_OHLCV,
                Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(100).build());
        manager.registerCustomCache(CACHE_INDICATORS,
                Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(100).build());
        manager.registerCustomCache(CACHE_FUNDAMENTAL,
                Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(100).build());
        cacheService = new MarketDataCacheService(manager);
    }

    // ── OHLCV ───────────────────────────────────────────────────────────────

    @Test
    void getOhlcv_shouldReturnEmptyBeforePut() {
        assertThat(cacheService.getOhlcv("AAPL", BarInterval.D1)).isEmpty();
    }

    @Test
    void getOhlcv_shouldReturnValueAfterPut() {
        List<OhlcvBar> bars = List.of(sampleBar());
        cacheService.putOhlcv("AAPL", BarInterval.D1, bars);

        Optional<List<OhlcvBar>> result = cacheService.getOhlcv("AAPL", BarInterval.D1);
        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(1);
    }

    @Test
    void getOhlcv_shouldBeScopedByInterval() {
        List<OhlcvBar> bars = List.of(sampleBar());
        cacheService.putOhlcv("AAPL", BarInterval.D1, bars);

        assertThat(cacheService.getOhlcv("AAPL", BarInterval.H1)).isEmpty();
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Test
    void getTick_shouldReturnEmptyBeforePut() {
        assertThat(cacheService.getTick("AAPL")).isEmpty();
    }

    @Test
    void getTick_shouldReturnValueAfterPut() {
        cacheService.putTick("AAPL", sampleTick());

        assertThat(cacheService.getTick("AAPL")).isPresent();
        assertThat(cacheService.getTick("AAPL").get().symbol()).isEqualTo("AAPL");
    }

    @Test
    void getTick_shouldBeScopedBySymbol() {
        cacheService.putTick("AAPL", sampleTick());

        assertThat(cacheService.getTick("TSLA")).isEmpty();
    }

    // ── Indicators ───────────────────────────────────────────────────────────

    @Test
    void getIndicators_shouldReturnEmptyBeforePut() {
        assertThat(cacheService.getIndicators("AAPL", BarInterval.D1)).isEmpty();
    }

    @Test
    void getIndicators_shouldReturnValueAfterPut() {
        cacheService.putIndicators("AAPL", BarInterval.D1, sampleIndicators());

        assertThat(cacheService.getIndicators("AAPL", BarInterval.D1)).isPresent();
    }

    // ── Fundamental ──────────────────────────────────────────────────────────

    @Test
    void getFundamental_shouldReturnEmptyBeforePut() {
        assertThat(cacheService.getFundamental("AAPL")).isEmpty();
    }

    @Test
    void getFundamental_shouldReturnValueAfterPut() {
        cacheService.putFundamental("AAPL", sampleFundamental());

        assertThat(cacheService.getFundamental("AAPL")).isPresent();
    }

    // ── evictAll ─────────────────────────────────────────────────────────────

    @Test
    void evictAll_shouldClearAllCachesForSymbol() {
        cacheService.putOhlcv("AAPL", BarInterval.D1, List.of(sampleBar()));
        cacheService.putTick("AAPL", sampleTick());
        cacheService.putIndicators("AAPL", BarInterval.D1, sampleIndicators());
        cacheService.putFundamental("AAPL", sampleFundamental());

        cacheService.evictAll("AAPL");

        assertThat(cacheService.getOhlcv("AAPL", BarInterval.D1)).isEmpty();
        assertThat(cacheService.getTick("AAPL")).isEmpty();
        assertThat(cacheService.getIndicators("AAPL", BarInterval.D1)).isEmpty();
        assertThat(cacheService.getFundamental("AAPL")).isEmpty();
    }

    @Test
    void evictAll_shouldNotAffectOtherSymbols() {
        cacheService.putTick("AAPL", sampleTick());
        cacheService.putTick("TSLA", sampleTick());

        cacheService.evictAll("AAPL");

        assertThat(cacheService.getTick("AAPL")).isEmpty();
        assertThat(cacheService.getTick("TSLA")).isPresent();
    }

    // ── sample data ──────────────────────────────────────────────────────────

    private OhlcvBar sampleBar() {
        return new OhlcvBar("AAPL", Instant.now(), BarInterval.D1,
                new BigDecimal("180"), new BigDecimal("185"),
                new BigDecimal("178"), new BigDecimal("183"), 50_000_000L);
    }

    private TickQuote sampleTick() {
        return new TickQuote("AAPL", Instant.now(),
                new BigDecimal("183"), new BigDecimal("181"),
                new BigDecimal("185"), new BigDecimal("181"),
                new BigDecimal("181"), new BigDecimal("2"), new BigDecimal("0.011"),
                50_000_000L, new BigDecimal("2850000000000"), "USD", "NMS");
    }

    private TechnicalIndicators sampleIndicators() {
        return new TechnicalIndicators("AAPL", BarInterval.D1, Instant.now(),
                new BigDecimal("182"), null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    private FundamentalData sampleFundamental() {
        return new FundamentalData("AAPL", "Apple Inc.", "Technology", "Consumer Electronics",
                new BigDecimal("28.5"), null, null, null,
                null, null, new BigDecimal("6.43"), null, new BigDecimal("1.26"),
                new BigDecimal("199.62"), new BigDecimal("124.17"), LocalDate.now());
    }
}
