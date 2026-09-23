package com.fooddelivery.restaurant.dto;

import jakarta.validation.constraints.NotBlank;

public record RestaurantUpdateRequest(@NotBlank String name, @NotBlank String address) {
}
