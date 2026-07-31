package com.trading.strategy.impl;

import com.trading.marketdata.domain.BarInterval;
import com.trading.marketdata.domain.TechnicalIndicators;
import com.trading.strategy.domain.Signal;
import com.trading.strategy.domain.StrategyContext;
import com.trading.strategy.domain.StrategyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiIndicatorStrategyTest {

    @Mock MovingAverageCrossStrategy maCross;
    @Mock MACDStrategy               macd;
    @Mock RSIStrategy                rsi;
    @Mock BollingerBandStrategy      bollinger;

    private MultiIndicatorStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new MultiIndicatorStrategy(maCross, macd, rsi, bollinger);
    }

    @Test
    void evaluate_returnsBuy_whenAllStrategiesAgreeBuy() {
        stubAll(Signal.BUY, new BigDecimal("0.80"));
        var result = strategy.evaluate(ctx());
        assertThat(result.signal()).isEqualTo(Signal.BUY);
        assertThat(result.confidence()).isEqualByComparingTo(new BigDecimal("0.8000"));
    }

    @Test
    void evaluate_returnsSell_whenAllStrategiesAgreeSell() {
        stubAll(Signal.SELL, new BigDecimal("0.60"));
        var result = strategy.evaluate(ctx());
        assertThat(result.signal()).isEqualTo(Signal.SELL);
        assertThat(result.confidence()).isEqualByComparingTo(new BigDecimal("0.6000"));
    }

    @Test
    void evaluate_returnsHold_whenScoreWithinDeadband() {
        // two BUY(0.10) + two SELL(0.10) → signed sum = 0 → avg = 0.00
        when(maCross.evaluate(any())).thenReturn(r("MA_CROSS", Signal.BUY,  "0.10"));
        when(macd.evaluate(any())).thenReturn(r("MACD",     Signal.BUY,  "0.10"));
        when(rsi.evaluate(any())).thenReturn(r("RSI",      Signal.SELL, "0.10"));
        when(bollinger.evaluate(any())).thenReturn(r("BOLLINGER", Signal.SELL, "0.10"));
        var result = strategy.evaluate(ctx());
        assertThat(result.signal()).isEqualTo(Signal.HOLD);
    }

    @Test
    void evaluate_returnsBuy_whenAverageScoreAboveThreshold() {
        // BUY(0.44) × 4 → avg = 0.44 > 0.10
        stubAll(Signal.BUY, new BigDecimal("0.44"));
        var result = strategy.evaluate(ctx());
        assertThat(result.signal()).isEqualTo(Signal.BUY);
    }

    @Test
    void evaluate_returnsHold_atExactThreshold() {
        // avg exactly 0.10 → NOT strictly greater than threshold → HOLD
        stubAll(Signal.BUY, new BigDecimal("0.10"));
        var result = strategy.evaluate(ctx());
        assertThat(result.signal()).isEqualTo(Signal.HOLD);
    }

    @Test
    void evaluate_returnsSell_whenAverageScoreBelowNegativeThreshold() {
        stubAll(Signal.SELL, new BigDecimal("0.44"));
        var result = strategy.evaluate(ctx());
        assertThat(result.signal()).isEqualTo(Signal.SELL);
    }

    @Test
    void evaluate_reasonStringContainsAllSubStrategyNames() {
        stubAll(Signal.BUY, new BigDecimal("0.50"));
        var result = strategy.evaluate(ctx());
        assertThat(result.reason()).contains("MA_CROSS", "MACD", "RSI", "BOLLINGER");
        assertThat(result.reason()).contains("avg score");
    }

    @Test
    void evaluate_confidenceIsAverageScore_singleBuyOthersHold() {
        // BUY(0.60) + HOLD(0) + HOLD(0) + HOLD(0) → avg = 0.60/4 = 0.15
        when(maCross.evaluate(any())).thenReturn(r("MA_CROSS", Signal.BUY,  "0.60"));
        when(macd.evaluate(any())).thenReturn(r("MACD",     Signal.HOLD, "0.00"));
        when(rsi.evaluate(any())).thenReturn(r("RSI",      Signal.HOLD, "0.00"));
        when(bollinger.evaluate(any())).thenReturn(r("BOLLINGER", Signal.HOLD, "0.00"));
        var result = strategy.evaluate(ctx());
        assertThat(result.signal()).isEqualTo(Signal.BUY);
        assertThat(result.confidence()).isEqualByComparingTo(new BigDecimal("0.1500"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void stubAll(Signal signal, BigDecimal confidence) {
        when(maCross.evaluate(any())).thenReturn(r("MA_CROSS", signal, confidence.toPlainString()));
        when(macd.evaluate(any())).thenReturn(r("MACD",     signal, confidence.toPlainString()));
        when(rsi.evaluate(any())).thenReturn(r("RSI",      signal, confidence.toPlainString()));
        when(bollinger.evaluate(any())).thenReturn(r("BOLLINGER", signal, confidence.toPlainString()));
    }

    private StrategyResult r(String name, Signal signal, String confidence) {
        return new StrategyResult(name, "AAPL", signal, new BigDecimal(confidence), "test", Instant.now());
    }

    private StrategyContext ctx() {
        var ind = new TechnicalIndicators("AAPL", BarInterval.D1, Instant.now(),
                null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        return new StrategyContext("AAPL", BarInterval.D1, null, ind);
    }
}
