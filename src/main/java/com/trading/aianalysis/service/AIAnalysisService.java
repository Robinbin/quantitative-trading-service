package com.trading.aianalysis.service;

import com.trading.aianalysis.domain.AnalysisResult;
import com.trading.marketdata.domain.BarInterval;

import java.util.Map;

public interface AIAnalysisService {

    /**
     * Analyse a single symbol using GPT-4o and return structured insights.
     *
     * @param symbol   ticker symbol (e.g. "AAPL")
     * @param interval bar interval used for OHLCV and indicator context
     * @return structured analysis result; never throws — returns a fallback HOLD result on error
     */
    AnalysisResult analyzeSymbol(String symbol, BarInterval interval);

    /**
     * Run {@link #analyzeSymbol} for every symbol in the configured watch-list.
     *
     * @return map of symbol → AnalysisResult; failing symbols return a fallback HOLD result
     */
    Map<String, AnalysisResult> scanAnalysis(BarInterval interval);
}
