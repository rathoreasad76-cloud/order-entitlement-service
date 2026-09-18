package com.asadrathore.orderentitlement.application;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read model for an order — the output side of CQRS. Shaped for what API consumers
 * actually need to display, rather than mirroring the write-side aggregate structure.
 */
@Schema(description = "An order as returned to API consumers")
public record OrderView(
        @Schema(description = "Server-assigned order id") UUID id,
        @Schema(description = "Customer the order belongs to", example = "customer-1") String customerId,
        @Schema(description = "Current order status", example = "PLACED") String status,
        @Schema(description = "Sum of every line, quantity included", example = "99.00") BigDecimal total,
        @Schema(description = "Currency the total is denominated in", example = "USD") String currency,
        @Schema(description = "When the order was placed") Instant placedAt,
        @Schema(description = "The ordered lines") List<LineView> lines
) {
    @Schema(description = "A single product line of an order")
    public record LineView(
            @Schema(example = "PRO_LICENSE") String productCode,
            @Schema(example = "1") int quantity,
            @Schema(example = "99.00") BigDecimal unitPrice) {
    }
}
