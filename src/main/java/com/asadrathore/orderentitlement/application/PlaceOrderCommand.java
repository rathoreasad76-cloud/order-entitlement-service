package com.asadrathore.orderentitlement.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Command DTO for placing an order — the input side of CQRS. This is intentionally
 * separate from both the domain model ({@code Order}) and the read model
 * ({@code OrderView}); the three are allowed to evolve independently.
 */
public record PlaceOrderCommand(
        String customerId,
        String idempotencyKey,
        String currency,
        List<Line> lines
) {
    public record Line(String productCode, int quantity, BigDecimal unitPrice) {
    }
}
