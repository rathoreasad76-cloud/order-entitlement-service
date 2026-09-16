package com.asadrathore.orderentitlement.application;

import com.asadrathore.orderentitlement.domain.entitlement.Entitlement;
import com.asadrathore.orderentitlement.domain.order.OrderPlacedEvent;
import com.asadrathore.orderentitlement.infrastructure.persistence.EntitlementEntity;
import com.asadrathore.orderentitlement.infrastructure.persistence.EntitlementJpaRepository;
import com.asadrathore.orderentitlement.infrastructure.outbox.OutboxEventWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Instant;

/**
 * Reacts to {@link OrderPlacedEvent} after the placing transaction has committed.
 *
 * <p>Why {@code AFTER_COMMIT} rather than handling this inline in the command handler?
 * If entitlement granting happened in the same transaction as the order save and then
 * failed for an unrelated reason (a constraint violation, a lock timeout), the whole
 * order placement would roll back — even though the order itself was perfectly valid.
 * Decoupling the two means a failure in "what happens next" never corrupts "did the
 * order succeed."
 *
 * <p>This listener also writes a row to the outbox table in the same (new) transaction
 * as the entitlement grant. That row is what a separate poller ({@code OutboxPublisher})
 * picks up and publishes to whatever external system needs to know an order happened —
 * this is the transactional outbox pattern, and it exists to avoid the classic
 * dual-write problem (write to the DB, then separately publish to a broker — and have
 * the process crash in between, silently losing the message).
 */
@Component
public class OrderPlacedEventListener {

    private final EntitlementJpaRepository entitlementRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final Clock clock;

    public OrderPlacedEventListener(EntitlementJpaRepository entitlementRepository,
                                     OutboxEventWriter outboxEventWriter,
                                     Clock clock) {
        this.entitlementRepository = entitlementRepository;
        this.outboxEventWriter = outboxEventWriter;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void onOrderPlaced(OrderPlacedEvent event) {
        Instant now = Instant.now(clock);

        for (String productCode : event.productCodes()) {
            Entitlement entitlement = Entitlement.grant(event.customerId(), productCode, event.orderId(), now);
            entitlementRepository.save(EntitlementEntity.fromDomain(entitlement));
        }

        outboxEventWriter.write("OrderPlaced", event.orderId().toString(), event);
    }
}
