package com.fooddelivery.user;

import com.fooddelivery.common.AbstractIntegrationTest;
import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.LoginRequest;
import com.fooddelivery.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerIT extends AbstractIntegrationTest {

    @Test
    void registerCustomerReturnsToken() {
        var request = new RegisterRequest("customer1@example.com", "password123", Role.CUSTOMER, null);

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity("/auth/register", request, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().token()).isNotBlank();
        assertThat(response.getBody().role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void registerAdminIsForbidden() {
        var request = new RegisterRequest("wannabe-admin@example.com", "password123", Role.ADMIN, null);

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void loginWithSeededAdminSucceeds() {
        var request = new LoginRequest("admin@fooddelivery.com", "Admin@123");

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity("/auth/login", request, AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void loginWithWrongPasswordReturns401() {
        var request = new LoginRequest("admin@fooddelivery.com", "wrong-password");

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
