package com.fooddelivery.restaurant.dto;

import java.math.BigDecimal;

public record MenuItemResponse(
        Long id, Long restaurantId, String name, BigDecimal price, int stockQuantity, boolean available
) {
}
