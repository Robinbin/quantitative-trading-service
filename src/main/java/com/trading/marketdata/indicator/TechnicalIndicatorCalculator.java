package com.trading.marketdata.indicator;

import com.trading.marketdata.domain.BarInterval;
import com.trading.marketdata.domain.OhlcvBar;
import com.trading.marketdata.domain.TechnicalIndicators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.*;
import org.ta4j.core.indicators.bollinger.*;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

@Component
public class TechnicalIndicatorCalculator {

    private static final Logger log = LoggerFactory.getLogger(TechnicalIndicatorCalculator.class);

    public TechnicalIndicators calculate(List<OhlcvBar> bars, String symbol, BarInterval interval) {
        if (bars == null || bars.size() < 2) {
            log.warn("Not enough bars to calculate indicators for {}: size={}", symbol, bars == null ? 0 : bars.size());
            return emptyIndicators(symbol, interval);
        }

        BarSeries series = toBarSeries(bars, symbol);
        int last = series.getEndIndex();

        ClosePriceIndicator close = new ClosePriceIndicator(series);

        SMAIndicator ma5  = new SMAIndicator(close, 5);
        SMAIndicator ma10 = new SMAIndicator(close, 10);
        SMAIndicator ma20 = new SMAIndicator(close, 20);
        SMAIndicator ma60 = new SMAIndicator(close, 60);

        EMAIndicator ema12 = new EMAIndicator(close, 12);
        EMAIndicator ema26 = new EMAIndicator(close, 26);

        MACDIndicator macd   = new MACDIndicator(close, 12, 26);
        EMAIndicator  signal = new EMAIndicator(macd, 9);

        RSIIndicator rsi14 = new RSIIndicator(close, 14);

        SMAIndicator                 bbBase  = new SMAIndicator(close, 20);
        StandardDeviationIndicator   stdDev  = new StandardDeviationIndicator(close, 20);
        BollingerBandsMiddleIndicator bbMid   = new BollingerBandsMiddleIndicator(bbBase);
        BollingerBandsUpperIndicator  bbUpper = new BollingerBandsUpperIndicator(bbMid, stdDev);
        BollingerBandsLowerIndicator  bbLower = new BollingerBandsLowerIndicator(bbMid, stdDev);

        BigDecimal macdHistogram = safe(macd.getValue(last)) == null || safe(signal.getValue(last)) == null
                ? null
                : safe(macd.getValue(last)).subtract(safe(signal.getValue(last)));

        return new TechnicalIndicators(
                symbol, interval, java.time.Instant.now(),
                safe(ma5.getValue(last)),
                safe(ma10.getValue(last)),
                safe(ma20.getValue(last)),
                safe(ma60.getValue(last)),
                safe(ema12.getValue(last)),
                safe(ema26.getValue(last)),
                safe(macd.getValue(last)),
                safe(signal.getValue(last)),
                macdHistogram,
                safe(rsi14.getValue(last)),
                safe(bbUpper.getValue(last)),
                safe(bbMid.getValue(last)),
                safe(bbLower.getValue(last))
        );
    }

    private BarSeries toBarSeries(List<OhlcvBar> bars, String symbol) {
        BarSeries series = new BaseBarSeries(symbol);
        Duration barDuration = Duration.ofSeconds(bars.get(0).interval().getSeconds());
        for (OhlcvBar b : bars) {
            ZonedDateTime endTime = ZonedDateTime.ofInstant(b.timestamp(), ZoneOffset.UTC)
                    .plus(barDuration);
            Bar bar = BaseBar.builder(DecimalNum::valueOf, Number.class)
                    .timePeriod(barDuration)
                    .endTime(endTime)
                    .openPrice(b.open())
                    .highPrice(b.high())
                    .lowPrice(b.low())
                    .closePrice(b.close())
                    .volume(b.volume())
                    .build();
            try {
                series.addBar(bar);
            } catch (Exception e) {
                // 跳过重复或乱序的 Bar
            }
        }
        return series;
    }

    private BigDecimal safe(Num num) {
        if (num == null || num.isNaN()) return null;
        return new BigDecimal(num.toString());
    }

    private TechnicalIndicators emptyIndicators(String symbol, BarInterval interval) {
        return new TechnicalIndicators(symbol, interval, java.time.Instant.now(),
                null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
