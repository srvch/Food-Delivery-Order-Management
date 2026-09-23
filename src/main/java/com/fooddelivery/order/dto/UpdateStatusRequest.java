package com.fooddelivery.order.dto;

import com.fooddelivery.order.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull OrderStatus status) {
}
