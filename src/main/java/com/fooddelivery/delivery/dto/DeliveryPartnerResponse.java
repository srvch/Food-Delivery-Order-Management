package com.fooddelivery.delivery.dto;

public record DeliveryPartnerResponse(
        Long id, Long userId, String email, Long cityId, String cityName, boolean active
) {
}
