package com.trading.marketdata.service;

import com.trading.marketdata.domain.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface MarketDataService {

    List<OhlcvBar> getOhlcv(String symbol, BarInterval interval, int limit);

    TickQuote getTick(String symbol);

    Map<String, TickQuote> getBatchTick(Set<String> symbols);

    TechnicalIndicators getIndicators(String symbol, BarInterval interval);

    FundamentalData getFundamental(String symbol);

    void refresh(String symbol);
}
