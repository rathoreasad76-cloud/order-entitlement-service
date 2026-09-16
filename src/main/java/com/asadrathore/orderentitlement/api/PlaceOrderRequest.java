package com.asadrathore.orderentitlement.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record PlaceOrderRequest(
        @NotBlank String customerId,
        @NotBlank String currency,
        @NotEmpty @Valid List<Line> lines
) {
    public record Line(
            @NotBlank String productCode,
            @Positive int quantity,
            @NotNull @DecimalMin(value = "0.00") BigDecimal unitPrice
    ) {
    }
}
