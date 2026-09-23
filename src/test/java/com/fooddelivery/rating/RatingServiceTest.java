package com.fooddelivery.rating;

import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.ForbiddenException;
import com.fooddelivery.order.Order;
import com.fooddelivery.order.OrderRepository;
import com.fooddelivery.order.OrderStatus;
import com.fooddelivery.rating.dto.RateRequest;
import com.fooddelivery.rating.dto.RatingResponse;
import com.fooddelivery.restaurant.Restaurant;
import com.fooddelivery.restaurant.RestaurantRepository;
import com.fooddelivery.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock private RatingRepository ratingRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @InjectMocks private RatingService ratingService;

    private Order deliveredOrderFor(Long customerId, Restaurant restaurant) {
        User customer = new User();
        customer.setId(customerId);
        Order order = new Order();
        order.setId(1L);
        order.setCustomer(customer);
        order.setRestaurant(restaurant);
        order.setStatus(OrderStatus.DELIVERED);
        return order;
    }

    @Test
    void ratingUpdatesRunningAverage() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        restaurant.setAvgRating(BigDecimal.ZERO);
        restaurant.setRatingCount(0);
        Order order = deliveredOrderFor(1L, restaurant);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(ratingRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(ratingRepository.save(any(Rating.class))).thenAnswer(inv -> inv.getArgument(0));
        User customer = new User();
        customer.setId(1L);

        RatingResponse response = ratingService.rate(customer, 1L, new RateRequest(4, "Great food"));

        assertThat(response.score()).isEqualTo(4);
        assertThat(restaurant.getAvgRating()).isEqualByComparingTo("4.00");
        assertThat(restaurant.getRatingCount()).isEqualTo(1);
    }

    @Test
    void ratingRejectsNonOwningCustomer() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        Order order = deliveredOrderFor(1L, restaurant);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        User otherCustomer = new User();
        otherCustomer.setId(2L);

        assertThatThrownBy(() -> ratingService.rate(otherCustomer, 1L, new RateRequest(5, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void ratingRejectsNonDeliveredOrder() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        Order order = deliveredOrderFor(1L, restaurant);
        order.setStatus(OrderStatus.PREPARING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        User customer = new User();
        customer.setId(1L);

        assertThatThrownBy(() -> ratingService.rate(customer, 1L, new RateRequest(5, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void ratingRejectsDuplicateForSameOrder() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        Order order = deliveredOrderFor(1L, restaurant);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(ratingRepository.findByOrderId(1L)).thenReturn(Optional.of(new Rating()));
        User customer = new User();
        customer.setId(1L);

        assertThatThrownBy(() -> ratingService.rate(customer, 1L, new RateRequest(5, null)))
                .isInstanceOf(ConflictException.class);
    }
}
