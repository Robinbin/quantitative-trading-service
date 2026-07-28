package com.trading.marketdata.scheduler;

import com.trading.marketdata.config.MarketDataProperties;
import com.trading.marketdata.service.MarketDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MarketDataRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketDataRefreshScheduler.class);

    private final MarketDataService    service;
    private final MarketDataProperties props;

    public MarketDataRefreshScheduler(MarketDataService service, MarketDataProperties props) {
        this.service = service;
        this.props   = props;
    }

    @Scheduled(fixedDelayString = "#{${trading.market-data.tick-refresh-seconds:30} * 1000L}")
    public void refreshTicks() {
        props.watchList().forEach(symbol -> {
            try {
                service.getTick(symbol);
            } catch (Exception e) {
                log.error("Failed to refresh tick for {}: {}", symbol, e.getMessage());
            }
        });
    }

    @Scheduled(fixedDelayString = "#{${trading.market-data.ohlcv-refresh-minutes:5} * 60000L}")
    public void refreshOhlcvAndIndicators() {
        props.watchList().forEach(symbol -> {
            try {
                service.refresh(symbol);
            } catch (Exception e) {
                log.error("Failed to refresh OHLCV/Indicators for {}: {}", symbol, e.getMessage());
            }
        });
    }

    @Scheduled(cron = "0 0 8 * * MON-FRI")
    public void refreshFundamentals() {
        props.watchList().forEach(symbol -> {
            try {
                service.getFundamental(symbol);
            } catch (Exception e) {
                log.error("Failed to refresh fundamental for {}: {}", symbol, e.getMessage());
            }
        });
    }
}
