package com.asadrathore.orderentitlement.application;

import com.asadrathore.orderentitlement.infrastructure.persistence.OrderEntity;
import com.asadrathore.orderentitlement.infrastructure.persistence.OrderJpaRepository;
import com.asadrathore.orderentitlement.infrastructure.persistence.OrderLineEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Query-side service for orders. Reads straight from the JPA entities rather than
 * rehydrating the domain aggregate — there's no business logic to enforce on a read,
 * so there's no reason to pay for reconstructing the aggregate.
 */
@Service
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderJpaRepository orderRepository;

    public OrderQueryService(OrderJpaRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public OrderView getById(UUID id) {
        OrderEntity entity = orderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No order found with id " + id));
        return toView(entity);
    }

    private OrderView toView(OrderEntity entity) {
        BigDecimal total = entity.getLines().stream()
                .map(l -> l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OrderView(
                entity.getId(),
                entity.getCustomerId(),
                entity.getStatus().name(),
                total,
                entity.getCurrency(),
                entity.getPlacedAt(),
                entity.getLines().stream()
                        .map(l -> new OrderView.LineView(l.getProductCode(), l.getQuantity(), l.getUnitPrice()))
                        .toList()
        );
    }
}
