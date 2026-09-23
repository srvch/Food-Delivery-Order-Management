package com.fooddelivery.order.dto;

import com.fooddelivery.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id, Long customerId, Long restaurantId, OrderStatus status,
        BigDecimal totalAmount, List<OrderItemLine> items,
        Instant createdAt, Instant updatedAt
) {
}
