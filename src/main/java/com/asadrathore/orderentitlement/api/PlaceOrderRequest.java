package com.asadrathore.orderentitlement.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "An order to place, as one or more product lines for a single customer")
public record PlaceOrderRequest(
        @Schema(description = "Customer the order belongs to", example = "customer-1")
        @NotBlank String customerId,

        @Schema(description = "ISO-4217 currency code that every line's unit price is denominated in",
                example = "USD")
        @NotBlank String currency,

        @Schema(description = "Lines to order; at least one is required")
        @NotEmpty @Valid List<Line> lines
) {
    @Schema(description = "A single product line within an order")
    public record Line(
            @Schema(description = "Product being ordered; this is what the resulting entitlement grants access to",
                    example = "PRO_LICENSE")
            @NotBlank String productCode,

            @Schema(description = "How many units of the product to order", example = "1")
            @Positive int quantity,

            @Schema(description = "Price per unit, in the order's currency", example = "99.00")
            @NotNull @DecimalMin(value = "0.00") BigDecimal unitPrice
    ) {
    }
}
