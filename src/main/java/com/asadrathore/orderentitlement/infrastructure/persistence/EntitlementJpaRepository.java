package com.asadrathore.orderentitlement.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EntitlementJpaRepository extends JpaRepository<EntitlementEntity, UUID> {
    List<EntitlementEntity> findByCustomerId(String customerId);
}
