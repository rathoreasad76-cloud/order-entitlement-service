package com.asadrathore.orderentitlement.infrastructure.outbox;

import com.asadrathore.orderentitlement.infrastructure.persistence.OutboxEventEntity;
import com.asadrathore.orderentitlement.infrastructure.persistence.OutboxEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Polls the outbox table for unpublished rows and "publishes" them.
 *
 * <p>In a real system this would hand the payload to a Kafka producer, an SNS topic,
 * or similar — deliberately left as a log line here so this sample has no external
 * infrastructure dependency beyond Postgres. The pattern (poll, publish, mark
 * published, all with at-least-once delivery semantics) is the part worth
 * demonstrating; the transport is an implementation detail you'd swap in.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventJpaRepository outboxEventRepository;
    private final Clock clock;

    public OutboxPublisher(OutboxEventJpaRepository outboxEventRepository, Clock clock) {
        this.outboxEventRepository = outboxEventRepository;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPending() {
        List<OutboxEventEntity> pending = outboxEventRepository.findTop50ByPublishedAtIsNullOrderByOccurredAtAsc();
        for (OutboxEventEntity event : pending) {
            log.info("Publishing outbox event [{}] type={} aggregateId={} payload={}",
                    event.getEventId(), event.getEventType(), event.getAggregateId(), event.getPayload());
            event.markPublished(Instant.now(clock));
        }
    }
}
