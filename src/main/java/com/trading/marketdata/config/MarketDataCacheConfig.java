package com.trading.marketdata.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties(MarketDataProperties.class)
public class MarketDataCacheConfig {

    public static final String CACHE_OHLCV       = "ohlcv";
    public static final String CACHE_TICK        = "tick";
    public static final String CACHE_INDICATORS  = "indicators";
    public static final String CACHE_FUNDAMENTAL = "fundamental";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheNames(List.of(CACHE_OHLCV, CACHE_TICK, CACHE_INDICATORS, CACHE_FUNDAMENTAL));

        manager.registerCustomCache(CACHE_TICK,
                Caffeine.newBuilder()
                        .expireAfterWrite(15, TimeUnit.SECONDS)
                        .maximumSize(1000)
                        .recordStats()
                        .build());
        manager.registerCustomCache(CACHE_OHLCV,
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.MINUTES)
                        .maximumSize(500)
                        .recordStats()
                        .build());
        manager.registerCustomCache(CACHE_INDICATORS,
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.MINUTES)
                        .maximumSize(500)
                        .recordStats()
                        .build());
        manager.registerCustomCache(CACHE_FUNDAMENTAL,
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .maximumSize(200)
                        .recordStats()
                        .build());
        return manager;
    }
}
