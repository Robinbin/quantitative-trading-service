package com.trading.marketdata.service;

import com.trading.marketdata.cache.MarketDataCacheService;
import com.trading.marketdata.domain.*;
import com.trading.marketdata.indicator.TechnicalIndicatorCalculator;
import com.trading.marketdata.provider.MarketDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketDataServiceImplTest {

    @Mock MarketDataProvider         provider;
    @Mock MarketDataCacheService     cache;
    @Mock TechnicalIndicatorCalculator calculator;

    @InjectMocks
    MarketDataServiceImpl service;

    private static final String SYMBOL = "AAPL";

    // ── getOhlcv ─────────────────────────────────────────────────────────────

    @Test
    void getOhlcv_shouldReturnCachedBarsWithoutCallingProvider() {
        List<OhlcvBar> cached = List.of(sampleBar());
        when(cache.getOhlcv(SYMBOL, BarInterval.D1)).thenReturn(Optional.of(cached));

        List<OhlcvBar> result = service.getOhlcv(SYMBOL, BarInterval.D1, 100);

        assertThat(result).isSameAs(cached);
        verify(provider, never()).fetchOhlcv(any(), any(), anyInt());
    }

    @Test
    void getOhlcv_shouldFetchAndCacheOnCacheMiss() {
        List<OhlcvBar> fetched = List.of(sampleBar());
        when(cache.getOhlcv(SYMBOL, BarInterval.D1)).thenReturn(Optional.empty());
        when(provider.fetchOhlcv(SYMBOL, BarInterval.D1, 100)).thenReturn(fetched);

        List<OhlcvBar> result = service.getOhlcv(SYMBOL, BarInterval.D1, 100);

        assertThat(result).isSameAs(fetched);
        verify(cache).putOhlcv(SYMBOL, BarInterval.D1, fetched);
    }

    // ── getTick ───────────────────────────────────────────────────────────────

    @Test
    void getTick_shouldReturnCachedTickWithoutCallingProvider() {
        TickQuote cached = sampleTick();
        when(cache.getTick(SYMBOL)).thenReturn(Optional.of(cached));

        TickQuote result = service.getTick(SYMBOL);

        assertThat(result).isSameAs(cached);
        verify(provider, never()).fetchTick(any());
    }

    @Test
    void getTick_shouldFetchAndCacheOnCacheMiss() {
        TickQuote fetched = sampleTick();
        when(cache.getTick(SYMBOL)).thenReturn(Optional.empty());
        when(provider.fetchTick(SYMBOL)).thenReturn(fetched);

        TickQuote result = service.getTick(SYMBOL);

        assertThat(result).isSameAs(fetched);
        verify(cache).putTick(SYMBOL, fetched);
    }

    @Test
    void getTick_shouldNotCacheNullResult() {
        when(cache.getTick(SYMBOL)).thenReturn(Optional.empty());
        when(provider.fetchTick(SYMBOL)).thenReturn(null);

        TickQuote result = service.getTick(SYMBOL);

        assertThat(result).isNull();
        verify(cache, never()).putTick(any(), any());
    }

    // ── getBatchTick ──────────────────────────────────────────────────────────

    @Test
    void getBatchTick_shouldReturnTickForEachSymbol() {
        TickQuote aaplTick = sampleTick();
        when(cache.getTick("AAPL")).thenReturn(Optional.of(aaplTick));
        when(cache.getTick("TSLA")).thenReturn(Optional.empty());
        when(provider.fetchTick("TSLA")).thenReturn(sampleTick());

        Map<String, TickQuote> result = service.getBatchTick(Set.of("AAPL", "TSLA"));

        assertThat(result).containsKey("AAPL").containsKey("TSLA");
    }

    @Test
    void getBatchTick_shouldSkipSymbolsWhereProviderReturnsNull() {
        when(cache.getTick("AAPL")).thenReturn(Optional.empty());
        when(provider.fetchTick("AAPL")).thenReturn(null);

        Map<String, TickQuote> result = service.getBatchTick(Set.of("AAPL"));

        assertThat(result).isEmpty();
    }

    @Test
    void getBatchTick_shouldContinueWhenOneSymbolThrows() {
        when(cache.getTick("AAPL")).thenReturn(Optional.empty());
        when(provider.fetchTick("AAPL")).thenThrow(new RuntimeException("timeout"));
        TickQuote tslaTickQuote = sampleTick();
        when(cache.getTick("TSLA")).thenReturn(Optional.of(tslaTickQuote));

        Map<String, TickQuote> result = service.getBatchTick(Set.of("AAPL", "TSLA"));

        assertThat(result).containsOnlyKeys("TSLA");
    }

    @Test
    void getBatchTick_shouldReturnEmptyMapForEmptyInput() {
        Map<String, TickQuote> result = service.getBatchTick(Set.of());
        assertThat(result).isEmpty();
    }

    @Test
    void getBatchTick_shouldDeduplicateSymbols() {
        TickQuote tick = sampleTick();
        when(cache.getTick("AAPL")).thenReturn(Optional.of(tick));

        // Set.of dedups on input, provider should only be called once
        service.getBatchTick(Set.of("AAPL"));

        verify(cache, times(1)).getTick("AAPL");
    }

    // ── getIndicators ─────────────────────────────────────────────────────────

    @Test
    void getIndicators_shouldReturnCachedIndicatorsWithoutCalculating() {
        TechnicalIndicators cached = sampleIndicators();
        when(cache.getIndicators(SYMBOL, BarInterval.D1)).thenReturn(Optional.of(cached));

        TechnicalIndicators result = service.getIndicators(SYMBOL, BarInterval.D1);

        assertThat(result).isSameAs(cached);
        verify(calculator, never()).calculate(any(), any(), any());
    }

    @Test
    void getIndicators_shouldCalculateAndCacheOnCacheMiss() {
        List<OhlcvBar> bars = List.of(sampleBar());
        TechnicalIndicators computed = sampleIndicators();
        when(cache.getIndicators(SYMBOL, BarInterval.D1)).thenReturn(Optional.empty());
        when(cache.getOhlcv(eq(SYMBOL), eq(BarInterval.D1))).thenReturn(Optional.of(bars));
        when(calculator.calculate(bars, SYMBOL, BarInterval.D1)).thenReturn(computed);

        TechnicalIndicators result = service.getIndicators(SYMBOL, BarInterval.D1);

        assertThat(result).isSameAs(computed);
        verify(cache).putIndicators(SYMBOL, BarInterval.D1, computed);
    }

    // ── getFundamental ────────────────────────────────────────────────────────

    @Test
    void getFundamental_shouldReturnCachedDataWithoutCallingProvider() {
        FundamentalData cached = sampleFundamental();
        when(cache.getFundamental(SYMBOL)).thenReturn(Optional.of(cached));

        FundamentalData result = service.getFundamental(SYMBOL);

        assertThat(result).isSameAs(cached);
        verify(provider, never()).fetchFundamental(any());
    }

    @Test
    void getFundamental_shouldFetchAndCacheOnCacheMiss() {
        FundamentalData fetched = sampleFundamental();
        when(cache.getFundamental(SYMBOL)).thenReturn(Optional.empty());
        when(provider.fetchFundamental(SYMBOL)).thenReturn(fetched);

        FundamentalData result = service.getFundamental(SYMBOL);

        assertThat(result).isSameAs(fetched);
        verify(cache).putFundamental(SYMBOL, fetched);
    }

    // ── refresh ───────────────────────────────────────────────────────────────

    @Test
    void refresh_shouldEvictCacheAndRefetchAllIntervals() {
        List<OhlcvBar> bars = List.of(sampleBar());
        when(provider.fetchOhlcv(eq(SYMBOL), any(), anyInt())).thenReturn(bars);
        when(calculator.calculate(any(), any(), any())).thenReturn(sampleIndicators());
        when(provider.fetchTick(SYMBOL)).thenReturn(sampleTick());

        service.refresh(SYMBOL);

        verify(cache).evictAll(SYMBOL);
        verify(provider, times(BarInterval.values().length)).fetchOhlcv(eq(SYMBOL), any(), anyInt());
        verify(provider).fetchTick(SYMBOL);
    }

    @Test
    void refresh_shouldContinueWhenOneIntervalFails() {
        when(provider.fetchOhlcv(eq(SYMBOL), eq(BarInterval.M1), anyInt()))
                .thenThrow(new RuntimeException("timeout"));
        when(provider.fetchOhlcv(eq(SYMBOL), eq(BarInterval.D1), anyInt()))
                .thenReturn(List.of(sampleBar()));
        when(calculator.calculate(any(), any(), any())).thenReturn(sampleIndicators());
        when(provider.fetchTick(SYMBOL)).thenReturn(sampleTick());

        // Should not throw
        service.refresh(SYMBOL);

        verify(cache).evictAll(SYMBOL);
    }

    @Test
    void refresh_shouldContinueWhenTickFails() {
        when(provider.fetchOhlcv(any(), any(), anyInt())).thenReturn(List.of(sampleBar()));
        when(calculator.calculate(any(), any(), any())).thenReturn(sampleIndicators());
        when(provider.fetchTick(SYMBOL)).thenThrow(new RuntimeException("network error"));

        // Should not throw
        service.refresh(SYMBOL);

        verify(cache).evictAll(SYMBOL);
    }

    // ── sample data ───────────────────────────────────────────────────────────

    private OhlcvBar sampleBar() {
        return new OhlcvBar(SYMBOL, Instant.now(), BarInterval.D1,
                new BigDecimal("180"), new BigDecimal("185"),
                new BigDecimal("178"), new BigDecimal("183"), 50_000_000L);
    }

    private TickQuote sampleTick() {
        return new TickQuote(SYMBOL, Instant.now(),
                new BigDecimal("183"), new BigDecimal("181"), new BigDecimal("185"),
                new BigDecimal("181"), new BigDecimal("181"), new BigDecimal("2"),
                new BigDecimal("0.011"), 50_000_000L, null, "USD", "NMS");
    }

    private TechnicalIndicators sampleIndicators() {
        return new TechnicalIndicators(SYMBOL, BarInterval.D1, Instant.now(),
                new BigDecimal("182"), null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    private FundamentalData sampleFundamental() {
        return new FundamentalData(SYMBOL, "Apple Inc.", "Technology", "Consumer Electronics",
                new BigDecimal("28.5"), null, null, null, null, null,
                new BigDecimal("6.43"), null, new BigDecimal("1.26"),
                new BigDecimal("199.62"), new BigDecimal("124.17"),
                java.time.LocalDate.now());
    }
}
