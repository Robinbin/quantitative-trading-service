package com.trading.strategy.impl;

import com.trading.strategy.domain.Signal;
import com.trading.strategy.domain.StrategyContext;
import com.trading.strategy.domain.StrategyResult;
import com.trading.strategy.engine.Strategy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MULTI: Composite signal from MA_CROSS, MACD, RSI, and BOLLINGER.
 * Each sub-strategy result is converted to a signed score:
 *   BUY  → +confidence
 *   SELL → -confidence
 *   HOLD → 0
 * Average score determines final signal with a ±0.10 dead-band to filter noise.
 */
@Component
public class MultiIndicatorStrategy implements Strategy {

    private static final String NAME = "MULTI";
    private static final BigDecimal DEAD_BAND = new BigDecimal("0.10");
    private static final BigDecimal DIVISOR = new BigDecimal("4");

    private final MovingAverageCrossStrategy maCross;
    private final MACDStrategy               macd;
    private final RSIStrategy                rsi;
    private final BollingerBandStrategy      bollinger;

    public MultiIndicatorStrategy(MovingAverageCrossStrategy maCross,
                                  MACDStrategy               macd,
                                  RSIStrategy                rsi,
                                  BollingerBandStrategy      bollinger) {
        this.maCross  = maCross;
        this.macd     = macd;
        this.rsi      = rsi;
        this.bollinger = bollinger;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Composite signal from MA_CROSS, MACD, RSI, and BOLLINGER; averaged score determines final signal";
    }

    @Override
    public StrategyResult evaluate(StrategyContext context) {
        List<StrategyResult> subResults = List.of(
                maCross.evaluate(context),
                macd.evaluate(context),
                rsi.evaluate(context),
                bollinger.evaluate(context)
        );

        BigDecimal avgScore = subResults.stream()
                .map(r -> switch (r.signal()) {
                    case BUY  ->  r.confidence();
                    case SELL -> r.confidence().negate();
                    case HOLD -> BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(DIVISOR, 4, RoundingMode.HALF_UP);

        String subSummary = subResults.stream()
                .map(r -> String.format("%s=%s(%.2f)", r.strategyName(), r.signal(),
                        r.confidence().doubleValue()))
                .collect(Collectors.joining(", "));
        String reason = String.format("Composite [%s]; avg score=%s", subSummary, avgScore);

        int cmp = avgScore.compareTo(DEAD_BAND);
        if (cmp > 0) {
            return result(context.symbol(), Signal.BUY, avgScore.min(BigDecimal.ONE), reason);
        }
        if (avgScore.compareTo(DEAD_BAND.negate()) < 0) {
            return result(context.symbol(), Signal.SELL, avgScore.abs().min(BigDecimal.ONE), reason);
        }
        return result(context.symbol(), Signal.HOLD, BigDecimal.ZERO, reason);
    }

    private StrategyResult result(String symbol, Signal signal, BigDecimal confidence, String reason) {
        return new StrategyResult(NAME, symbol, signal, confidence, reason, Instant.now());
    }
}
