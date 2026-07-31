package com.trading.strategy.service;

import com.trading.marketdata.config.MarketDataProperties;
import com.trading.marketdata.domain.BarInterval;
import com.trading.marketdata.domain.TechnicalIndicators;
import com.trading.marketdata.domain.TickQuote;
import com.trading.marketdata.service.MarketDataService;
import com.trading.strategy.domain.StrategyContext;
import com.trading.strategy.domain.StrategyDefinition;
import com.trading.strategy.domain.StrategyResult;
import com.trading.strategy.engine.StrategyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class StrategyServiceImpl implements StrategyService {

    private static final Logger log = LoggerFactory.getLogger(StrategyServiceImpl.class);

    private final MarketDataService   marketDataService;
    private final StrategyEngine      engine;
    private final MarketDataProperties props;

    public StrategyServiceImpl(MarketDataService marketDataService,
                               StrategyEngine engine,
                               MarketDataProperties props) {
        this.marketDataService = marketDataService;
        this.engine            = engine;
        this.props             = props;
    }

    @Override
    public List<StrategyDefinition> listStrategies() {
        return engine.listStrategies();
    }

    @Override
    public StrategyResult evaluate(String symbol, String strategyName, BarInterval interval) {
        StrategyContext ctx = buildContext(symbol, interval);
        return engine.evaluate(strategyName, ctx);
    }

    @Override
    public List<StrategyResult> evaluateAll(String symbol, BarInterval interval) {
        StrategyContext ctx = buildContext(symbol, interval);
        return engine.evaluateAll(ctx);
    }

    @Override
    public Map<String, List<StrategyResult>> scan(BarInterval interval) {
        return props.watchList().parallelStream()
                .collect(Collectors.toMap(
                        symbol -> symbol,
                        symbol -> {
                            try {
                                return evaluateAll(symbol, interval);
                            } catch (Exception e) {
                                log.warn("Strategy scan failed for symbol {}: {}", symbol, e.getMessage());
                                return List.of();
                            }
                        },
                        (a, b) -> a,
                        TreeMap::new
                ));
    }

    private StrategyContext buildContext(String symbol, BarInterval interval) {
        TickQuote           tick       = marketDataService.getTick(symbol);
        TechnicalIndicators indicators = marketDataService.getIndicators(symbol, interval);
        return new StrategyContext(symbol, interval, tick, indicators);
    }
}
