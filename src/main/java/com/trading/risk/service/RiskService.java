package com.trading.risk.service;

import com.trading.marketdata.domain.BarInterval;
import com.trading.risk.domain.PortfolioRisk;
import com.trading.risk.domain.PositionRequest;
import com.trading.risk.domain.PositionRisk;
import com.trading.risk.domain.RiskMetrics;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface RiskService {

    /**
     * Compute risk metrics for a symbol using historical bars of the given interval.
     */
    RiskMetrics assessSymbol(String symbol, BarInterval interval);

    /**
     * Assess risk for a single position defined by symbol, share count and cost basis.
     */
    PositionRisk assessPosition(String symbol, BigDecimal quantity, BigDecimal entryPrice);

    /**
     * Assess aggregate risk across a set of positions.
     */
    PortfolioRisk assessPortfolio(List<PositionRequest> positions);

    /**
     * Run {@link #assessSymbol} for every symbol in the configured watch-list.
     *
     * @return map of symbol → RiskMetrics; symbols that fail are omitted
     */
    Map<String, RiskMetrics> scanRisk(BarInterval interval);
}
