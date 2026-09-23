package com.fooddelivery.city;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.AbstractIntegrationTest;
import com.fooddelivery.user.Role;
import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.LoginRequest;
import com.fooddelivery.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

class CityControllerIT extends AbstractIntegrationTest {

    private String adminToken() {
        var login = new LoginRequest("admin@fooddelivery.com", "Admin@123");
        return restTemplate.postForEntity("/auth/login", login, AuthResponse.class).getBody().token();
    }

    private HttpEntity<CityRequest> authed(CityRequest body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void adminCanCreateCity() {
        ResponseEntity<CityResponse> response = restTemplate.exchange(
                "/cities", HttpMethod.POST, authed(new CityRequest("Mumbai", true), adminToken()), CityResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().name()).isEqualTo("Mumbai");
    }

    @Test
    void nonAdminCannotCreateCity() {
        var register = new RegisterRequest("cust-city@example.com", "password123", Role.CUSTOMER, null);
        String token = restTemplate.postForEntity("/auth/register", register, AuthResponse.class).getBody().token();

        ResponseEntity<String> response = restTemplate.exchange(
                "/cities", HttpMethod.POST, authed(new CityRequest("Delhi", true), token), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
