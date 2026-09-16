package com.asadrathore.orderentitlement.domain.order;

import java.util.Objects;

/**
 * A single line item within an order. Value object — two lines are equal if their
 * content is equal, regardless of identity.
 */
public final class OrderLine {

    private final String productCode;
    private final int quantity;
    private final Money unitPrice;

    public OrderLine(String productCode, int quantity, Money unitPrice) {
        if (productCode == null || productCode.isBlank()) {
            throw new IllegalArgumentException("productCode must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.productCode = productCode;
        this.quantity = quantity;
        this.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice must not be null");
    }

    public Money lineTotal() {
        return unitPrice.multiply(quantity);
    }

    public String productCode() {
        return productCode;
    }

    public int quantity() {
        return quantity;
    }

    public Money unitPrice() {
        return unitPrice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderLine orderLine)) return false;
        return quantity == orderLine.quantity
                && productCode.equals(orderLine.productCode)
                && unitPrice.equals(orderLine.unitPrice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productCode, quantity, unitPrice);
    }
}
