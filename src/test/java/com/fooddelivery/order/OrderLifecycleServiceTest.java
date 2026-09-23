package com.fooddelivery.order;

import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.ForbiddenException;
import com.fooddelivery.order.dto.OrderResponse;
import com.fooddelivery.restaurant.MenuItem;
import com.fooddelivery.restaurant.Restaurant;
import com.fooddelivery.user.Role;
import com.fooddelivery.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderLifecycleServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private com.fooddelivery.restaurant.MenuItemRepository menuItemRepository;
    @Mock private com.fooddelivery.restaurant.RestaurantService restaurantService;
    @Mock private PaymentGateway paymentGateway;
    @Mock private com.fooddelivery.delivery.DeliveryAssignmentService deliveryAssignmentService;
    @InjectMocks private OrderService orderService;

    private Order orderWithOwner(Long ownerId, OrderStatus status) {
        User owner = new User();
        owner.setId(ownerId);
        Restaurant restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setOwner(owner);
        Order order = new Order();
        order.setId(5L);
        order.setRestaurant(restaurant);
        order.setStatus(status);
        return order;
    }

    @Test
    void acceptOrderTransitionsPlacedToAccepted() {
        Order order = orderWithOwner(1L, OrderStatus.PLACED);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(5L)).thenReturn(List.of());

        OrderResponse response = orderService.acceptOrder(1L, 5L);

        assertThat(response.status()).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    void acceptOrderRejectsNonOwner() {
        Order order = orderWithOwner(1L, OrderStatus.PLACED);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.acceptOrder(2L, 5L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void acceptOrderRejectsIllegalTransition() {
        Order order = orderWithOwner(1L, OrderStatus.DELIVERED);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.acceptOrder(1L, 5L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectOrderRestoresStock() {
        Order order = orderWithOwner(1L, OrderStatus.PLACED);
        MenuItem menuItem = new MenuItem();
        menuItem.setId(10L);
        menuItem.setStockQuantity(2);
        OrderItem orderItem = new OrderItem();
        orderItem.setMenuItem(menuItem);
        orderItem.setQuantity(3);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(5L)).thenReturn(List.of(orderItem));

        OrderResponse response = orderService.rejectOrder(1L, 5L);

        assertThat(response.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(menuItem.getStockQuantity()).isEqualTo(5);
    }

    @Test
    void updateStatusRejectsNonOwnerCallerForPreparing() {
        Order order = orderWithOwner(1L, OrderStatus.ACCEPTED);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        User deliveryPartner = new User();
        deliveryPartner.setId(99L);
        deliveryPartner.setRole(Role.DELIVERY_PARTNER);

        assertThatThrownBy(() -> orderService.updateStatus(deliveryPartner, 5L, OrderStatus.PREPARING))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateStatusOwnerCanMarkPreparing() {
        Order order = orderWithOwner(1L, OrderStatus.ACCEPTED);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(5L)).thenReturn(List.of());
        User owner = new User();
        owner.setId(1L);
        owner.setRole(Role.RESTAURANT_OWNER);

        OrderResponse response = orderService.updateStatus(owner, 5L, OrderStatus.PREPARING);

        assertThat(response.status()).isEqualTo(OrderStatus.PREPARING);
    }
}
