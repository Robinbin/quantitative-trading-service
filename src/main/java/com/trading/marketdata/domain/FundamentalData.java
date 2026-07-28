package com.trading.marketdata.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FundamentalData(
        String     symbol,
        String     companyName,
        String     sector,
        String     industry,
        BigDecimal peRatio,
        BigDecimal forwardPE,
        BigDecimal pbRatio,
        BigDecimal psRatio,
        BigDecimal revenueTotal,
        BigDecimal netIncome,
        BigDecimal eps,
        BigDecimal dividendYield,
        BigDecimal beta,
        BigDecimal fiftyTwoWeekHigh,
        BigDecimal fiftyTwoWeekLow,
        LocalDate  fetchedDate
) {}
