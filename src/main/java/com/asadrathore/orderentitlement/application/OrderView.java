package com.asadrathore.orderentitlement.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read model for an order — the output side of CQRS. Shaped for what API consumers
 * actually need to display, rather than mirroring the write-side aggregate structure.
 */
public record OrderView(
        UUID id,
        String customerId,
        String status,
        BigDecimal total,
        String currency,
        Instant placedAt,
        List<LineView> lines
) {
    public record LineView(String productCode, int quantity, BigDecimal unitPrice) {
    }
}
