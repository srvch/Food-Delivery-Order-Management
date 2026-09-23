package com.fooddelivery.notification;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.AbstractIntegrationTest;
import com.fooddelivery.notification.dto.NotificationResponse;
import com.fooddelivery.order.dto.OrderResponse;
import com.fooddelivery.order.dto.PlaceOrderRequest;
import com.fooddelivery.restaurant.dto.AdminCreateRestaurantRequest;
import com.fooddelivery.restaurant.dto.MenuItemRequest;
import com.fooddelivery.restaurant.dto.MenuItemResponse;
import com.fooddelivery.restaurant.dto.RestaurantResponse;
import com.fooddelivery.user.Role;
import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.LoginRequest;
import com.fooddelivery.user.dto.RegisterRequest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationFanOutIT extends AbstractIntegrationTest {

    private <T> HttpEntity<T> authed(T body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void placingAnOrderEventuallyNotifiesCustomerAndOwner() {
        String admin = restTemplate.postForEntity("/auth/login",
                new LoginRequest("admin@fooddelivery.com", "Admin@123"), AuthResponse.class).getBody().token();
        Long cityId = restTemplate.exchange("/cities", HttpMethod.POST,
                authed(new CityRequest("NotifyCity", true), admin), CityResponse.class).getBody().id();
        var createReq = new AdminCreateRestaurantRequest(cityId, "NotifyRest", "Addr",
                "notify-owner@example.com", "password123");
        Long restaurantId = restTemplate.exchange("/admin/restaurants", HttpMethod.POST,
                authed(createReq, admin), RestaurantResponse.class).getBody().id();
        String ownerToken = restTemplate.postForEntity("/auth/login",
                new LoginRequest("notify-owner@example.com", "password123"), AuthResponse.class).getBody().token();
        Long menuItemId = restTemplate.exchange("/restaurants/" + restaurantId + "/menu-items", HttpMethod.POST,
                authed(new MenuItemRequest("Item", new BigDecimal("20.00"), 5, true), ownerToken),
                MenuItemResponse.class).getBody().id();
        String customerToken = restTemplate.postForEntity("/auth/register",
                new RegisterRequest("notify-cust@example.com", "password123", Role.CUSTOMER, null),
                AuthResponse.class).getBody().token();

        var placeReq = new PlaceOrderRequest(restaurantId, List.of(new PlaceOrderRequest.Item(menuItemId, 1)));
        restTemplate.exchange("/orders", HttpMethod.POST, authed(placeReq, customerToken), OrderResponse.class);

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            ResponseEntity<NotificationResponse[]> customerNotifs = restTemplate.exchange(
                    "/notifications", HttpMethod.GET, authed(null, customerToken), NotificationResponse[].class);
            assertThat(customerNotifs.getBody()).isNotEmpty();

            ResponseEntity<NotificationResponse[]> ownerNotifs = restTemplate.exchange(
                    "/notifications", HttpMethod.GET, authed(null, ownerToken), NotificationResponse[].class);
            assertThat(ownerNotifs.getBody()).isNotEmpty();
        });
    }
}
