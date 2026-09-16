package com.asadrathore.orderentitlement.domain.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root for an order. All invariants around an order (it must have at least
 * one line, its total is derived from its lines, its status transitions are constrained)
 * are enforced here rather than scattered across services.
 *
 * <p>The aggregate raises domain events rather than reaching out to other aggregates or
 * infrastructure directly — {@link #placedEvent()} is read once by the application layer
 * after a successful save and handed to whatever publishes it (see the outbox pattern in
 * {@code infrastructure.outbox}).
 */
public class Order {

    private final UUID id;
    private final String customerId;
    private final String idempotencyKey;
    private final List<OrderLine> lines;
    private OrderStatus status;
    private final Instant placedAt;

    private Order(UUID id, String customerId, String idempotencyKey, List<OrderLine> lines,
                  OrderStatus status, Instant placedAt) {
        this.id = id;
        this.customerId = customerId;
        this.idempotencyKey = idempotencyKey;
        this.lines = new ArrayList<>(lines);
        this.status = status;
        this.placedAt = placedAt;
    }

    /**
     * Places a new order. This is the only way to create an Order — there is no
     * public constructor — so every order that exists in the system has passed
     * through these invariant checks.
     */
    public static Order place(String customerId, String idempotencyKey, List<OrderLine> lines, Instant now) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId must not be blank");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("an order must contain at least one line");
        }
        return new Order(UUID.randomUUID(), customerId, idempotencyKey, lines, OrderStatus.PLACED, now);
    }

    /**
     * Reconstructs an existing order from persisted state. Used only by the
     * infrastructure layer when hydrating an aggregate from the database —
     * application code should never call this directly.
     */
    public static Order reconstruct(UUID id, String customerId, String idempotencyKey, List<OrderLine> lines,
                                     OrderStatus status, Instant placedAt) {
        return new Order(id, customerId, idempotencyKey, lines, status, placedAt);
    }

    public Money total() {
        String currency = lines.get(0).unitPrice().currencyCode();
        Money total = Money.zero(currency);
        for (OrderLine line : lines) {
            total = total.add(line.lineTotal());
        }
        return total;
    }

    public OrderPlacedEvent placedEvent() {
        return new OrderPlacedEvent(
                id,
                customerId,
                lines.stream().map(OrderLine::productCode).toList(),
                placedAt
        );
    }

    public void cancel() {
        if (status != OrderStatus.PLACED) {
            throw new IllegalStateException("only a PLACED order can be cancelled, current status is " + status);
        }
        this.status = OrderStatus.CANCELLED;
    }

    public UUID id() {
        return id;
    }

    public String customerId() {
        return customerId;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public List<OrderLine> lines() {
        return Collections.unmodifiableList(lines);
    }

    public OrderStatus status() {
        return status;
    }

    public Instant placedAt() {
        return placedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order order)) return false;
        return id.equals(order.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
