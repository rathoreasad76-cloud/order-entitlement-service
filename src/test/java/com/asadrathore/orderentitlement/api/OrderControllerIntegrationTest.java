package com.asadrathore.orderentitlement.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test spinning up a real Postgres via Testcontainers, exercising the
 * full flow: place an order over HTTP, confirm it's idempotent on retry, and confirm
 * the asynchronous entitlement grant (triggered by the AFTER_COMMIT event listener)
 * eventually shows up.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class OrderControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("order_entitlement")
            .withUsername("order_entitlement")
            .withPassword("order_entitlement");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void placingAnOrderReturns201AndTheOrderCanThenBeFetched() throws Exception {
        Map<String, Object> body = Map.of(
                "customerId", "customer-42",
                "currency", "USD",
                "lines", List.of(Map.of("productCode", "WIDGET", "quantity", 2, "unitPrice", new BigDecimal("10.00")))
        );

        String orderJson = mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "test-key-1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.customerId", is("customer-42")))
                .andExpect(jsonPath("$.status", is("PLACED")))
                .andReturn().getResponse().getContentAsString();

        String orderId = objectMapper.readTree(orderJson).get("id").asText();

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(20.00)));
    }

    @Test
    void repeatingTheSameIdempotencyKeyReturnsTheSameOrderInstead0fCreatingADuplicate() throws Exception {
        Map<String, Object> body = Map.of(
                "customerId", "customer-43",
                "currency", "USD",
                "lines", List.of(Map.of("productCode", "GADGET", "quantity", 1, "unitPrice", new BigDecimal("5.00")))
        );
        String json = objectMapper.writeValueAsString(body);

        String firstResponse = mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "duplicate-key")
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String secondResponse = mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "duplicate-key")
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String firstId = objectMapper.readTree(firstResponse).get("id").asText();
        String secondId = objectMapper.readTree(secondResponse).get("id").asText();

        org.assertj.core.api.Assertions.assertThat(secondId).isEqualTo(firstId);
    }

    @Test
    void placingAnOrderEventuallyGrantsAnEntitlementForEachProductPurchased() throws Exception {
        Map<String, Object> body = Map.of(
                "customerId", "customer-99",
                "currency", "USD",
                "lines", List.of(Map.of("productCode", "PRO_LICENSE", "quantity", 1, "unitPrice", new BigDecimal("99.00")))
        );

        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "entitlement-key-1")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        // The entitlement grant happens in an AFTER_COMMIT listener, so it isn't
        // guaranteed to have run by the time the HTTP response comes back — hence
        // polling rather than asserting immediately.
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                mockMvc.perform(get("/api/customers/{customerId}/entitlements", "customer-99"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[0].productCode", is("PRO_LICENSE")))
        );
    }
}
