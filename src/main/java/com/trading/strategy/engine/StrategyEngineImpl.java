package com.trading.strategy.engine;

import com.trading.strategy.domain.StrategyContext;
import com.trading.strategy.domain.StrategyDefinition;
import com.trading.strategy.domain.StrategyResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class StrategyEngineImpl implements StrategyEngine {

    private final Map<String, Strategy> strategies;

    public StrategyEngineImpl(List<Strategy> strategyList) {
        strategies = new LinkedHashMap<>();
        for (Strategy s : strategyList) {
            strategies.put(s.name().toLowerCase(), s);
        }
    }

    @Override
    public List<StrategyDefinition> listStrategies() {
        return strategies.values().stream()
                .map(s -> new StrategyDefinition(s.name(), s.description()))
                .toList();
    }

    @Override
    public StrategyResult evaluate(String strategyName, StrategyContext context) {
        Strategy strategy = strategies.get(strategyName.toLowerCase());
        if (strategy == null) {
            throw new IllegalArgumentException("Unknown strategy: " + strategyName);
        }
        return strategy.evaluate(context);
    }

    @Override
    public List<StrategyResult> evaluateAll(StrategyContext context) {
        return strategies.values().stream()
                .map(s -> s.evaluate(context))
                .toList();
    }
}
