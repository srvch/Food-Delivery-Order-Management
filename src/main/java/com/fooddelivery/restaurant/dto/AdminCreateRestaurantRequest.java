package com.fooddelivery.restaurant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminCreateRestaurantRequest(
        @NotNull Long cityId,
        @NotBlank String name,
        @NotBlank String address,
        @Email @NotBlank String ownerEmail,
        @NotBlank @Size(min = 8, max = 100) String ownerPassword
) {
}
