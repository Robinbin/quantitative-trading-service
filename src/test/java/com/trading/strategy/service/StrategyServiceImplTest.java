package com.trading.strategy.service;

import com.trading.marketdata.config.MarketDataProperties;
import com.trading.marketdata.domain.BarInterval;
import com.trading.marketdata.domain.TechnicalIndicators;
import com.trading.marketdata.domain.TickQuote;
import com.trading.marketdata.service.MarketDataService;
import com.trading.strategy.domain.Signal;
import com.trading.strategy.domain.StrategyContext;
import com.trading.strategy.domain.StrategyResult;
import com.trading.strategy.engine.StrategyEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StrategyServiceImplTest {

    @Mock MarketDataService    marketDataService;
    @Mock StrategyEngine       engine;
    @Mock MarketDataProperties props;

    private StrategyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StrategyServiceImpl(marketDataService, engine, props);
    }

    @Test
    void evaluate_fetchesTickAndIndicators_thenDelegatesToEngine() {
        when(marketDataService.getTick("AAPL")).thenReturn(sampleTick());
        when(marketDataService.getIndicators("AAPL", BarInterval.D1)).thenReturn(sampleIndicators());
        when(engine.evaluate(eq("RSI"), any())).thenReturn(sampleResult());

        service.evaluate("AAPL", "RSI", BarInterval.D1);

        verify(marketDataService).getTick("AAPL");
        verify(marketDataService).getIndicators("AAPL", BarInterval.D1);
        verify(engine).evaluate(eq("RSI"), any(StrategyContext.class));
    }

    @Test
    void evaluate_passesNullTickGracefully() {
        when(marketDataService.getTick("AAPL")).thenReturn(null);
        when(marketDataService.getIndicators("AAPL", BarInterval.D1)).thenReturn(sampleIndicators());
        when(engine.evaluate(eq("RSI"), any())).thenReturn(sampleResult());

        service.evaluate("AAPL", "RSI", BarInterval.D1);

        ArgumentCaptor<StrategyContext> captor = ArgumentCaptor.forClass(StrategyContext.class);
        verify(engine).evaluate(eq("RSI"), captor.capture());
        assertThat(captor.getValue().tick()).isNull();
    }

    @Test
    void evaluateAll_callsEngineEvaluateAll() {
        when(marketDataService.getTick("AAPL")).thenReturn(sampleTick());
        when(marketDataService.getIndicators("AAPL", BarInterval.D1)).thenReturn(sampleIndicators());
        when(engine.evaluateAll(any())).thenReturn(List.of(sampleResult()));

        var results = service.evaluateAll("AAPL", BarInterval.D1);

        verify(engine).evaluateAll(any(StrategyContext.class));
        assertThat(results).hasSize(1);
    }

    @Test
    void scan_evaluatesAllWatchListSymbols() {
        when(props.watchList()).thenReturn(List.of("AAPL", "TSLA"));
        when(marketDataService.getTick(any())).thenReturn(sampleTick());
        when(marketDataService.getIndicators(any(), any())).thenReturn(sampleIndicators());
        when(engine.evaluateAll(any())).thenReturn(List.of(sampleResult()));

        var scanResult = service.scan(BarInterval.D1);

        assertThat(scanResult).containsKeys("AAPL", "TSLA");
        assertThat(scanResult.get("AAPL")).hasSize(1);
    }

    @Test
    void scan_continuesWhenOneSymbolThrows() {
        when(props.watchList()).thenReturn(List.of("AAPL", "TSLA"));
        when(marketDataService.getTick("AAPL")).thenThrow(new RuntimeException("network error"));
        when(marketDataService.getTick("TSLA")).thenReturn(sampleTick());
        when(marketDataService.getIndicators("TSLA", BarInterval.D1)).thenReturn(sampleIndicators());
        when(engine.evaluateAll(any())).thenReturn(List.of(sampleResult()));

        var scanResult = service.scan(BarInterval.D1);

        assertThat(scanResult).containsKeys("AAPL", "TSLA");
        assertThat(scanResult.get("AAPL")).isEmpty();
        assertThat(scanResult.get("TSLA")).hasSize(1);
    }

    @Test
    void scan_returnsEmptyMap_forEmptyWatchList() {
        when(props.watchList()).thenReturn(List.of());

        var scanResult = service.scan(BarInterval.D1);

        assertThat(scanResult).isEmpty();
        verifyNoInteractions(engine, marketDataService);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private TickQuote sampleTick() {
        return new TickQuote("AAPL", Instant.now(),
                new BigDecimal("183"), null, null, null, null, null, null,
                0L, null, "USD", "NMS");
    }

    private TechnicalIndicators sampleIndicators() {
        return new TechnicalIndicators("AAPL", BarInterval.D1, Instant.now(),
                null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    private StrategyResult sampleResult() {
        return new StrategyResult("RSI", "AAPL", Signal.HOLD, BigDecimal.ZERO, "test", Instant.now());
    }
}
