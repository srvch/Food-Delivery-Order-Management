package com.fooddelivery.user.dto;

import com.fooddelivery.user.Role;

public record AuthResponse(String token, Long userId, Role role) {
}
