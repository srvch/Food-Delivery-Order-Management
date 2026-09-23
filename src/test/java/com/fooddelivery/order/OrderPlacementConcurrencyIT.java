package com.fooddelivery.order;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.AbstractIntegrationTest;
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

class OrderPlacementConcurrencyIT extends AbstractIntegrationTest {

    private <T> HttpEntity<T> authed(T body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void concurrentOrdersNeverOversellLimitedStock() throws InterruptedException {
        String admin = restTemplate.postForEntity("/auth/login",
                new LoginRequest("admin@fooddelivery.com", "Admin@123"), AuthResponse.class).getBody().token();
        Long cityId = restTemplate.exchange("/cities", HttpMethod.POST,
                authed(new CityRequest("ConcurrencyCity", true), admin), CityResponse.class).getBody().id();
        var createReq = new AdminCreateRestaurantRequest(cityId, "ConcurrencyRest", "Addr",
                "concurrency-owner@example.com", "password123");
        Long restaurantId = restTemplate.exchange("/admin/restaurants", HttpMethod.POST,
                authed(createReq, admin), RestaurantResponse.class).getBody().id();
        String ownerToken = restTemplate.postForEntity("/auth/login",
                new LoginRequest("concurrency-owner@example.com", "password123"), AuthResponse.class).getBody().token();

        int stock = 5;
        int concurrentOrders = 15;
        Long menuItemId = restTemplate.exchange("/restaurants/" + restaurantId + "/menu-items", HttpMethod.POST,
                authed(new MenuItemRequest("LimitedItem", new BigDecimal("10.00"), stock, true), ownerToken),
                MenuItemResponse.class).getBody().id();

        List<String> customerTokens = new ArrayList<>();
        for (int i = 0; i < concurrentOrders; i++) {
            var register = new RegisterRequest("racer" + i + "@example.com", "password123", Role.CUSTOMER, null);
            customerTokens.add(restTemplate.postForEntity("/auth/register", register, AuthResponse.class).getBody().token());
        }

        ExecutorService pool = Executors.newFixedThreadPool(concurrentOrders);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (String token : customerTokens) {
            futures.add(pool.submit(() -> {
                try {
                    startLatch.await();
                    var request = new PlaceOrderRequest(restaurantId, List.of(new PlaceOrderRequest.Item(menuItemId, 1)));
                    ResponseEntity<String> response = restTemplate.exchange(
                            "/orders", HttpMethod.POST, authed(request, token), String.class);
                    if (response.getStatusCode() == HttpStatus.CREATED) {
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

        assertThat(successCount.get()).isEqualTo(stock);
        assertThat(conflictCount.get()).isEqualTo(concurrentOrders - stock);

        ResponseEntity<MenuItemResponse[]> menu = restTemplate.exchange(
                "/restaurants/" + restaurantId + "/menu", HttpMethod.GET,
                authed(null, ownerToken), MenuItemResponse[].class);
        assertThat(menu.getBody()[0].stockQuantity()).isEqualTo(0);
    }
}
