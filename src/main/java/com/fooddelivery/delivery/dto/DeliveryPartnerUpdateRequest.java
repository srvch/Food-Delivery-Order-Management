package com.fooddelivery.delivery.dto;

import jakarta.validation.constraints.NotNull;

public record DeliveryPartnerUpdateRequest(@NotNull Long cityId, boolean active) {
}
