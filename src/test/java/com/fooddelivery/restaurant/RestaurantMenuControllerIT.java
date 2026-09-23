package com.fooddelivery.restaurant;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.AbstractIntegrationTest;
import com.fooddelivery.restaurant.dto.*;
import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RestaurantMenuControllerIT extends AbstractIntegrationTest {

    private String adminToken() {
        var login = new LoginRequest("admin@fooddelivery.com", "Admin@123");
        return restTemplate.postForEntity("/auth/login", login, AuthResponse.class).getBody().token();
    }

    private <T> HttpEntity<T> authed(T body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private Long createCity(String name, String token) {
        var response = restTemplate.exchange("/cities", HttpMethod.POST,
                authed(new CityRequest(name, true), token), CityResponse.class);
        return response.getBody().id();
    }

    @Test
    void ownerCanManageOwnMenuButNotAnotherRestaurant() {
        String admin = adminToken();
        Long cityId = createCity("Chennai", admin);

        var createReq = new AdminCreateRestaurantRequest(cityId, "Spice Hub", "Anna Salai",
                "owner-a@example.com", "password123");
        RestaurantResponse restaurantA = restTemplate.exchange("/admin/restaurants", HttpMethod.POST,
                authed(createReq, admin), RestaurantResponse.class).getBody();

        var createReqB = new AdminCreateRestaurantRequest(cityId, "Curry Corner", "T Nagar",
                "owner-b@example.com", "password123");
        RestaurantResponse restaurantB = restTemplate.exchange("/admin/restaurants", HttpMethod.POST,
                authed(createReqB, admin), RestaurantResponse.class).getBody();

        String ownerAToken = restTemplate.postForEntity("/auth/login",
                new LoginRequest("owner-a@example.com", "password123"), AuthResponse.class).getBody().token();
        String ownerBToken = restTemplate.postForEntity("/auth/login",
                new LoginRequest("owner-b@example.com", "password123"), AuthResponse.class).getBody().token();

        var menuItemReq = new MenuItemRequest("Masala Dosa", new BigDecimal("80.00"), 20, true);
        ResponseEntity<MenuItemResponse> addOwn = restTemplate.exchange(
                "/restaurants/" + restaurantA.id() + "/menu-items", HttpMethod.POST,
                authed(menuItemReq, ownerAToken), MenuItemResponse.class);
        assertThat(addOwn.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> addToOthers = restTemplate.exchange(
                "/restaurants/" + restaurantB.id() + "/menu-items", HttpMethod.POST,
                authed(menuItemReq, ownerAToken), String.class);
        assertThat(addToOthers.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<MenuItemResponse[]> menu = restTemplate.getForEntity(
                "/restaurants/" + restaurantA.id() + "/menu", MenuItemResponse[].class);
        assertThat(menu.getBody()).hasSize(1);
        assertThat(menu.getBody()[0].name()).isEqualTo("Masala Dosa");
    }
}
