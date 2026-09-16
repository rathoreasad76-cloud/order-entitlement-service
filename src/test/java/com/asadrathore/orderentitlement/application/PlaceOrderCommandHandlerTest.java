package com.asadrathore.orderentitlement.application;

import com.asadrathore.orderentitlement.infrastructure.persistence.OrderEntity;
import com.asadrathore.orderentitlement.infrastructure.persistence.OrderJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceOrderCommandHandlerTest {

    @Mock
    private OrderJpaRepository orderRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PlaceOrderCommandHandler handler;

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        handler = new PlaceOrderCommandHandler(orderRepository, eventPublisher, fixedClock);
    }

    private PlaceOrderCommand aCommand(String idempotencyKey) {
        return new PlaceOrderCommand(
                "customer-1",
                idempotencyKey,
                "USD",
                List.of(new PlaceOrderCommand.Line("WIDGET", 2, new BigDecimal("10.00")))
        );
    }

    @Test
    void placingANewOrderSavesItAndPublishesAnOrderPlacedEvent() {
        when(orderRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());

        UUID orderId = handler.handle(aCommand("idem-1"));

        verify(orderRepository, times(1)).save(any(OrderEntity.class));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(
                com.asadrathore.orderentitlement.domain.order.OrderPlacedEvent.class);

        assertThat(orderId).isNotNull();
    }

    @Test
    void repeatingTheSameIdempotencyKeyReturnsTheExistingOrderWithoutSavingAgain() {
        OrderEntity existing = OrderEntity.fromDomain(
                com.asadrathore.orderentitlement.domain.order.Order.place(
                        "customer-1",
                        "idem-1",
                        List.of(new com.asadrathore.orderentitlement.domain.order.OrderLine(
                                "WIDGET", 2,
                                com.asadrathore.orderentitlement.domain.order.Money.of(new BigDecimal("10.00"), "USD"))),
                        FIXED_INSTANT
                )
        );
        when(orderRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(existing));

        UUID orderId = handler.handle(aCommand("idem-1"));

        assertThat(orderId).isEqualTo(existing.getId());
        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
