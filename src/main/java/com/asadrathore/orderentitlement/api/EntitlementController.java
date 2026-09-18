package com.asadrathore.orderentitlement.api;

import com.asadrathore.orderentitlement.application.EntitlementQueryService;
import com.asadrathore.orderentitlement.application.EntitlementView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers/{customerId}/entitlements")
@Tag(name = "Entitlements", description = "What a customer has been granted access to")
public class EntitlementController {

    private final EntitlementQueryService entitlementQueryService;

    public EntitlementController(EntitlementQueryService entitlementQueryService) {
        this.entitlementQueryService = entitlementQueryService;
    }

    @Operation(
            summary = "List a customer's entitlements",
            description = """
                    Returns every entitlement granted to the customer, each naming the order it \
                    came from.

                    Entitlements are granted asynchronously after an order commits, so this list \
                    is eventually consistent: calling it immediately after `POST /api/orders` can \
                    return before the new entitlement appears. An unknown customer id is not an \
                    error — it simply has no entitlements yet, and returns an empty list.
                    """)
    @ApiResponse(responseCode = "200", description = "The customer's entitlements, possibly empty")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<EntitlementView> listEntitlements(
            @Parameter(description = "Customer identifier used when the order was placed", example = "customer-1")
            @PathVariable String customerId) {
        return entitlementQueryService.listForCustomer(customerId);
    }
}
