package com.trading.marketdata.service;

import com.trading.marketdata.domain.*;

import java.util.List;

public interface MarketDataService {

    List<OhlcvBar> getOhlcv(String symbol, BarInterval interval, int limit);

    TickQuote getTick(String symbol);

    TechnicalIndicators getIndicators(String symbol, BarInterval interval);

    FundamentalData getFundamental(String symbol);

    void refresh(String symbol);
}
