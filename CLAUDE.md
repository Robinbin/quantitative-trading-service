# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build (compile + test + JaCoCo coverage report)
./gradlew build

# Run the application (requires OPENAI_API_KEY env var)
OPENAI_API_KEY=your-key ./gradlew bootRun

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.trading.marketdata.service.MarketDataServiceImplTest"

# Run a single test method
./gradlew test --tests "com.trading.marketdata.service.MarketDataServiceImplTest.methodName"

# Generate JaCoCo coverage report
./gradlew jacocoTestReport

# Verify coverage threshold (minimum 80%)
./gradlew jacocoTestCoverageVerification
```

## Architecture

Single Gradle module Spring Boot 4.x application. All code lives under `com.trading.marketdata`.

**Data flow:** `MarketDataRefreshScheduler` → `MarketDataServiceImpl` → `MarketDataCacheService` (Caffeine) + `YahooFinanceProvider` (WebClient) + `TechnicalIndicatorCalculator` (ta4j) → `MarketDataController` (REST API)

**Layers:**

| Package | Role |
|---|---|
| `controller` | REST endpoints at `/api/v1/market-data`, input validation, Swagger annotations |
| `service` | Business logic; cache-aside pattern: check cache → call provider on miss → populate cache |
| `cache` | Typed facade over Spring `CacheManager`/Caffeine (get/put/evict per data type) |
| `provider` | `MarketDataProvider` interface; `YahooFinanceProvider` uses `WebClient` with blocking `.block()` calls to Yahoo Finance v8/v10 APIs; `YahooResponseParser` parses responses with Jackson `JsonNode` |
| `indicator` | `TechnicalIndicatorCalculator` wraps ta4j: MA5/10/20/60, EMA12/26, MACD, RSI(14), Bollinger Bands(20,2) |
| `scheduler` | `@Scheduled` cache warming: ticks every 30s, OHLCV+indicators every 5min, fundamentals at 08:00 weekdays |
| `domain` | Immutable Java records: `OhlcvBar`, `TickQuote`, `TechnicalIndicators`, `FundamentalData`; `BarInterval` enum maps to Yahoo Finance URL params |
| `config` | `MarketDataProperties` (`@ConfigurationProperties(prefix = "trading.market-data")`), Caffeine TTL config, OpenAPI/Swagger config |

**Key design decisions:**
- `MarketDataProvider` and `MarketDataService` are interfaces — concrete implementations can be swapped without touching the controller.
- All domain objects are Java 16+ records (immutable, structural equality).
- Scheduler wraps each symbol in try/catch so one failing symbol never blocks others.

## Testing

- JUnit 5 + Mockito + AssertJ
- Unit tests: `@ExtendWith(MockitoExtension.class)` with manual mocks
- Controller tests: `@WebMvcTest` slice with Spring MockMvc
- Provider tests: OkHttp `MockWebServer` for HTTP-level integration without hitting real Yahoo servers
- JSON fixtures for parser/provider replay tests: `src/test/resources/fixtures/`
- Test `application.yml` sets `spring.ai.openai.api-key=test-key` and empty watch-list to prevent external calls

JaCoCo enforces **80% minimum coverage** — run `jacocoTestCoverageVerification` to verify before committing.

## Planned Modules (Not Yet Implemented)

README lists `strategy`, `ai-analysis`, `risk-management`, `trading-executor` as future modules.
