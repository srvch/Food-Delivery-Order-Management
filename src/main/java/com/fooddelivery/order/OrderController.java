package com.fooddelivery.order;

import com.fooddelivery.order.dto.OrderResponse;
import com.fooddelivery.order.dto.PlaceOrderRequest;
import com.fooddelivery.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> place(@AuthenticationPrincipal User customer,
                                                @Valid @RequestBody PlaceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(customer, request));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.getOrder(id);
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<OrderResponse> myOrders(@AuthenticationPrincipal User customer) {
        return orderService.listCustomerOrders(customer.getId());
    }
}
