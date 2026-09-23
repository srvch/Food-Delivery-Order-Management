package com.fooddelivery.notification.dto;

import java.time.Instant;

public record NotificationResponse(Long id, Long orderId, String message, boolean read, Instant createdAt) {
}
