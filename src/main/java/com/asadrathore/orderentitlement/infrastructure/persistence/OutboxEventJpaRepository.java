package com.asadrathore.orderentitlement.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, Long> {
    List<OutboxEventEntity> findTop50ByPublishedAtIsNullOrderByOccurredAtAsc();
}
