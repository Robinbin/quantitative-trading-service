package com.trading.marketdata.scheduler;

import com.trading.marketdata.config.MarketDataProperties;
import com.trading.marketdata.domain.BarInterval;
import com.trading.marketdata.domain.TickQuote;
import com.trading.marketdata.service.MarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketDataRefreshSchedulerTest {

    @Mock MarketDataService service;

    private MarketDataRefreshScheduler scheduler;

    @BeforeEach
    void setUp() {
        MarketDataProperties props = new MarketDataProperties(
                List.of("AAPL", "TSLA"), 30, 5);
        scheduler = new MarketDataRefreshScheduler(service, props);
    }

    @Test
    void refreshTicks_shouldCallGetTickForEachSymbol() {
        TickQuote tick = new TickQuote("AAPL", Instant.now(),
                new BigDecimal("183"), null, null, null, null, null, null,
                0L, null, "USD", "NMS");
        when(service.getTick("AAPL")).thenReturn(tick);
        when(service.getTick("TSLA")).thenReturn(tick);

        scheduler.refreshTicks();

        verify(service).getTick("AAPL");
        verify(service).getTick("TSLA");
    }

    @Test
    void refreshTicks_shouldContinueWhenOneSymbolFails() {
        when(service.getTick("AAPL")).thenThrow(new RuntimeException("timeout"));

        // Should not throw
        scheduler.refreshTicks();

        verify(service).getTick("AAPL");
        verify(service).getTick("TSLA");
    }

    @Test
    void refreshOhlcvAndIndicators_shouldCallRefreshForEachSymbol() {
        scheduler.refreshOhlcvAndIndicators();

        verify(service).refresh("AAPL");
        verify(service).refresh("TSLA");
    }

    @Test
    void refreshOhlcvAndIndicators_shouldContinueWhenOneSymbolFails() {
        doThrow(new RuntimeException("error")).when(service).refresh("AAPL");

        // Should not throw
        scheduler.refreshOhlcvAndIndicators();

        verify(service).refresh("AAPL");
        verify(service).refresh("TSLA");
    }

    @Test
    void refreshFundamentals_shouldCallGetFundamentalForEachSymbol() {
        scheduler.refreshFundamentals();

        verify(service).getFundamental("AAPL");
        verify(service).getFundamental("TSLA");
    }

    @Test
    void refreshFundamentals_shouldContinueWhenOneSymbolFails() {
        when(service.getFundamental("AAPL")).thenThrow(new RuntimeException("error"));

        // Should not throw
        scheduler.refreshFundamentals();

        verify(service).getFundamental("AAPL");
        verify(service).getFundamental("TSLA");
    }

    @Test
    void shouldDoNothingWithEmptyWatchList() {
        MarketDataProperties emptyProps = new MarketDataProperties(List.of(), 30, 5);
        scheduler = new MarketDataRefreshScheduler(service, emptyProps);

        scheduler.refreshTicks();
        scheduler.refreshOhlcvAndIndicators();
        scheduler.refreshFundamentals();

        verifyNoInteractions(service);
    }
}
