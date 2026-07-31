package com.trading.marketdata.provider.yahoo;

import com.trading.marketdata.domain.BarInterval;
import com.trading.marketdata.domain.FundamentalData;
import com.trading.marketdata.domain.OhlcvBar;
import com.trading.marketdata.domain.TickQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class YahooResponseParserTest {

    private YahooResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new YahooResponseParser();
    }

    // ── parseOhlcv ──────────────────────────────────────────────────────────

    @Test
    void parseOhlcv_shouldReturnCorrectBars() throws IOException {
        String json = loadFixture("yahoo_ohlcv.json");

        List<OhlcvBar> bars = parser.parseOhlcv(json, "AAPL", BarInterval.D1);

        assertThat(bars).hasSize(3);
        OhlcvBar last = bars.get(2);
        assertThat(last.symbol()).isEqualTo("AAPL");
        assertThat(last.interval()).isEqualTo(BarInterval.D1);
        assertThat(last.open()).isCloseTo(new BigDecimal("182.0"), within(new BigDecimal("0.01")));
        assertThat(last.close()).isCloseTo(new BigDecimal("183.0"), within(new BigDecimal("0.01")));
        assertThat(last.volume()).isEqualTo(60_000_000L);
    }

    @Test
    void parseOhlcv_shouldReturnEmptyListForMalformedJson() {
        List<OhlcvBar> bars = parser.parseOhlcv("not-valid-json", "AAPL", BarInterval.D1);
        assertThat(bars).isEmpty();
    }

    @Test
    void parseOhlcv_shouldReturnEmptyListWhenResultMissing() {
        String json = "{\"chart\":{\"result\":null,\"error\":null}}";
        List<OhlcvBar> bars = parser.parseOhlcv(json, "AAPL", BarInterval.D1);
        assertThat(bars).isEmpty();
    }

    @Test
    void parseOhlcv_shouldSkipBarsWithNullClose() throws IOException {
        String json = """
                {"chart":{"result":[{"timestamp":[1700000000,1700086400],
                "indicators":{"quote":[{"open":[180.0,181.0],"high":[183.0,184.0],
                "low":[179.0,180.0],"close":[null,182.0],"volume":[1000,2000]}]}}]}}
                """;
        List<OhlcvBar> bars = parser.parseOhlcv(json, "AAPL", BarInterval.D1);
        assertThat(bars).hasSize(1);
        assertThat(bars.get(0).close()).isCloseTo(new BigDecimal("182.0"), within(new BigDecimal("0.01")));
    }

    // ── parseTick ───────────────────────────────────────────────────────────

    @Test
    void parseTick_shouldReturnCorrectQuote() throws IOException {
        String json = loadFixture("yahoo_tick.json");

        TickQuote tick = parser.parseTick(json, "AAPL");

        assertThat(tick).isNotNull();
        assertThat(tick.symbol()).isEqualTo("AAPL");
        assertThat(tick.price()).isCloseTo(new BigDecimal("183.0"), within(new BigDecimal("0.01")));
        assertThat(tick.change()).isCloseTo(new BigDecimal("2.0"), within(new BigDecimal("0.01")));
        assertThat(tick.volume()).isEqualTo(50_000_000L);
        assertThat(tick.currency()).isEqualTo("USD");
        assertThat(tick.exchangeName()).isEqualTo("NMS");
    }

    @Test
    void parseTick_shouldReturnNullForMalformedJson() {
        TickQuote tick = parser.parseTick("bad-json", "AAPL");
        assertThat(tick).isNull();
    }

    // ── parseFundamental ────────────────────────────────────────────────────

    @Test
    void parseFundamental_shouldReturnCorrectData() throws IOException {
        String json = loadFixture("yahoo_fundamental.json");

        FundamentalData fd = parser.parseFundamental(json, "AAPL");

        assertThat(fd).isNotNull();
        assertThat(fd.symbol()).isEqualTo("AAPL");
        assertThat(fd.companyName()).isEqualTo("Apple Inc.");
        assertThat(fd.sector()).isEqualTo("Technology");
        assertThat(fd.peRatio()).isCloseTo(new BigDecimal("28.5"), within(new BigDecimal("0.01")));
        assertThat(fd.beta()).isCloseTo(new BigDecimal("1.26"), within(new BigDecimal("0.01")));
        assertThat(fd.fetchedDate()).isNotNull();
    }

    @Test
    void parseFundamental_shouldReturnNullForMalformedJson() {
        FundamentalData fd = parser.parseFundamental("bad-json", "AAPL");
        assertThat(fd).isNull();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private String loadFixture(String name) throws IOException {
        var url = getClass().getClassLoader().getResource("fixtures/" + name);
        assertThat(url).isNotNull();
        return Files.readString(Path.of(url.getPath()));
    }
}
