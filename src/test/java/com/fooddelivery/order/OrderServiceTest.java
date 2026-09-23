package com.fooddelivery.order;

import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.PaymentDeclinedException;
import com.fooddelivery.order.dto.OrderResponse;
import com.fooddelivery.order.dto.PlaceOrderRequest;
import com.fooddelivery.restaurant.MenuItem;
import com.fooddelivery.restaurant.MenuItemRepository;
import com.fooddelivery.restaurant.Restaurant;
import com.fooddelivery.restaurant.RestaurantService;
import com.fooddelivery.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private MenuItemRepository menuItemRepository;
    @Mock private RestaurantService restaurantService;
    @Mock private PaymentGateway paymentGateway;
    @InjectMocks private OrderService orderService;

    private MenuItem menuItem(Long id, Restaurant restaurant, BigDecimal price, int stock) {
        MenuItem item = new MenuItem();
        item.setId(id);
        item.setRestaurant(restaurant);
        item.setPrice(price);
        item.setStockQuantity(stock);
        item.setAvailable(true);
        return item;
    }

    @Test
    void placeOrderDecrementsStockAndChargesPayment() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(1L);
        when(restaurantService.getRestaurantEntity(1L)).thenReturn(restaurant);
        MenuItem item = menuItem(10L, restaurant, new BigDecimal("50.00"), 5);
        when(menuItemRepository.findAllByIdInForUpdate(List.of(10L))).thenReturn(List.of(item));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(100L);
            return o;
        });

        User customer = new User();
        customer.setId(1L);
        var request = new PlaceOrderRequest(1L, List.of(new PlaceOrderRequest.Item(10L, 2)));

        OrderResponse response = orderService.placeOrder(customer, request);

        assertThat(response.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(response.totalAmount()).isEqualByComparingTo("100.00");
        assertThat(item.getStockQuantity()).isEqualTo(3);
        verify(paymentGateway).charge(new BigDecimal("100.00"));
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void placeOrderRejectsInsufficientStockWithoutCharging() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(1L);
        when(restaurantService.getRestaurantEntity(1L)).thenReturn(restaurant);
        MenuItem item = menuItem(10L, restaurant, new BigDecimal("50.00"), 1);
        when(menuItemRepository.findAllByIdInForUpdate(List.of(10L))).thenReturn(List.of(item));

        User customer = new User();
        customer.setId(1L);
        var request = new PlaceOrderRequest(1L, List.of(new PlaceOrderRequest.Item(10L, 2)));

        assertThatThrownBy(() -> orderService.placeOrder(customer, request))
                .isInstanceOf(ConflictException.class);

        verifyNoInteractions(paymentGateway);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrderPropagatesPaymentDecline() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(1L);
        when(restaurantService.getRestaurantEntity(1L)).thenReturn(restaurant);
        MenuItem item = menuItem(10L, restaurant, new BigDecimal("50.00"), 5);
        when(menuItemRepository.findAllByIdInForUpdate(List.of(10L))).thenReturn(List.of(item));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new PaymentDeclinedException("card declined")).when(paymentGateway).charge(any());

        User customer = new User();
        customer.setId(1L);
        var request = new PlaceOrderRequest(1L, List.of(new PlaceOrderRequest.Item(10L, 1)));

        assertThatThrownBy(() -> orderService.placeOrder(customer, request))
                .isInstanceOf(PaymentDeclinedException.class);

        verify(paymentRepository, never()).save(any());
    }
}
