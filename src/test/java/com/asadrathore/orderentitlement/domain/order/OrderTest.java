package com.asadrathore.orderentitlement.domain.order;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private final OrderLine oneWidget = new OrderLine("WIDGET", 1, Money.of(new java.math.BigDecimal("10.00"), "USD"));

    @Test
    void placingAnOrderComputesTotalFromItsLines() {
        OrderLine twoWidgets = new OrderLine("WIDGET", 2, Money.of(new java.math.BigDecimal("10.00"), "USD"));
        OrderLine oneGadget = new OrderLine("GADGET", 1, Money.of(new java.math.BigDecimal("5.50"), "USD"));

        Order order = Order.place("customer-1", "idem-key-1", List.of(twoWidgets, oneGadget), Instant.now());

        assertThat(order.total()).isEqualTo(Money.of(new java.math.BigDecimal("25.50"), "USD"));
        assertThat(order.status()).isEqualTo(OrderStatus.PLACED);
    }

    @Test
    void anOrderMustHaveAtLeastOneLine() {
        assertThatThrownBy(() -> Order.place("customer-1", "idem-key-1", List.of(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one line");
    }

    @Test
    void anOrderRequiresAnIdempotencyKey() {
        assertThatThrownBy(() -> Order.place("customer-1", "  ", List.of(oneWidget), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotencyKey");
    }

    @Test
    void aPlacedOrderCanBeCancelled() {
        Order order = Order.place("customer-1", "idem-key-1", List.of(oneWidget), Instant.now());

        order.cancel();

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void aCancelledOrderCannotBeCancelledAgain() {
        Order order = Order.place("customer-1", "idem-key-1", List.of(oneWidget), Instant.now());
        order.cancel();

        assertThatThrownBy(order::cancel)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PLACED");
    }

    @Test
    void placingAnOrderRaisesAnOrderPlacedEventWithTheProductCodesInvolved() {
        OrderLine twoWidgets = new OrderLine("WIDGET", 2, Money.of(new java.math.BigDecimal("10.00"), "USD"));
        Order order = Order.place("customer-1", "idem-key-1", List.of(twoWidgets), Instant.now());

        OrderPlacedEvent event = order.placedEvent();

        assertThat(event.orderId()).isEqualTo(order.id());
        assertThat(event.customerId()).isEqualTo("customer-1");
        assertThat(event.productCodes()).containsExactly("WIDGET");
    }
}
