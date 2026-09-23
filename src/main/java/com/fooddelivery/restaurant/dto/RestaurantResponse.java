package com.fooddelivery.restaurant.dto;

import java.math.BigDecimal;

public record RestaurantResponse(
        Long id, Long cityId, String cityName, Long ownerId,
        String name, String address, BigDecimal avgRating, int ratingCount
) {
}
