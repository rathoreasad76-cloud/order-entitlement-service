package com.asadrathore.orderentitlement.api;

import com.asadrathore.orderentitlement.application.EntitlementQueryService;
import com.asadrathore.orderentitlement.application.EntitlementView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers/{customerId}/entitlements")
@Tag(name = "Entitlements", description = "Products a customer is entitled to")
public class EntitlementController {

    private final EntitlementQueryService entitlementQueryService;

    public EntitlementController(EntitlementQueryService entitlementQueryService) {
        this.entitlementQueryService = entitlementQueryService;
    }

    @Operation(summary = "List a customer's entitlements",
            description = "Returns an empty list if the customer has none.")
    @GetMapping
    public List<EntitlementView> listEntitlements(
            @Parameter(example = "customer-1") @PathVariable String customerId) {
        return entitlementQueryService.listForCustomer(customerId);
    }
}
