package com.fooddelivery.order;

import com.fooddelivery.common.exception.BadRequestException;
import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.ForbiddenException;
import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.order.dto.OrderItemLine;
import com.fooddelivery.order.dto.OrderResponse;
import com.fooddelivery.order.dto.PlaceOrderRequest;
import com.fooddelivery.restaurant.MenuItem;
import com.fooddelivery.restaurant.MenuItemRepository;
import com.fooddelivery.restaurant.Restaurant;
import com.fooddelivery.restaurant.RestaurantService;
import com.fooddelivery.user.Role;
import com.fooddelivery.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public OrderResponse getOrder(User customer, Long id) {
        Order order = getOrderEntity(id);
        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new ForbiddenException("You may only view your own orders");
        }
        return toResponse(order, orderItemRepository.findByOrderId(id));
    }

    public List<OrderResponse> listCustomerOrders(Long customerId) {
        return orderRepository.findByCustomerId(customerId).stream()
                .map(order -> toResponse(order, orderItemRepository.findByOrderId(order.getId())))
                .toList();
    }

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            OrderStatus.PLACED, Set.of(OrderStatus.ACCEPTED, OrderStatus.REJECTED),
            OrderStatus.ACCEPTED, Set.of(OrderStatus.PREPARING),
            OrderStatus.PREPARING, Set.of(OrderStatus.OUT_FOR_DELIVERY),
            OrderStatus.OUT_FOR_DELIVERY, Set.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, Set.of(),
            OrderStatus.REJECTED, Set.of()
    );

    private void transition(Order order, OrderStatus target) {
        Set<OrderStatus> allowed = TRANSITIONS.getOrDefault(order.getStatus(), Set.of());
        if (!allowed.contains(target)) {
            throw new ConflictException(
                    "Cannot transition order " + order.getId() + " from " + order.getStatus() + " to " + target);
        }
        order.setStatus(target);
        order.setUpdatedAt(java.time.Instant.now());
    }

    private void verifyRestaurantOwnership(Order order, Long ownerId) {
        if (!order.getRestaurant().getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("You do not own the restaurant for order " + order.getId());
        }
    }

    @Transactional
    public OrderResponse acceptOrder(Long ownerId, Long orderId) {
        Order order = getOrderEntity(orderId);
        verifyRestaurantOwnership(order, ownerId);
        transition(order, OrderStatus.ACCEPTED);
        return toResponse(order, orderItemRepository.findByOrderId(orderId));
    }

    @Transactional
    public OrderResponse rejectOrder(Long ownerId, Long orderId) {
        Order order = getOrderEntity(orderId);
        verifyRestaurantOwnership(order, ownerId);
        transition(order, OrderStatus.REJECTED);
        for (OrderItem orderItem : orderItemRepository.findByOrderId(orderId)) {
            MenuItem item = orderItem.getMenuItem();
            item.setStockQuantity(item.getStockQuantity() + orderItem.getQuantity());
        }
        return toResponse(order, orderItemRepository.findByOrderId(orderId));
    }

    @Transactional
    public OrderResponse updateStatus(User caller, Long orderId, OrderStatus target) {
        Order order = getOrderEntity(orderId);
        if (caller.getRole() == Role.RESTAURANT_OWNER) {
            verifyRestaurantOwnership(order, caller.getId());
            if (target != OrderStatus.PREPARING) {
                throw new ForbiddenException("Restaurant owners may only mark orders PREPARING via this endpoint");
            }
        } else {
            throw new ForbiddenException("You are not authorized to update this order's status");
        }
        transition(order, target);
        return toResponse(order, orderItemRepository.findByOrderId(orderId));
    }

    public List<OrderResponse> listRestaurantOrders(Long ownerId, Long restaurantId) {
        restaurantService.getOwnedRestaurantEntity(restaurantId, ownerId);
        return orderRepository.findByRestaurantId(restaurantId).stream()
                .map(order -> toResponse(order, orderItemRepository.findByOrderId(order.getId())))
                .toList();
    }

    private OrderResponse toResponse(Order order, List<OrderItem> items) {
        List<OrderItemLine> lines = items.stream()
                .map(oi -> new OrderItemLine(oi.getMenuItem().getId(), oi.getMenuItem().getName(),
                        oi.getQuantity(), oi.getUnitPriceAtOrder()))
                .toList();
        Long customerId = order.getCustomer() == null ? null : order.getCustomer().getId();
        return new OrderResponse(order.getId(), customerId, order.getRestaurant().getId(),
                order.getStatus(), order.getTotalAmount(), lines, order.getCreatedAt(), order.getUpdatedAt());
    }
}
