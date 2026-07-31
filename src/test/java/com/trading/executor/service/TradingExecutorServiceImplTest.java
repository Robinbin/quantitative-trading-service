package com.trading.executor.service;

import com.trading.executor.config.TradingExecutorProperties;
import com.trading.executor.domain.Order;
import com.trading.executor.domain.OrderRequest;
import com.trading.executor.domain.OrderSide;
import com.trading.executor.domain.OrderStatus;
import com.trading.executor.domain.OrderType;
import com.trading.executor.domain.Position;
import com.trading.executor.domain.TradingPortfolio;
import com.trading.marketdata.config.MarketDataProperties;
import com.trading.marketdata.domain.BarInterval;
import com.trading.marketdata.domain.TickQuote;
import com.trading.marketdata.service.MarketDataService;
import com.trading.risk.domain.RiskLevel;
import com.trading.risk.domain.RiskMetrics;
import com.trading.risk.service.RiskService;
import com.trading.strategy.domain.Signal;
import com.trading.strategy.domain.StrategyResult;
import com.trading.strategy.service.StrategyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradingExecutorServiceImplTest {

    @Mock MarketDataService    marketDataService;
    @Mock StrategyService      strategyService;
    @Mock RiskService          riskService;
    @Mock MarketDataProperties marketDataProps;

    private TradingExecutorServiceImpl service;

    @BeforeEach
    void setUp() {
        TradingExecutorProperties props = new TradingExecutorProperties(
                100_000.0,   // initialCash
                10_000.0,    // maxOrderValue
                3,           // minStrategiesAgree
                0.60         // minConfidence
        );
        service = new TradingExecutorServiceImpl(
                marketDataService, strategyService, riskService, props, marketDataProps);
    }

    // ------------------------------------------------------------------
    // submitOrder — market BUY
    // ------------------------------------------------------------------

    @Test
    void marketBuy_fillsImmediately_deductsCash() {
        stubTick("AAPL", 150.0);

        Order order = service.submitOrder(new OrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET,
                BigDecimal.valueOf(10), null));

        assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.filledPrice()).isEqualByComparingTo(BigDecimal.valueOf(150));
        assertThat(order.symbol()).isEqualTo("AAPL");

        // Position should exist
        List<Position> positions = service.listPositions();
        assertThat(positions).hasSize(1);
        assertThat(positions.get(0).symbol()).isEqualTo("AAPL");
        assertThat(positions.get(0).quantity()).isEqualByComparingTo(BigDecimal.valueOf(10));
    }

    @Test
    void marketBuy_rejectsWhenInsufficientCash() {
        stubTick("AAPL", 15_000.0); // 15_000 × 10 = 150_000 > 100_000 cash

        Order order = service.submitOrder(new OrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET,
                BigDecimal.valueOf(10), null));

        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.rejectionReason()).contains("Insufficient cash");
    }

    // ------------------------------------------------------------------
    // submitOrder — market SELL
    // ------------------------------------------------------------------

    @Test
    void marketSell_rejectsWhenNoPosition() {
        stubTick("AAPL", 150.0);

        Order order = service.submitOrder(new OrderRequest("AAPL", OrderSide.SELL, OrderType.MARKET,
                BigDecimal.valueOf(5), null));

        assertThat(order.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.rejectionReason()).contains("Insufficient shares");
    }

    @Test
    void fullSell_clearsPosition() {
        stubTick("AAPL", 150.0);

        service.submitOrder(new OrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET,
                BigDecimal.valueOf(10), null));
        Order sell = service.submitOrder(new OrderRequest("AAPL", OrderSide.SELL, OrderType.MARKET,
                BigDecimal.valueOf(10), null));

        assertThat(sell.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(service.listPositions()).isEmpty();
    }

    @Test
    void partialSell_reducesQuantity() {
        stubTick("AAPL", 100.0);

        service.submitOrder(new OrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET,
                BigDecimal.valueOf(10), null));
        service.submitOrder(new OrderRequest("AAPL", OrderSide.SELL, OrderType.MARKET,
                BigDecimal.valueOf(4), null));

        Position pos = service.listPositions().get(0);
        assertThat(pos.quantity()).isEqualByComparingTo(BigDecimal.valueOf(6));
    }

    // ------------------------------------------------------------------
    // Weighted average cost
    // ------------------------------------------------------------------

    @Test
    void weightedAverageCost_updatedCorrectly_onSecondBuy() {
        // First buy: 10 shares @ 100 → avg=100
        // Second buy: 10 shares @ 200 → avg=(10×100 + 10×200)/20 = 150
        stubTick("AAPL", 100.0);
        service.submitOrder(new OrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET,
                BigDecimal.valueOf(10), null));

        stubTick("AAPL", 200.0);
        service.submitOrder(new OrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET,
                BigDecimal.valueOf(10), null));

        Position pos = service.listPositions().get(0);
        assertThat(pos.averageCost().doubleValue()).isCloseTo(150.0, org.assertj.core.api.Assertions.within(0.01));
        assertThat(pos.quantity()).isEqualByComparingTo(BigDecimal.valueOf(20));
    }

    // ------------------------------------------------------------------
    // LIMIT orders
    // ------------------------------------------------------------------

    @Test
    void limitBuy_fillsWhenPriceAtOrBelowLimit() {
        stubTick("AAPL", 90.0); // market 90, limit 100 → fill

        Order order = service.submitOrder(new OrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT,
                BigDecimal.valueOf(5), BigDecimal.valueOf(100)));

        assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void limitBuy_pendingWhenPriceAboveLimit() {
        stubTick("AAPL", 110.0); // market 110, limit 100 → pending

        Order order = service.submitOrder(new OrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT,
                BigDecimal.valueOf(5), BigDecimal.valueOf(100)));

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void limitSell_fillsWhenPriceAtOrAboveLimit() {
        // First buy to get a position
        stubTick("AAPL", 100.0);
        service.submitOrder(new OrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET,
                BigDecimal.valueOf(5), null));

        stubTick("AAPL", 120.0); // market 120, limit 110 → fill
        Order order = service.submitOrder(new OrderRequest("AAPL", OrderSide.SELL, OrderType.LIMIT,
                BigDecimal.valueOf(5), BigDecimal.valueOf(110)));

        assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void limitSell_pendingWhenPriceBelowLimit() {
        stubTick("AAPL", 100.0);
        service.submitOrder(new OrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET,
                BigDecimal.valueOf(5), null));

        stubTick("AAPL", 90.0); // market 90, limit 110 → pending
        Order order = service.submitOrder(new OrderRequest("AAPL", OrderSide.SELL, OrderType.LIMIT,
                BigDecimal.valueOf(5), BigDecimal.valueOf(110)));

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void limitOrderWithoutLimitPrice_throwsIAE() {
        stubTick("AAPL", 100.0);

        assertThatThrownBy(() ->
                service.submitOrder(new OrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT,
                        BigDecimal.valueOf(5), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limitPrice");
    }

    // ------------------------------------------------------------------
    // cancelOrder
    // ------------------------------------------------------------------

    @Test
    void cancelPendingOrder_returnsCANCELLED() {
        stubTick("AAPL", 200.0); // price > limit → PENDING

        Order pending = service.submitOrder(new OrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT,
                BigDecimal.valueOf(1), BigDecimal.valueOf(100)));
        assertThat(pending.status()).isEqualTo(OrderStatus.PENDING);

        Order cancelled = service.cancelOrder(pending.orderId());
        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelFilledOrder_throwsIAE() {
        stubTick("AAPL", 100.0);
        Order filled = service.submitOrder(new OrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET,
                BigDecimal.valueOf(1), null));

        assertThatThrownBy(() -> service.cancelOrder(filled.orderId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancelUnknownOrder_throwsIAE() {
        assertThatThrownBy(() -> service.cancelOrder("non-existent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ------------------------------------------------------------------
    // listOrders / getOrder
    // ------------------------------------------------------------------

    @Test
    void listOrders_returnsAllOrders() {
        stubTick("AAPL", 100.0);
        service.submitOrder(new OrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, BigDecimal.ONE, null));
        service.submitOrder(new OrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, BigDecimal.ONE, null));

        assertThat(service.listOrders()).hasSize(2);
    }

    @Test
    void getOrder_found_returnsOrder() {
        stubTick("AAPL", 100.0);
        Order submitted = service.submitOrder(
                new OrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, BigDecimal.ONE, null));

        assertThat(service.getOrder(submitted.orderId()).orderId()).isEqualTo(submitted.orderId());
    }

    @Test
    void getOrder_notFound_throwsIAE() {
        assertThatThrownBy(() -> service.getOrder("missing"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------
    // listPositions / getPortfolio
    // ------------------------------------------------------------------

    @Test
    void listPositions_enrichesWithLivePrice() {
        stubTick("AAPL", 100.0);
        service.submitOrder(new OrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET,
                BigDecimal.valueOf(5), null));

        stubTick("AAPL", 120.0); // price has moved
        Position pos = service.listPositions().get(0);

        assertThat(pos.currentPrice()).isEqualByComparingTo(BigDecimal.valueOf(120));
        assertThat(pos.unrealizedPnl().doubleValue()).isCloseTo(100.0, org.assertj.core.api.Assertions.within(0.01)); // (120-100)×5
    }

    @Test
    void getPortfolio_totalValueEqualsCashPlusMarketValue() {
        stubTick("AAPL", 100.0);
        service.submitOrder(new OrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET,
                BigDecimal.valueOf(10), null)); // cost = 1000, cash = 99000

        TradingPortfolio portfolio = service.getPortfolio();
        // totalValue = cashBalance + marketValue = 99000 + 10×100 = 100000
        assertThat(portfolio.totalValue().doubleValue()).isCloseTo(100_000.0, org.assertj.core.api.Assertions.within(1.0));
    }

    // ------------------------------------------------------------------
    // autoExecute
    // ------------------------------------------------------------------

    @Test
    void autoExecute_buysOnConsensus() {
        when(marketDataProps.watchList()).thenReturn(List.of("AAPL"));
        stubTick("AAPL", 100.0);
        when(strategyService.scan(any())).thenReturn(Map.of("AAPL", threeBuySignals()));
        when(riskService.assessSymbol(anyString(), any())).thenReturn(metrics(RiskLevel.LOW));

        List<Order> executed = service.autoExecute(BarInterval.D1);

        assertThat(executed).hasSize(1);
        assertThat(executed.get(0).side()).isEqualTo(OrderSide.BUY);
    }

    @Test
    void autoExecute_skipsCriticalRisk() {
        when(marketDataProps.watchList()).thenReturn(List.of("AAPL"));
        // tick not stubbed — getTick is only called after risk check passes
        when(strategyService.scan(any())).thenReturn(Map.of("AAPL", threeBuySignals()));
        when(riskService.assessSymbol(anyString(), any())).thenReturn(metrics(RiskLevel.CRITICAL));

        List<Order> executed = service.autoExecute(BarInterval.D1);

        assertThat(executed).isEmpty();
    }

    @Test
    void autoExecute_skipsWhenBelowMinStrategyCount() {
        when(marketDataProps.watchList()).thenReturn(List.of("AAPL"));
        // tick not stubbed — getTick is only called when consensus threshold is met
        // Only 2 BUY signals, minStrategiesAgree=3
        when(strategyService.scan(any())).thenReturn(Map.of("AAPL", twoBuySignals()));

        List<Order> executed = service.autoExecute(BarInterval.D1);

        assertThat(executed).isEmpty();
    }

    @Test
    void autoExecute_sellsExistingPosition() {
        when(marketDataProps.watchList()).thenReturn(List.of("AAPL"));
        stubTick("AAPL", 100.0);

        // First, get a position
        service.submitOrder(new OrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET,
                BigDecimal.valueOf(10), null));

        when(strategyService.scan(any())).thenReturn(Map.of("AAPL", threeSellSignals()));

        List<Order> executed = service.autoExecute(BarInterval.D1);

        assertThat(executed).hasSize(1);
        assertThat(executed.get(0).side()).isEqualTo(OrderSide.SELL);
    }

    @Test
    void autoExecute_skipsAlreadyHeldPosition_onBuy() {
        when(marketDataProps.watchList()).thenReturn(List.of("AAPL"));
        stubTick("AAPL", 100.0);

        // Acquire a position first
        service.submitOrder(new OrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET,
                BigDecimal.valueOf(5), null));

        when(strategyService.scan(any())).thenReturn(Map.of("AAPL", threeBuySignals()));
        when(riskService.assessSymbol(anyString(), any())).thenReturn(metrics(RiskLevel.LOW));

        List<Order> executed = service.autoExecute(BarInterval.D1);

        // No new buy because position already exists
        assertThat(executed).isEmpty();
    }

    @Test
    void autoExecute_continuesOnSymbolError() {
        when(marketDataProps.watchList()).thenReturn(List.of("BAD", "AAPL"));
        stubTick("AAPL", 100.0);
        // BAD has BUY signals so processing reaches getTick — which then throws
        when(marketDataService.getTick("BAD")).thenThrow(new RuntimeException("network error"));
        when(strategyService.scan(any())).thenReturn(
                Map.of("BAD", threeBuySignals(), "AAPL", threeBuySignals()));
        when(riskService.assessSymbol(anyString(), any())).thenReturn(metrics(RiskLevel.LOW));

        // Should not throw; BAD is skipped, AAPL is processed
        List<Order> executed = service.autoExecute(BarInterval.D1);
        assertThat(executed).hasSize(1);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void stubTick(String symbol, double price) {
        when(marketDataService.getTick(symbol)).thenReturn(
                new TickQuote(symbol, Instant.now(), BigDecimal.valueOf(price),
                        null, null, null, null, null, null, 0L, null, "USD", "NMS"));
    }

    private RiskMetrics metrics(RiskLevel level) {
        return new RiskMetrics("AAPL", BarInterval.D1,
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(0.20),
                BigDecimal.valueOf(1.0),
                BigDecimal.valueOf(1.0),
                BigDecimal.valueOf(0.10),
                BigDecimal.valueOf(3.0),
                level,
                Instant.now());
    }

    private List<StrategyResult> threeBuySignals() {
        return List.of(
                result(Signal.BUY, 0.80),
                result(Signal.BUY, 0.75),
                result(Signal.BUY, 0.70));
    }

    private List<StrategyResult> twoBuySignals() {
        return List.of(
                result(Signal.BUY, 0.80),
                result(Signal.BUY, 0.75));
    }

    private List<StrategyResult> threeSellSignals() {
        return List.of(
                result(Signal.SELL, 0.80),
                result(Signal.SELL, 0.75),
                result(Signal.SELL, 0.70));
    }

    private StrategyResult result(Signal signal, double confidence) {
        return new StrategyResult("TestStrategy", "AAPL", signal,
                BigDecimal.valueOf(confidence), "reason", Instant.now());
    }
}
