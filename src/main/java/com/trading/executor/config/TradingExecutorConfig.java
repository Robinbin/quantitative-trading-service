package com.trading.executor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TradingExecutorProperties.class)
public class TradingExecutorConfig {}
