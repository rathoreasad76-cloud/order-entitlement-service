package com.asadrathore.orderentitlement.infrastructure.outbox;

import com.asadrathore.orderentitlement.infrastructure.persistence.OutboxEventEntity;
import com.asadrathore.orderentitlement.infrastructure.persistence.OutboxEventJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class OutboxEventWriter {

    private final OutboxEventJpaRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxEventWriter(OutboxEventJpaRepository outboxEventRepository, ObjectMapper objectMapper, Clock clock) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void write(String eventType, String aggregateId, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            Instant now = Instant.now(clock);
            outboxEventRepository.save(new OutboxEventEntity(eventType, aggregateId, json, now));
        } catch (Exception e) {
            // In production this would go to a dead-letter path rather than a bare
            // RuntimeException — kept simple here since it's not the point of the demo.
            throw new IllegalStateException("Failed to serialise outbox event of type " + eventType, e);
        }
    }
}
