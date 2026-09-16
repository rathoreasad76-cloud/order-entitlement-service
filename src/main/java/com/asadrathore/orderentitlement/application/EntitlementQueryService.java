package com.asadrathore.orderentitlement.application;

import com.asadrathore.orderentitlement.infrastructure.persistence.EntitlementJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EntitlementQueryService {

    private final EntitlementJpaRepository entitlementRepository;

    public EntitlementQueryService(EntitlementJpaRepository entitlementRepository) {
        this.entitlementRepository = entitlementRepository;
    }

    public List<EntitlementView> listForCustomer(String customerId) {
        return entitlementRepository.findByCustomerId(customerId).stream()
                .map(e -> new EntitlementView(e.getId(), e.getProductCode(), e.getSourceOrderId(), e.getGrantedAt()))
                .toList();
    }
}
