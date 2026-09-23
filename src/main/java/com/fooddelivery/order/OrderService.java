package com.fooddelivery.order;

import com.fooddelivery.common.exception.BadRequestException;
import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.order.dto.OrderItemLine;
import com.fooddelivery.order.dto.OrderResponse;
import com.fooddelivery.order.dto.PlaceOrderRequest;
import com.fooddelivery.restaurant.MenuItem;
import com.fooddelivery.restaurant.MenuItemRepository;
import com.fooddelivery.restaurant.Restaurant;
import com.fooddelivery.restaurant.RestaurantService;
import com.fooddelivery.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final MenuItemRepository menuItemRepository;
    private final RestaurantService restaurantService;
    private final PaymentGateway paymentGateway;

    @Transactional
    public OrderResponse placeOrder(User customer, PlaceOrderRequest request) {
        Restaurant restaurant = restaurantService.getRestaurantEntity(request.restaurantId());

        List<Long> ids = request.items().stream().map(PlaceOrderRequest.Item::menuItemId).sorted().toList();
        List<MenuItem> lockedItems = menuItemRepository.findAllByIdInForUpdate(ids);
        Map<Long, MenuItem> itemsById = new HashMap<>();
        for (MenuItem item : lockedItems) {
            itemsById.put(item.getId(), item);
        }

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> orderItems = new java.util.ArrayList<>();
        for (PlaceOrderRequest.Item requested : request.items()) {
            MenuItem item = itemsById.get(requested.menuItemId());
            if (item == null) {
                throw new NotFoundException("Menu item not found: " + requested.menuItemId());
            }
            if (!item.getRestaurant().getId().equals(restaurant.getId())) {
                throw new BadRequestException("Menu item " + item.getId() + " does not belong to restaurant " + restaurant.getId());
            }
            if (!item.isAvailable() || item.getStockQuantity() < requested.quantity()) {
                throw new ConflictException("Insufficient stock for menu item: " + item.getId());
            }
            item.setStockQuantity(item.getStockQuantity() - requested.quantity());

            OrderItem orderItem = new OrderItem();
            orderItem.setMenuItem(item);
            orderItem.setQuantity(requested.quantity());
            orderItem.setUnitPriceAtOrder(item.getPrice());
            orderItems.add(orderItem);

            total = total.add(item.getPrice().multiply(BigDecimal.valueOf(requested.quantity())));
        }

        Order order = new Order();
        order.setCustomer(customer);
        order.setRestaurant(restaurant);
        order.setStatus(OrderStatus.PLACED);
        order.setTotalAmount(total);
        order = orderRepository.save(order);

        for (OrderItem orderItem : orderItems) {
            orderItem.setOrder(order);
        }
        orderItemRepository.saveAll(orderItems);

        paymentGateway.charge(total);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(total);
        paymentRepository.save(payment);

        return toResponse(order, orderItems);
    }

    public Order getOrderEntity(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found: " + id));
    }

    public OrderResponse getOrder(Long id) {
        Order order = getOrderEntity(id);
        return toResponse(order, orderItemRepository.findByOrderId(id));
    }

    public List<OrderResponse> listCustomerOrders(Long customerId) {
        return orderRepository.findByCustomerId(customerId).stream()
                .map(order -> toResponse(order, orderItemRepository.findByOrderId(order.getId())))
                .toList();
    }

    private OrderResponse toResponse(Order order, List<OrderItem> items) {
        List<OrderItemLine> lines = items.stream()
                .map(oi -> new OrderItemLine(oi.getMenuItem().getId(), oi.getMenuItem().getName(),
                        oi.getQuantity(), oi.getUnitPriceAtOrder()))
                .toList();
        return new OrderResponse(order.getId(), order.getCustomer().getId(), order.getRestaurant().getId(),
                order.getStatus(), order.getTotalAmount(), lines, order.getCreatedAt(), order.getUpdatedAt());
    }
}
