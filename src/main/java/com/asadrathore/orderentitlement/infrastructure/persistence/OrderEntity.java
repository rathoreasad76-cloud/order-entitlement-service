package com.asadrathore.orderentitlement.infrastructure.persistence;

import com.asadrathore.orderentitlement.domain.order.Order;
import com.asadrathore.orderentitlement.domain.order.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA record for an order. Deliberately kept separate from the {@code Order} domain
 * aggregate — this class knows about tables and columns, the domain class knows about
 * business rules, and neither has to compromise its shape to accommodate the other.
 */
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private Instant placedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLineEntity> lines = new ArrayList<>();

    protected OrderEntity() {
        // required by JPA
    }

    public static OrderEntity fromDomain(Order order) {
        OrderEntity entity = new OrderEntity();
        entity.id = order.id();
        entity.customerId = order.customerId();
        entity.idempotencyKey = order.idempotencyKey();
        entity.currency = order.total().currencyCode();
        entity.status = order.status();
        entity.placedAt = order.placedAt();
        entity.lines = order.lines().stream()
                .map(line -> OrderLineEntity.fromDomain(line, entity))
                .toList();
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getCurrency() {
        return currency;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public List<OrderLineEntity> getLines() {
        return lines;
    }
}
