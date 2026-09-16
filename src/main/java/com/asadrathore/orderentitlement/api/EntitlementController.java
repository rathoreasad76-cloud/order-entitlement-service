package com.asadrathore.orderentitlement.api;

import com.asadrathore.orderentitlement.application.EntitlementQueryService;
import com.asadrathore.orderentitlement.application.EntitlementView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers/{customerId}/entitlements")
public class EntitlementController {

    private final EntitlementQueryService entitlementQueryService;

    public EntitlementController(EntitlementQueryService entitlementQueryService) {
        this.entitlementQueryService = entitlementQueryService;
    }

    @GetMapping
    public List<EntitlementView> listEntitlements(@PathVariable String customerId) {
        return entitlementQueryService.listForCustomer(customerId);
    }
}
