package com.asadrathore.orderentitlement.api;

import com.asadrathore.orderentitlement.application.OrderQueryService;
import com.asadrathore.orderentitlement.application.OrderView;
import com.asadrathore.orderentitlement.application.PlaceOrderCommand;
import com.asadrathore.orderentitlement.application.PlaceOrderCommandHandler;
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
public class OrderController {

    private final PlaceOrderCommandHandler placeOrderCommandHandler;
    private final OrderQueryService orderQueryService;

    public OrderController(PlaceOrderCommandHandler placeOrderCommandHandler, OrderQueryService orderQueryService) {
        this.placeOrderCommandHandler = placeOrderCommandHandler;
        this.orderQueryService = orderQueryService;
    }

    @PostMapping
    public ResponseEntity<OrderView> placeOrder(
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

    @GetMapping("/{id}")
    public OrderView getOrder(@PathVariable UUID id) {
        return orderQueryService.getById(id);
    }
}
