package com.fooddelivery.city.dto;

import jakarta.validation.constraints.NotBlank;

public record CityRequest(@NotBlank String name, boolean active) {
}
