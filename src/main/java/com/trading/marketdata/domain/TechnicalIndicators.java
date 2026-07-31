package com.trading.marketdata.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record TechnicalIndicators(
        String      symbol,
        BarInterval interval,
        Instant     calculatedAt,
        // 简单移动平均
        BigDecimal  ma5,
        BigDecimal  ma10,
        BigDecimal  ma20,
        BigDecimal  ma60,
        // 指数移动平均
        BigDecimal  ema12,
        BigDecimal  ema26,
        // MACD
        BigDecimal  macdLine,
        BigDecimal  signalLine,
        BigDecimal  macdHistogram,
        // RSI(14)
        BigDecimal  rsi14,
        // 布林带 (20, 2)
        BigDecimal  bollingerUpper,
        BigDecimal  bollingerMiddle,
        BigDecimal  bollingerLower
) {}
