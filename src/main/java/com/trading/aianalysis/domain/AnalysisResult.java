package com.trading.aianalysis.domain;

import com.trading.marketdata.domain.BarInterval;
import com.trading.strategy.domain.Signal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Structured result from GPT-4o market analysis.
 *
 * @param symbol         ticker symbol analysed
 * @param interval       bar interval used for context
 * @param summary        2–3 sentence AI-generated market overview
 * @param recommendation trading recommendation derived from AI analysis
 * @param confidence     model confidence in the recommendation [0.00, 1.00]
 * @param keyFactors     3–5 bullet points supporting the recommendation
 * @param generatedAt    wall-clock timestamp of generation
 */
public record AnalysisResult(
        String       symbol,
        BarInterval  interval,
        String       summary,
        Signal       recommendation,
        BigDecimal   confidence,
        List<String> keyFactors,
        Instant      generatedAt
) {}
