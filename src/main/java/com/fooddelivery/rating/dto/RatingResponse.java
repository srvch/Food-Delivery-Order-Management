package com.fooddelivery.rating.dto;

import java.time.Instant;

public record RatingResponse(Long id, Long orderId, Long restaurantId, int score, String review, Instant createdAt) {
}
