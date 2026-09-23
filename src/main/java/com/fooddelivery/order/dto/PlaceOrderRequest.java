package com.fooddelivery.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PlaceOrderRequest(
        @NotNull Long restaurantId,
        @NotEmpty @Valid List<Item> items
) {
    public record Item(@NotNull Long menuItemId, @Min(1) int quantity) {
    }
}
