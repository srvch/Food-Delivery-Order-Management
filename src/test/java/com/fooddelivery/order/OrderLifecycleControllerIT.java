package com.fooddelivery.order;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.AbstractIntegrationTest;
import com.fooddelivery.order.dto.OrderResponse;
import com.fooddelivery.order.dto.PlaceOrderRequest;
import com.fooddelivery.order.dto.UpdateStatusRequest;
import com.fooddelivery.restaurant.dto.AdminCreateRestaurantRequest;
import com.fooddelivery.restaurant.dto.MenuItemRequest;
import com.fooddelivery.restaurant.dto.MenuItemResponse;
import com.fooddelivery.restaurant.dto.RestaurantResponse;
import com.fooddelivery.user.Role;
import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.LoginRequest;
import com.fooddelivery.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderLifecycleControllerIT extends AbstractIntegrationTest {

    private <T> HttpEntity<T> authed(T body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void ownerAcceptsThenMarksPreparingThenRejectionRestoresStockOnASeparateOrder() {
        String admin = restTemplate.postForEntity("/auth/login",
                new LoginRequest("admin@fooddelivery.com", "Admin@123"), AuthResponse.class).getBody().token();
        Long cityId = restTemplate.exchange("/cities", HttpMethod.POST,
                authed(new CityRequest("LifecycleCity", true), admin), CityResponse.class).getBody().id();
        var createReq = new AdminCreateRestaurantRequest(cityId, "LifecycleRest", "Addr",
                "lifecycle-owner@example.com", "password123");
        Long restaurantId = restTemplate.exchange("/admin/restaurants", HttpMethod.POST,
                authed(createReq, admin), RestaurantResponse.class).getBody().id();
        String ownerToken = restTemplate.postForEntity("/auth/login",
                new LoginRequest("lifecycle-owner@example.com", "password123"), AuthResponse.class).getBody().token();
        Long menuItemId = restTemplate.exchange("/restaurants/" + restaurantId + "/menu-items", HttpMethod.POST,
                authed(new MenuItemRequest("Item", new BigDecimal("30.00"), 10, true), ownerToken),
                MenuItemResponse.class).getBody().id();
        String customerToken = restTemplate.postForEntity("/auth/register",
                new RegisterRequest("lifecycle-cust@example.com", "password123", Role.CUSTOMER, null),
                AuthResponse.class).getBody().token();

        var request = new PlaceOrderRequest(restaurantId, List.of(new PlaceOrderRequest.Item(menuItemId, 2)));
        Long orderId = restTemplate.exchange("/orders", HttpMethod.POST,
                authed(request, customerToken), OrderResponse.class).getBody().id();

        ResponseEntity<OrderResponse> accepted = restTemplate.exchange(
                "/orders/" + orderId + "/accept", HttpMethod.POST, authed(null, ownerToken), OrderResponse.class);
        assertThat(accepted.getBody().status()).isEqualTo(OrderStatus.ACCEPTED);

        ResponseEntity<OrderResponse> preparing = restTemplate.exchange(
                "/orders/" + orderId + "/status", HttpMethod.POST,
                authed(new UpdateStatusRequest(OrderStatus.PREPARING), ownerToken), OrderResponse.class);
        assertThat(preparing.getBody().status()).isEqualTo(OrderStatus.PREPARING);

        ResponseEntity<OrderResponse[]> restaurantOrders = restTemplate.exchange(
                "/restaurants/" + restaurantId + "/orders", HttpMethod.GET, authed(null, ownerToken), OrderResponse[].class);
        assertThat(restaurantOrders.getBody()).hasSize(1);

        var secondOrderReq = new PlaceOrderRequest(restaurantId, List.of(new PlaceOrderRequest.Item(menuItemId, 3)));
        Long secondOrderId = restTemplate.exchange("/orders", HttpMethod.POST,
                authed(secondOrderReq, customerToken), OrderResponse.class).getBody().id();

        ResponseEntity<OrderResponse> rejected = restTemplate.exchange(
                "/orders/" + secondOrderId + "/reject", HttpMethod.POST, authed(null, ownerToken), OrderResponse.class);
        assertThat(rejected.getBody().status()).isEqualTo(OrderStatus.REJECTED);

        ResponseEntity<MenuItemResponse[]> menu = restTemplate.exchange(
                "/restaurants/" + restaurantId + "/menu", HttpMethod.GET,
                authed(null, ownerToken), MenuItemResponse[].class);
        // stock was 10, -2 (first order) -3 (second order, then restored on reject) = 8
        assertThat(menu.getBody()[0].stockQuantity()).isEqualTo(8);
    }
}
