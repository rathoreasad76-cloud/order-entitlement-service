package com.asadrathore.orderentitlement.application;

import java.time.Instant;
import java.util.UUID;

public record EntitlementView(
        UUID id,
        String productCode,
        UUID sourceOrderId,
        Instant grantedAt
) {
}
