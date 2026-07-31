package com.trading.strategy.service;

import com.trading.marketdata.domain.BarInterval;
import com.trading.strategy.domain.StrategyDefinition;
import com.trading.strategy.domain.StrategyResult;

import java.util.List;
import java.util.Map;

public interface StrategyService {

    List<StrategyDefinition> listStrategies();

    StrategyResult evaluate(String symbol, String strategyName, BarInterval interval);

    List<StrategyResult> evaluateAll(String symbol, BarInterval interval);

    /** Returns a map of symbol → List<StrategyResult> for all watch-list symbols. */
    Map<String, List<StrategyResult>> scan(BarInterval interval);
}
