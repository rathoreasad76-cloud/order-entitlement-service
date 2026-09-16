package com.asadrathore.orderentitlement.infrastructure.persistence;

import com.asadrathore.orderentitlement.domain.entitlement.Entitlement;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "entitlements")
public class EntitlementEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String productCode;

    @Column(nullable = false)
    private UUID sourceOrderId;

    @Column(nullable = false)
    private Instant grantedAt;

    protected EntitlementEntity() {
        // required by JPA
    }

    public static EntitlementEntity fromDomain(Entitlement entitlement) {
        EntitlementEntity entity = new EntitlementEntity();
        entity.id = entitlement.id();
        entity.customerId = entitlement.customerId();
        entity.productCode = entitlement.productCode();
        entity.sourceOrderId = entitlement.sourceOrderId();
        entity.grantedAt = entitlement.grantedAt();
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProductCode() {
        return productCode;
    }

    public UUID getSourceOrderId() {
        return sourceOrderId;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }
}
