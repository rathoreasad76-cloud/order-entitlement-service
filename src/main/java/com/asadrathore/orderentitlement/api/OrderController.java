package com.asadrathore.orderentitlement.api;

import com.asadrathore.orderentitlement.api.ApiExceptionHandler.ErrorResponse;
import com.asadrathore.orderentitlement.application.OrderQueryService;
import com.asadrathore.orderentitlement.application.OrderView;
import com.asadrathore.orderentitlement.application.PlaceOrderCommand;
import com.asadrathore.orderentitlement.application.PlaceOrderCommandHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Place orders and read them back")
public class OrderController {

    private final PlaceOrderCommandHandler placeOrderCommandHandler;
    private final OrderQueryService orderQueryService;

    public OrderController(PlaceOrderCommandHandler placeOrderCommandHandler, OrderQueryService orderQueryService) {
        this.placeOrderCommandHandler = placeOrderCommandHandler;
        this.orderQueryService = orderQueryService;
    }

    @Operation(
            summary = "Place an order",
            description = "Idempotent: repeating a request with the same Idempotency-Key returns the "
                    + "original order instead of creating a duplicate. Entitlements are granted "
                    + "after the order commits.")
    @ApiResponse(responseCode = "201", description = "Order placed (or the existing order for this key)")
    @ApiResponse(responseCode = "400", description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<OrderView> placeOrder(
            @Parameter(description = "Client-generated key that makes retries safe", example = "demo-key-1")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request,
            @Parameter(hidden = true) UriComponentsBuilder uriBuilder) {

        PlaceOrderCommand command = new PlaceOrderCommand(
                request.customerId(),
                idempotencyKey,
                request.currency(),
                request.lines().stream()
                        .map(l -> new PlaceOrderCommand.Line(l.productCode(), l.quantity(), l.unitPrice()))
                        .toList()
        );

        UUID orderId = placeOrderCommandHandler.handle(command);
        OrderView view = orderQueryService.getById(orderId);

        URI location = uriBuilder.path("/api/orders/{id}").buildAndExpand(orderId).toUri();
        return ResponseEntity.created(location).body(view);
    }

    @Operation(summary = "Get an order by id")
    @ApiResponse(responseCode = "200", description = "Order found")
    @ApiResponse(responseCode = "404", description = "No order with this id",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public OrderView getOrder(@PathVariable UUID id) {
        return orderQueryService.getById(id);
    }
}
