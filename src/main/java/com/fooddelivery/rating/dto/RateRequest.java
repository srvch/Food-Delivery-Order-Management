package com.fooddelivery.rating.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record RateRequest(@Min(1) @Max(5) int score, @Size(max = 2000) String review) {
}
