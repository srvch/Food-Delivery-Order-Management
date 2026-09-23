package com.fooddelivery.delivery;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.AbstractIntegrationTest;
import com.fooddelivery.delivery.dto.AssignmentResponse;
import com.fooddelivery.delivery.dto.DeliveryPartnerResponse;
import com.fooddelivery.delivery.dto.DeliveryPartnerUpdateRequest;
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
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryAssignmentConcurrencyIT extends AbstractIntegrationTest {

    private <T> HttpEntity<T> authed(T body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void onlyOnePartnerWinsConcurrentAcceptRace() throws InterruptedException {
        String admin = restTemplate.postForEntity("/auth/login",
                new LoginRequest("admin@fooddelivery.com", "Admin@123"), AuthResponse.class).getBody().token();
        Long cityId = restTemplate.exchange("/cities", HttpMethod.POST,
                authed(new CityRequest("RaceCity", true), admin), CityResponse.class).getBody().id();
        var createReq = new AdminCreateRestaurantRequest(cityId, "RaceRest", "Addr",
                "race-owner@example.com", "password123");
        Long restaurantId = restTemplate.exchange("/admin/restaurants", HttpMethod.POST,
                authed(createReq, admin), RestaurantResponse.class).getBody().id();
        String ownerToken = restTemplate.postForEntity("/auth/login",
                new LoginRequest("race-owner@example.com", "password123"), AuthResponse.class).getBody().token();
        Long menuItemId = restTemplate.exchange("/restaurants/" + restaurantId + "/menu-items", HttpMethod.POST,
                authed(new MenuItemRequest("Item", new BigDecimal("15.00"), 100, true), ownerToken),
                MenuItemResponse.class).getBody().id();
        String customerToken = restTemplate.postForEntity("/auth/register",
                new RegisterRequest("race-cust@example.com", "password123", Role.CUSTOMER, null),
                AuthResponse.class).getBody().token();

        int partnerCount = 10;
        List<String> partnerTokens = new ArrayList<>();
        for (int i = 0; i < partnerCount; i++) {
            String token = restTemplate.postForEntity("/auth/register",
                    new RegisterRequest("racer-partner" + i + "@example.com", "password123", Role.DELIVERY_PARTNER, cityId),
                    AuthResponse.class).getBody().token();
            partnerTokens.add(token);
        }
        DeliveryPartnerResponse[] profiles = restTemplate.exchange("/admin/delivery-partners", HttpMethod.GET,
                authed(null, admin), DeliveryPartnerResponse[].class).getBody();
        for (DeliveryPartnerResponse profile : profiles) {
            if (profile.email().startsWith("racer-partner") && profile.email().endsWith("@example.com")) {
                restTemplate.exchange("/admin/delivery-partners/" + profile.id(), HttpMethod.PUT,
                        authed(new DeliveryPartnerUpdateRequest(cityId, true), admin), Void.class);
            }
        }

        var placeReq = new PlaceOrderRequest(restaurantId, List.of(new PlaceOrderRequest.Item(menuItemId, 1)));
        Long orderId = restTemplate.exchange("/orders", HttpMethod.POST,
                authed(placeReq, customerToken), OrderResponse.class).getBody().id();
        restTemplate.exchange("/orders/" + orderId + "/accept", HttpMethod.POST, authed(null, ownerToken), OrderResponse.class);

        Long assignmentId = restTemplate.exchange("/assignments/open?cityId=" + cityId, HttpMethod.GET,
                authed(null, partnerTokens.get(0)), AssignmentResponse[].class).getBody()[0].id();

        ExecutorService pool = Executors.newFixedThreadPool(partnerCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (String token : partnerTokens) {
            futures.add(pool.submit(() -> {
                try {
                    startLatch.await();
                    ResponseEntity<String> response = restTemplate.exchange(
                            "/assignments/" + assignmentId + "/accept", HttpMethod.POST, authed(null, token), String.class);
                    if (response.getStatusCode() == HttpStatus.OK) {
                        successCount.incrementAndGet();
                    } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        startLatch.countDown();
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
        pool.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(partnerCount - 1);
    }
}
