package com.trading.marketdata.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Quantitative Trading Service API")
                        .description("Market data collection and processing API — " +
                                "provides real-time quotes, OHLCV bars, technical indicators, " +
                                "and fundamental data powered by Yahoo Finance.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Robbin")
                                .email("hansenzhulei@qq.com"))
                        .license(new License()
                                .name("MIT")))
                .servers(List.of(
                        new Server().url("/").description("Current server")
                ));
    }
}
