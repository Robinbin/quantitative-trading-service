package com.trading.marketdata.provider;

import com.trading.marketdata.domain.BarInterval;
import com.trading.marketdata.domain.FundamentalData;
import com.trading.marketdata.domain.OhlcvBar;
import com.trading.marketdata.domain.TickQuote;

import java.util.List;

public interface MarketDataProvider {

    List<OhlcvBar> fetchOhlcv(String symbol, BarInterval interval, int limit);

    TickQuote fetchTick(String symbol);

    FundamentalData fetchFundamental(String symbol);
}
