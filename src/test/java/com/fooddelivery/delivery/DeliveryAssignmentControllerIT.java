package com.fooddelivery.delivery;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.AbstractIntegrationTest;
import com.fooddelivery.delivery.dto.AssignmentResponse;
import com.fooddelivery.order.OrderStatus;
import com.fooddelivery.order.dto.OrderResponse;
import com.fooddelivery.order.dto.PlaceOrderRequest;
import com.fooddelivery.order.dto.UpdateStatusRequest;
import com.fooddelivery.restaurant.dto.AdminCreateRestaurantRequest;
import com.fooddelivery.restaurant.dto.MenuItemResponse;
import com.fooddelivery.restaurant.dto.MenuItemRequest;
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

class DeliveryAssignmentControllerIT extends AbstractIntegrationTest {

    private <T> HttpEntity<T> authed(T body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void partnerAcceptsAssignmentThenDrivesOrderToDelivered() {
        String admin = restTemplate.postForEntity("/auth/login",
                new LoginRequest("admin@fooddelivery.com", "Admin@123"), AuthResponse.class).getBody().token();
        Long cityId = restTemplate.exchange("/cities", HttpMethod.POST,
                authed(new CityRequest("AssignCity", true), admin), CityResponse.class).getBody().id();
        var createReq = new AdminCreateRestaurantRequest(cityId, "AssignRest", "Addr",
                "assign-owner@example.com", "password123");
        Long restaurantId = restTemplate.exchange("/admin/restaurants", HttpMethod.POST,
                authed(createReq, admin), RestaurantResponse.class).getBody().id();
        String ownerToken = restTemplate.postForEntity("/auth/login",
                new LoginRequest("assign-owner@example.com", "password123"), AuthResponse.class).getBody().token();
        Long menuItemId = restTemplate.exchange("/restaurants/" + restaurantId + "/menu-items", HttpMethod.POST,
                authed(new MenuItemRequest("Item", new BigDecimal("40.00"), 5, true), ownerToken),
                MenuItemResponse.class).getBody().id();
        String customerToken = restTemplate.postForEntity("/auth/register",
                new RegisterRequest("assign-cust@example.com", "password123", Role.CUSTOMER, null),
                AuthResponse.class).getBody().token();

        String partnerToken = restTemplate.postForEntity("/auth/register",
                new RegisterRequest("assign-partner@example.com", "password123", Role.DELIVERY_PARTNER, cityId),
                AuthResponse.class).getBody().token();
        var partners = restTemplate.exchange("/admin/delivery-partners", HttpMethod.GET,
                authed(null, admin), com.fooddelivery.delivery.dto.DeliveryPartnerResponse[].class).getBody();
        Long partnerProfileId = java.util.Arrays.stream(partners)
                .filter(p -> p.email().equals("assign-partner@example.com"))
                .findFirst().orElseThrow().id();
        restTemplate.exchange("/admin/delivery-partners/" + partnerProfileId, HttpMethod.PUT,
                authed(new com.fooddelivery.delivery.dto.DeliveryPartnerUpdateRequest(cityId, true), admin), Void.class);

        var placeReq = new PlaceOrderRequest(restaurantId, List.of(new PlaceOrderRequest.Item(menuItemId, 1)));
        Long orderId = restTemplate.exchange("/orders", HttpMethod.POST,
                authed(placeReq, customerToken), OrderResponse.class).getBody().id();
        restTemplate.exchange("/orders/" + orderId + "/accept", HttpMethod.POST, authed(null, ownerToken), OrderResponse.class);

        ResponseEntity<AssignmentResponse[]> open = restTemplate.exchange(
                "/assignments/open?cityId=" + cityId, HttpMethod.GET, authed(null, partnerToken), AssignmentResponse[].class);
        assertThat(open.getBody()).hasSize(1);
        Long assignmentId = open.getBody()[0].id();

        ResponseEntity<AssignmentResponse> accepted = restTemplate.exchange(
                "/assignments/" + assignmentId + "/accept", HttpMethod.POST, authed(null, partnerToken), AssignmentResponse.class);
        assertThat(accepted.getBody().status()).isEqualTo(DeliveryAssignmentStatus.ACCEPTED);

        restTemplate.exchange("/orders/" + orderId + "/status", HttpMethod.POST,
                authed(new UpdateStatusRequest(OrderStatus.PREPARING), ownerToken), OrderResponse.class);
        ResponseEntity<OrderResponse> outForDelivery = restTemplate.exchange(
                "/orders/" + orderId + "/status", HttpMethod.POST,
                authed(new UpdateStatusRequest(OrderStatus.OUT_FOR_DELIVERY), partnerToken), OrderResponse.class);
        assertThat(outForDelivery.getBody().status()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);

        ResponseEntity<OrderResponse> delivered = restTemplate.exchange(
                "/orders/" + orderId + "/status", HttpMethod.POST,
                authed(new UpdateStatusRequest(OrderStatus.DELIVERED), partnerToken), OrderResponse.class);
        assertThat(delivered.getBody().status()).isEqualTo(OrderStatus.DELIVERED);
    }
}
