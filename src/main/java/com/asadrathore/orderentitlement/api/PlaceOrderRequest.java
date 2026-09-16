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

public record PlaceOrderRequest(
        @Schema(example = "customer-1") @NotBlank String customerId,
        @Schema(description = "ISO 4217 currency code", example = "USD") @NotBlank String currency,
        @NotEmpty @Valid List<Line> lines
) {
    public record Line(
            @Schema(example = "PRO_LICENSE") @NotBlank String productCode,
            @Schema(example = "1") @Positive int quantity,
            @Schema(example = "99.00") @NotNull @DecimalMin(value = "0.00") BigDecimal unitPrice
    ) {
    }
}
