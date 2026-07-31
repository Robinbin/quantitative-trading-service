package com.trading.marketdata.provider.yahoo;

import com.trading.marketdata.domain.BarInterval;
import com.trading.marketdata.domain.FundamentalData;
import com.trading.marketdata.domain.OhlcvBar;
import com.trading.marketdata.domain.TickQuote;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class YahooFinanceProviderIntegrationTest {

    private MockWebServer mockWebServer;
    private YahooFinanceProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();
        YahooResponseParser parser = new YahooResponseParser();
        provider = new YahooFinanceProvider(WebClient.builder(), parser, baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // ── fetchOhlcv ───────────────────────────────────────────────────────────

    @Test
    void fetchOhlcv_shouldReturnParsedBars() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody(loadFixture("yahoo_ohlcv.json"))
                .addHeader("Content-Type", "application/json"));

        List<OhlcvBar> bars = provider.fetchOhlcv("AAPL", BarInterval.D1, 100);

        assertThat(bars).hasSize(3);
        assertThat(bars.get(0).symbol()).isEqualTo("AAPL");
        assertThat(bars.get(0).interval()).isEqualTo(BarInterval.D1);
    }

    @Test
    void fetchOhlcv_shouldRespectLimitParameter() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody(loadFixture("yahoo_ohlcv.json"))
                .addHeader("Content-Type", "application/json"));

        List<OhlcvBar> bars = provider.fetchOhlcv("AAPL", BarInterval.D1, 2);

        assertThat(bars).hasSize(2);
    }

    @Test
    void fetchOhlcv_shouldSendCorrectRequest() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody(loadFixture("yahoo_ohlcv.json"))
                .addHeader("Content-Type", "application/json"));

        provider.fetchOhlcv("AAPL", BarInterval.D1, 100);

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).contains("/v8/finance/chart/AAPL");
        assertThat(request.getPath()).contains("interval=1d");
        assertThat(request.getHeader("User-Agent")).contains("Mozilla");
    }

    // ── fetchTick ────────────────────────────────────────────────────────────

    @Test
    void fetchTick_shouldReturnParsedQuote() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody(loadFixture("yahoo_tick.json"))
                .addHeader("Content-Type", "application/json"));

        TickQuote tick = provider.fetchTick("AAPL");

        assertThat(tick).isNotNull();
        assertThat(tick.symbol()).isEqualTo("AAPL");
        assertThat(tick.currency()).isEqualTo("USD");
    }

    @Test
    void fetchTick_shouldSendCorrectRequest() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody(loadFixture("yahoo_tick.json"))
                .addHeader("Content-Type", "application/json"));

        provider.fetchTick("AAPL");

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).contains("/v10/finance/quoteSummary/AAPL");
        assertThat(request.getPath()).contains("modules=price");
    }

    // ── fetchFundamental ─────────────────────────────────────────────────────

    @Test
    void fetchFundamental_shouldReturnParsedData() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody(loadFixture("yahoo_fundamental.json"))
                .addHeader("Content-Type", "application/json"));

        FundamentalData fd = provider.fetchFundamental("AAPL");

        assertThat(fd).isNotNull();
        assertThat(fd.symbol()).isEqualTo("AAPL");
        assertThat(fd.companyName()).isEqualTo("Apple Inc.");
    }

    @Test
    void fetchFundamental_shouldSendCorrectRequest() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody(loadFixture("yahoo_fundamental.json"))
                .addHeader("Content-Type", "application/json"));

        provider.fetchFundamental("AAPL");

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).contains("/v10/finance/quoteSummary/AAPL");
        assertThat(request.getPath()).contains("defaultKeyStatistics");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String loadFixture(String name) throws IOException {
        var url = getClass().getClassLoader().getResource("fixtures/" + name);
        assertThat(url).isNotNull();
        return Files.readString(Path.of(url.getPath()));
    }
}
