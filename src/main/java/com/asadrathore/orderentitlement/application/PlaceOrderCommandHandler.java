package com.asadrathore.orderentitlement.application;

import com.asadrathore.orderentitlement.domain.order.Money;
import com.asadrathore.orderentitlement.domain.order.Order;
import com.asadrathore.orderentitlement.domain.order.OrderLine;
import com.asadrathore.orderentitlement.infrastructure.persistence.OrderEntity;
import com.asadrathore.orderentitlement.infrastructure.persistence.OrderJpaRepository;
import com.asadrathore.orderentitlement.infrastructure.persistence.OrderLineEntity;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles {@link PlaceOrderCommand}. Two things worth calling out:
 *
 * <ol>
 *   <li><b>Idempotency</b> — if a request with the same {@code idempotencyKey} has already
 *       been processed, we return the existing order instead of creating a duplicate. This
 *       matters because clients (and API gateways, and retried HTTP calls) will sometimes
 *       send the same "place order" request twice.</li>
 *   <li><b>Event publication happens inside the same transaction</b> as the save, via
 *       {@link ApplicationEventPublisher}. The actual side effects (granting an entitlement,
 *       writing to the outbox for external systems) are handled in
 *       {@link OrderPlacedEventListener}, which runs after this transaction commits —
 *       see that class for why.</li>
 * </ol>
 */
@Service
public class PlaceOrderCommandHandler {

    private final OrderJpaRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PlaceOrderCommandHandler(OrderJpaRepository orderRepository,
                                     ApplicationEventPublisher eventPublisher,
                                     Clock clock) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public UUID handle(PlaceOrderCommand command) {
        Optional<OrderEntity> existing = orderRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return existing.get().getId();
        }

        List<OrderLine> lines = command.lines().stream()
                .map(l -> new OrderLine(l.productCode(), l.quantity(), Money.of(l.unitPrice(), command.currency())))
                .toList();

        Order order = Order.place(command.customerId(), command.idempotencyKey(), lines, Instant.now(clock));

        orderRepository.save(OrderEntity.fromDomain(order));

        // Published now, but only actually acted on after this transaction commits —
        // see OrderPlacedEventListener.
        eventPublisher.publishEvent(order.placedEvent());

        return order.id();
    }
}
