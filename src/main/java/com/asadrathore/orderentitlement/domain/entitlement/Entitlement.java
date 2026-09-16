package com.asadrathore.orderentitlement.domain.entitlement;

import java.time.Instant;
import java.util.UUID;

/**
 * An entitlement grants a customer the right to use a given product, and records
 * which order caused the grant. Kept deliberately simple here — a real licensing
 * system would also track expiry, seat counts, and revocation, but the point of
 * this sample is the flow that creates entitlements, not the entitlement model itself.
 */
public record Entitlement(
        UUID id,
        String customerId,
        String productCode,
        UUID sourceOrderId,
        Instant grantedAt
) {
    public static Entitlement grant(String customerId, String productCode, UUID sourceOrderId, Instant now) {
        return new Entitlement(UUID.randomUUID(), customerId, productCode, sourceOrderId, now);
    }
}
