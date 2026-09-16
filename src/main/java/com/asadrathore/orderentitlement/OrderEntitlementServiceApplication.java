package com.asadrathore.orderentitlement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
@EnableScheduling
public class OrderEntitlementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderEntitlementServiceApplication.class, args);
    }

    /**
     * Injected everywhere instead of calling Instant.now() directly, so tests can
     * supply a fixed clock and assert on exact timestamps rather than "some time
     * near now".
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
