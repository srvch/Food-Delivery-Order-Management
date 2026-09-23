package com.fooddelivery.order.event;

import com.fooddelivery.order.OrderStatus;

public record OrderStatusChangedEvent(Long orderId, OrderStatus oldStatus, OrderStatus newStatus) {
}
