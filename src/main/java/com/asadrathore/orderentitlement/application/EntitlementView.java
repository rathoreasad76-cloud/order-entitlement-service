package com.asadrathore.orderentitlement.application;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Access a customer holds to a product, granted by an order they placed")
public record EntitlementView(
        @Schema(description = "Server-assigned entitlement id") UUID id,
        @Schema(description = "Product the customer is entitled to", example = "PRO_LICENSE") String productCode,
        @Schema(description = "Order that caused this entitlement to be granted") UUID sourceOrderId,
        @Schema(description = "When the entitlement was granted") Instant grantedAt
) {
}
