package com.asadrathore.orderentitlement.api;

import com.asadrathore.orderentitlement.application.OrderQueryService;
import com.asadrathore.orderentitlement.application.OrderView;
import com.asadrathore.orderentitlement.application.PlaceOrderCommand;
import com.asadrathore.orderentitlement.application.PlaceOrderCommandHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
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
@Tag(name = "Orders", description = "Placing and retrieving orders")
public class OrderController {

    private final PlaceOrderCommandHandler placeOrderCommandHandler;
    private final OrderQueryService orderQueryService;

    public OrderController(PlaceOrderCommandHandler placeOrderCommandHandler, OrderQueryService orderQueryService) {
        this.placeOrderCommandHandler = placeOrderCommandHandler;
        this.orderQueryService = orderQueryService;
    }

    @Operation(
            summary = "Place an order",
            description = """
                    Places an order for one or more product lines and returns the resulting order.

                    The call is idempotent on the `Idempotency-Key` header: replaying a request \
                    with a key that has already been used returns the order created by the first \
                    call, with `201` and the same body, rather than placing a duplicate. Use a \
                    fresh key per distinct order and reuse the same key when retrying.

                    Entitlements for the ordered products are granted after the order commits, \
                    not within this request — see `GET /api/customers/{customerId}/entitlements`.
                    """)
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Order placed, or the existing order returned for a replayed idempotency key",
                    headers = @Header(
                            name = "Location",
                            description = "URI of the created order",
                            schema = @Schema(type = "string", format = "uri"))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Request body failed validation, or the order violates a domain rule",
                    content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderView> placeOrder(
            @Parameter(
                    description = "Caller-generated key that makes the request safe to retry. "
                            + "Reuse the same value when retrying a failed call; use a new one for a new order.",
                    required = true,
                    example = "8f1c2e4a-6b9d-4f3a-9c21-0d7e5a1b8c34")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest request,
            UriComponentsBuilder uriBuilder) {

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

    @Operation(summary = "Fetch an order by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The order"),
            @ApiResponse(
                    responseCode = "404",
                    description = "No order exists with that id",
                    content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ErrorResponse.class)))
    })
    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public OrderView getOrder(
            @Parameter(description = "Order id returned when the order was placed", required = true)
            @PathVariable UUID id) {
        return orderQueryService.getById(id);
    }
}
