package com.fooddelivery.delivery.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminCreateDeliveryPartnerRequest(
        @NotNull Long cityId,
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 100) String password
) {
}
