package com.trading.marketdata.domain;

public enum BarInterval {

    M1("1m",  "1d",  60L),
    M5("5m",  "5d",  300L),
    H1("1h",  "1mo", 3600L),
    D1("1d",  "1y",  86400L);

    private final String yahooInterval;
    private final String yahooRange;
    private final long   seconds;

    BarInterval(String yahooInterval, String yahooRange, long seconds) {
        this.yahooInterval = yahooInterval;
        this.yahooRange    = yahooRange;
        this.seconds       = seconds;
    }

    public String getYahooInterval() { return yahooInterval; }
    public String getYahooRange()    { return yahooRange; }
    public long   getSeconds()       { return seconds; }
}
