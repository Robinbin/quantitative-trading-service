package com.trading.marketdata.service;

import com.trading.marketdata.domain.*;

import java.util.List;
import java.util.Map;

public interface MarketDataService {

    List<OhlcvBar> getOhlcv(String symbol, BarInterval interval, int limit);

    TickQuote getTick(String symbol);

    Map<String, TickQuote> getBatchTick(List<String> symbols);

    TechnicalIndicators getIndicators(String symbol, BarInterval interval);

    FundamentalData getFundamental(String symbol);

    void refresh(String symbol);
}
