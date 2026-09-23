package com.fooddelivery.delivery;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.AbstractIntegrationTest;
import com.fooddelivery.delivery.dto.DeliveryPartnerResponse;
import com.fooddelivery.delivery.dto.DeliveryPartnerUpdateRequest;
import com.fooddelivery.user.Role;
import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.LoginRequest;
import com.fooddelivery.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryPartnerControllerIT extends AbstractIntegrationTest {

    private String adminToken() {
        var login = new LoginRequest("admin@fooddelivery.com", "Admin@123");
        return restTemplate.postForEntity("/auth/login", login, AuthResponse.class).getBody().token();
    }

    private <T> HttpEntity<T> authed(T body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void selfRegisteredPartnerStartsInactiveThenAdminActivates() {
        String admin = adminToken();
        Long cityId = restTemplate.exchange("/cities", HttpMethod.POST,
                authed(new CityRequest("Hyderabad", true), admin), CityResponse.class).getBody().id();

        var register = new RegisterRequest("partner-x@example.com", "password123", Role.DELIVERY_PARTNER, cityId);
        restTemplate.postForEntity("/auth/register", register, AuthResponse.class);

        ResponseEntity<DeliveryPartnerResponse[]> list = restTemplate.exchange(
                "/admin/delivery-partners", HttpMethod.GET, authed(null, admin), DeliveryPartnerResponse[].class);
        DeliveryPartnerResponse profile = java.util.Arrays.stream(list.getBody())
                .filter(p -> p.email().equals("partner-x@example.com"))
                .findFirst().orElseThrow();
        assertThat(profile.active()).isFalse();

        ResponseEntity<DeliveryPartnerResponse> updated = restTemplate.exchange(
                "/admin/delivery-partners/" + profile.id(), HttpMethod.PUT,
                authed(new DeliveryPartnerUpdateRequest(cityId, true), admin), DeliveryPartnerResponse.class);
        assertThat(updated.getBody().active()).isTrue();
    }

    @Test
    void registerDeliveryPartnerWithoutCityIdFails() {
        var register = new RegisterRequest("partner-y@example.com", "password123", Role.DELIVERY_PARTNER, null);

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register", register, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
