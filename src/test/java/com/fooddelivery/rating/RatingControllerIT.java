package com.fooddelivery.rating;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.AbstractIntegrationTest;
import com.fooddelivery.delivery.dto.AssignmentResponse;
import com.fooddelivery.delivery.dto.DeliveryPartnerResponse;
import com.fooddelivery.delivery.dto.DeliveryPartnerUpdateRequest;
import com.fooddelivery.order.OrderStatus;
import com.fooddelivery.order.dto.OrderResponse;
import com.fooddelivery.order.dto.PlaceOrderRequest;
import com.fooddelivery.order.dto.UpdateStatusRequest;
import com.fooddelivery.rating.dto.RateRequest;
import com.fooddelivery.rating.dto.RatingResponse;
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

class RatingControllerIT extends AbstractIntegrationTest {

    private <T> HttpEntity<T> authed(T body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void customerRatesDeliveredOrderAndRestaurantAverageUpdates() {
        String admin = restTemplate.postForEntity("/auth/login",
                new LoginRequest("admin@fooddelivery.com", "Admin@123"), AuthResponse.class).getBody().token();
        Long cityId = restTemplate.exchange("/cities", HttpMethod.POST,
                authed(new CityRequest("RateCity", true), admin), CityResponse.class).getBody().id();
        var createReq = new AdminCreateRestaurantRequest(cityId, "RateRest", "Addr",
                "rate-owner@example.com", "password123");
        Long restaurantId = restTemplate.exchange("/admin/restaurants", HttpMethod.POST,
                authed(createReq, admin), RestaurantResponse.class).getBody().id();
        String ownerToken = restTemplate.postForEntity("/auth/login",
                new LoginRequest("rate-owner@example.com", "password123"), AuthResponse.class).getBody().token();
        Long menuItemId = restTemplate.exchange("/restaurants/" + restaurantId + "/menu-items", HttpMethod.POST,
                authed(new MenuItemRequest("Item", new BigDecimal("18.00"), 5, true), ownerToken),
                MenuItemResponse.class).getBody().id();
        String customerToken = restTemplate.postForEntity("/auth/register",
                new RegisterRequest("rate-cust@example.com", "password123", Role.CUSTOMER, null),
                AuthResponse.class).getBody().token();
        String partnerToken = restTemplate.postForEntity("/auth/register",
                new RegisterRequest("rate-partner@example.com", "password123", Role.DELIVERY_PARTNER, cityId),
                AuthResponse.class).getBody().token();
        DeliveryPartnerResponse[] partners = restTemplate.exchange("/admin/delivery-partners", HttpMethod.GET,
                authed(null, admin), DeliveryPartnerResponse[].class).getBody();
        Long partnerId = java.util.Arrays.stream(partners)
                .filter(p -> p.email().equals("rate-partner@example.com"))
                .findFirst().orElseThrow().id();
        restTemplate.exchange("/admin/delivery-partners/" + partnerId, HttpMethod.PUT,
                authed(new DeliveryPartnerUpdateRequest(cityId, true), admin), Void.class);

        var placeReq = new PlaceOrderRequest(restaurantId, List.of(new PlaceOrderRequest.Item(menuItemId, 1)));
        Long orderId = restTemplate.exchange("/orders", HttpMethod.POST,
                authed(placeReq, customerToken), OrderResponse.class).getBody().id();
        restTemplate.exchange("/orders/" + orderId + "/accept", HttpMethod.POST, authed(null, ownerToken), OrderResponse.class);
        Long assignmentId = restTemplate.exchange("/assignments/open?cityId=" + cityId, HttpMethod.GET,
                authed(null, partnerToken), AssignmentResponse[].class).getBody()[0].id();
        restTemplate.exchange("/assignments/" + assignmentId + "/accept", HttpMethod.POST, authed(null, partnerToken), AssignmentResponse.class);
        restTemplate.exchange("/orders/" + orderId + "/status", HttpMethod.POST,
                authed(new UpdateStatusRequest(OrderStatus.PREPARING), ownerToken), OrderResponse.class);
        restTemplate.exchange("/orders/" + orderId + "/status", HttpMethod.POST,
                authed(new UpdateStatusRequest(OrderStatus.OUT_FOR_DELIVERY), partnerToken), OrderResponse.class);
        restTemplate.exchange("/orders/" + orderId + "/status", HttpMethod.POST,
                authed(new UpdateStatusRequest(OrderStatus.DELIVERED), partnerToken), OrderResponse.class);

        ResponseEntity<RatingResponse> rated = restTemplate.exchange(
                "/orders/" + orderId + "/ratings", HttpMethod.POST,
                authed(new RateRequest(5, "Excellent"), customerToken), RatingResponse.class);
        assertThat(rated.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<RestaurantResponse> restaurant = restTemplate.exchange(
                "/restaurants/" + restaurantId, HttpMethod.GET,
                authed(null, customerToken), RestaurantResponse.class);
        assertThat(restaurant.getBody().avgRating()).isEqualByComparingTo("5.00");
        assertThat(restaurant.getBody().ratingCount()).isEqualTo(1);

        ResponseEntity<String> duplicate = restTemplate.exchange(
                "/orders/" + orderId + "/ratings", HttpMethod.POST,
                authed(new RateRequest(3, "second try"), customerToken), String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
