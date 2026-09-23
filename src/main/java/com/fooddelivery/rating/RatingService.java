package com.fooddelivery.rating;

import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.ForbiddenException;
import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.order.Order;
import com.fooddelivery.order.OrderRepository;
import com.fooddelivery.order.OrderStatus;
import com.fooddelivery.rating.dto.RateRequest;
import com.fooddelivery.rating.dto.RatingResponse;
import com.fooddelivery.restaurant.Restaurant;
import com.fooddelivery.restaurant.RestaurantRepository;
import com.fooddelivery.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;
    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;

    @Transactional
    public RatingResponse rate(User customer, Long orderId, RateRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new ForbiddenException("You may only rate your own orders");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new ConflictException("Only delivered orders can be rated");
        }
        if (ratingRepository.findByOrderId(orderId).isPresent()) {
            throw new ConflictException("Order already rated: " + orderId);
        }

        Rating rating = new Rating();
        rating.setOrder(order);
        rating.setRater(customer);
        rating.setScore(request.score());
        rating.setReview(request.review());
        rating = ratingRepository.save(rating);

        Restaurant restaurant = order.getRestaurant();
        int newCount = restaurant.getRatingCount() + 1;
        BigDecimal totalScore = restaurant.getAvgRating()
                .multiply(BigDecimal.valueOf(restaurant.getRatingCount()))
                .add(BigDecimal.valueOf(request.score()));
        restaurant.setAvgRating(totalScore.divide(BigDecimal.valueOf(newCount), 2, RoundingMode.HALF_UP));
        restaurant.setRatingCount(newCount);
        restaurantRepository.save(restaurant);

        return new RatingResponse(rating.getId(), order.getId(), restaurant.getId(),
                rating.getScore(), rating.getReview(), rating.getCreatedAt());
    }
}
