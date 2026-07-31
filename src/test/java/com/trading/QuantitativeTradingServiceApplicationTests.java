package com.trading;

import com.trading.marketdata.provider.MarketDataProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class QuantitativeTradingServiceApplicationTests {

    @MockitoBean
    MarketDataProvider marketDataProvider;

    @Test
    void contextLoads() {
    }
}
