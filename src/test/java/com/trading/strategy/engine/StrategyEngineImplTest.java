package com.trading.strategy.engine;

import com.trading.marketdata.domain.BarInterval;
import com.trading.marketdata.domain.TechnicalIndicators;
import com.trading.strategy.domain.Signal;
import com.trading.strategy.domain.StrategyContext;
import com.trading.strategy.domain.StrategyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyEngineImplTest {

    private StrategyEngineImpl engine;
    private Strategy strategyA;
    private Strategy strategyB;

    @BeforeEach
    void setUp() {
        strategyA = new StubStrategy("TEST_A", "Strategy A");
        strategyB = new StubStrategy("TEST_B", "Strategy B");
        engine = new StrategyEngineImpl(List.of(strategyA, strategyB));
    }

    @Test
    void listStrategies_returnsAllRegisteredStrategies() {
        var definitions = engine.listStrategies();
        assertThat(definitions).hasSize(2);
        assertThat(definitions).extracting("name").containsExactly("TEST_A", "TEST_B");
        assertThat(definitions).extracting("description").containsExactly("Strategy A", "Strategy B");
    }

    @Test
    void evaluate_delegatesToCorrectStrategy() {
        var result = engine.evaluate("TEST_A", ctx());
        assertThat(result.strategyName()).isEqualTo("TEST_A");
    }

    @Test
    void evaluate_isCaseInsensitive() {
        var result = engine.evaluate("test_a", ctx());
        assertThat(result.strategyName()).isEqualTo("TEST_A");
    }

    @Test
    void evaluate_throwsIllegalArgument_forUnknownName() {
        assertThatThrownBy(() -> engine.evaluate("UNKNOWN", ctx()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown strategy: UNKNOWN");
    }

    @Test
    void evaluateAll_returnsResultsFromAllStrategies() {
        var results = engine.evaluateAll(ctx());
        assertThat(results).hasSize(2);
        assertThat(results).extracting("strategyName").containsExactly("TEST_A", "TEST_B");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private StrategyContext ctx() {
        var ind = new TechnicalIndicators("AAPL", BarInterval.D1, Instant.now(),
                null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        return new StrategyContext("AAPL", BarInterval.D1, null, ind);
    }

    private static class StubStrategy implements Strategy {
        private final String name;
        private final String description;

        StubStrategy(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        public String name() { return name; }

        @Override
        public String description() { return description; }

        @Override
        public StrategyResult evaluate(StrategyContext context) {
            return new StrategyResult(name, context.symbol(), Signal.HOLD,
                    BigDecimal.ZERO, "stub", Instant.now());
        }
    }
}
