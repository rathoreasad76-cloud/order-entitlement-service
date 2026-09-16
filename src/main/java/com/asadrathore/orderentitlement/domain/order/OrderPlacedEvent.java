package com.asadrathore.orderentitlement.domain.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain event raised when an order is successfully placed. Downstream handlers
 * (e.g. entitlement granting) react to this rather than being called directly by
 * the command handler, which keeps the write model decoupled from what happens next.
 */
public record OrderPlacedEvent(
        UUID orderId,
        String customerId,
        List<String> productCodes,
        Instant occurredAt
) {
}
