package com.fooddelivery.delivery.dto;

import com.fooddelivery.delivery.DeliveryAssignmentStatus;

import java.time.Instant;

public record AssignmentResponse(
        Long id, Long orderId, DeliveryAssignmentStatus status, Long partnerId,
        Instant offeredAt, Instant acceptedAt
) {
}
