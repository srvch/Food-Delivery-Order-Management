package com.fooddelivery.order;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.AbstractIntegrationTest;
import com.fooddelivery.common.exception.PaymentDeclinedException;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.*;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

class OrderPlacementIT extends AbstractIntegrationTest {

    @MockBean
    private PaymentGateway paymentGateway;

    private String adminToken() {
        var login = new LoginRequest("admin@fooddelivery.com", "Admin@123");
        return restTemplate.postForEntity("/auth/login", login, AuthResponse.class).getBody().token();
    }

    private <T> HttpEntity<T> authed(T body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private record Setup(Long restaurantId, Long menuItemId, String customerToken) {
    }

    private Setup setUpRestaurantWithStock(int stock, String suffix) {
        String admin = adminToken();
        Long cityId = restTemplate.exchange("/cities", HttpMethod.POST,
                authed(new CityRequest("City" + suffix, true), admin), CityResponse.class).getBody().id();
        var createReq = new AdminCreateRestaurantRequest(cityId, "Rest" + suffix, "Addr",
                "owner" + suffix + "@example.com", "password123");
        Long restaurantId = restTemplate.exchange("/admin/restaurants", HttpMethod.POST,
                authed(createReq, admin), RestaurantResponse.class).getBody().id();
        String ownerToken = restTemplate.postForEntity("/auth/login",
                new LoginRequest("owner" + suffix + "@example.com", "password123"), AuthResponse.class).getBody().token();
        var menuReq = new MenuItemRequest("Item" + suffix, new BigDecimal("25.00"), stock, true);
        Long menuItemId = restTemplate.exchange("/restaurants/" + restaurantId + "/menu-items", HttpMethod.POST,
                authed(menuReq, ownerToken), MenuItemResponse.class).getBody().id();
        var register = new RegisterRequest("cust" + suffix + "@example.com", "password123", Role.CUSTOMER, null);
        String customerToken = restTemplate.postForEntity("/auth/register", register, AuthResponse.class).getBody().token();
        return new Setup(restaurantId, menuItemId, customerToken);
    }

    @Test
    void placingOrderDecrementsStockAndCreatesPayment() {
        Setup setup = setUpRestaurantWithStock(10, "A");
        var request = new PlaceOrderRequest(setup.restaurantId(), java.util.List.of(new PlaceOrderRequest.Item(setup.menuItemId(), 3)));

        ResponseEntity<OrderResponse> response = restTemplate.exchange("/orders", HttpMethod.POST,
                authed(request, setup.customerToken()), OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.PLACED);
        assertThat(response.getBody().totalAmount()).isEqualByComparingTo("75.00");
    }

    @Test
    void placingOrderExceedingStockReturns409() {
        Setup setup = setUpRestaurantWithStock(2, "B");
        var request = new PlaceOrderRequest(setup.restaurantId(), java.util.List.of(new PlaceOrderRequest.Item(setup.menuItemId(), 5)));

        ResponseEntity<String> response = restTemplate.exchange("/orders", HttpMethod.POST,
                authed(request, setup.customerToken()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void declinedPaymentRollsBackStockDecrement() {
        Setup setup = setUpRestaurantWithStock(10, "C");
        doThrow(new PaymentDeclinedException("simulated decline")).when(paymentGateway).charge(any());
        var request = new PlaceOrderRequest(setup.restaurantId(), java.util.List.of(new PlaceOrderRequest.Item(setup.menuItemId(), 4)));

        ResponseEntity<String> response = restTemplate.exchange("/orders", HttpMethod.POST,
                authed(request, setup.customerToken()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);

        ResponseEntity<MenuItemResponse[]> menu = restTemplate.exchange(
                "/restaurants/" + setup.restaurantId() + "/menu", HttpMethod.GET,
                authed(null, setup.customerToken()), MenuItemResponse[].class);
        assertThat(menu.getBody()[0].stockQuantity()).isEqualTo(10);
    }

    @Test
    void customerCanViewOwnOrderButNotAnothersOrder() {
        Setup setup = setUpRestaurantWithStock(10, "D");
        var request = new PlaceOrderRequest(setup.restaurantId(), java.util.List.of(new PlaceOrderRequest.Item(setup.menuItemId(), 1)));
        Long orderId = restTemplate.exchange("/orders", HttpMethod.POST,
                authed(request, setup.customerToken()), OrderResponse.class).getBody().id();

        ResponseEntity<OrderResponse> own = restTemplate.exchange("/orders/" + orderId, HttpMethod.GET,
                authed(null, setup.customerToken()), OrderResponse.class);
        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.OK);

        String otherCustomerToken = restTemplate.postForEntity("/auth/register",
                new RegisterRequest("other-customer-d@example.com", "password123", Role.CUSTOMER, null),
                AuthResponse.class).getBody().token();

        ResponseEntity<String> forbidden = restTemplate.exchange("/orders/" + orderId, HttpMethod.GET,
                authed(null, otherCustomerToken), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
