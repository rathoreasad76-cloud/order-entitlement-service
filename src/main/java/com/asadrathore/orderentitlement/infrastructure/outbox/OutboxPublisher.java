package com.asadrathore.orderentitlement.infrastructure.outbox;

import com.asadrathore.orderentitlement.infrastructure.persistence.OutboxEventEntity;
import com.asadrathore.orderentitlement.infrastructure.persistence.OutboxEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Polls the outbox table for unpublished rows and publishes each one to an SNS
 * topic, with the event type carried as a message attribute so downstream SQS
 * subscriptions can filter on it without deserialising the body.
 *
 * <p>This is deliberately at-least-once, not exactly-once: a row is only marked
 * published after the SNS call succeeds, so a crash between "publish succeeded"
 * and "mark published committed" would republish it on the next poll. Consumers
 * on the other end are expected to be idempotent (dedup on message ID) rather
 * than relying on this publisher to guarantee single delivery — that's a much
 * easier property to hold onto at the consumer than to fake at the producer.
 *
 * <p>If SNS is unreachable, publishing for this batch simply fails and is retried
 * on the next scheduled run; nothing is marked published, so nothing is lost.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventJpaRepository outboxEventRepository;
    private final SnsClient snsClient;
    private final Clock clock;
    private final String topicArn;

    public OutboxPublisher(
            OutboxEventJpaRepository outboxEventRepository,
            SnsClient snsClient,
            Clock clock,
            @Value("${aws.sns.topic-arn}") String topicArn) {
        this.outboxEventRepository = outboxEventRepository;
        this.snsClient = snsClient;
        this.clock = clock;
        this.topicArn = topicArn;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPending() {
        List<OutboxEventEntity> pending = outboxEventRepository.findTop50ByPublishedAtIsNullOrderByOccurredAtAsc();
        for (OutboxEventEntity event : pending) {
            try {
                publish(event);
                event.markPublished(Instant.now(clock));
            } catch (SnsException e) {
                // Leave it unpublished; the next poll will retry. Logged at WARN
                // rather than ERROR since a transient SNS blip during normal
                // operation isn't itself an incident.
                log.warn("Failed to publish outbox event [{}], will retry on next poll", event.getEventId(), e);
            }
        }
    }

    private void publish(OutboxEventEntity event) {
        PublishRequest request = PublishRequest.builder()
                .topicArn(topicArn)
                .message(event.getPayload())
                .messageAttributes(Map.of(
                        "eventType", MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(event.getEventType())
                                .build(),
                        "aggregateId", MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(event.getAggregateId())
                                .build()
                ))
                .build();

        snsClient.publish(request);
        log.info("Published outbox event [{}] type={} aggregateId={} to {}",
                event.getEventId(), event.getEventType(), event.getAggregateId(), topicArn);
    }
}
