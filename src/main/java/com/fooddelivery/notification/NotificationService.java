package com.fooddelivery.notification;

import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.delivery.DeliveryAssignment;
import com.fooddelivery.delivery.DeliveryAssignmentRepository;
import com.fooddelivery.delivery.DeliveryAssignmentStatus;
import com.fooddelivery.notification.dto.NotificationResponse;
import com.fooddelivery.order.Order;
import com.fooddelivery.order.OrderRepository;
import com.fooddelivery.order.OrderStatus;
import com.fooddelivery.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final OrderRepository orderRepository;
    private final DeliveryAssignmentRepository assignmentRepository;

    @Transactional
    public void notifyOrderPlaced(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        String message = "Order #" + orderId + " has been placed.";
        save(order.getCustomer(), order, message);
        save(order.getRestaurant().getOwner(), order, "New order #" + orderId + " received.");
    }

    @Transactional
    public void notifyOrderStatusChanged(Long orderId, OrderStatus oldStatus, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        String message = "Order #" + orderId + " changed from " + oldStatus + " to " + newStatus + ".";
        save(order.getCustomer(), order, message);
        save(order.getRestaurant().getOwner(), order, message);
        assignedPartner(orderId).ifPresent(partner -> save(partner, order, message));
    }

    @Transactional
    public void notifyAssignmentAccepted(Long orderId, Long partnerId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        String message = "A delivery partner has picked up order #" + orderId + ".";
        save(order.getCustomer(), order, message);
        save(order.getRestaurant().getOwner(), order, message);
    }

    public List<NotificationResponse> getMyNotifications(Long userId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> new NotFoundException("Notification not found: " + notificationId));
        notification.setRead(true);
        return toResponse(notification);
    }

    private Optional<User> assignedPartner(Long orderId) {
        return assignmentRepository.findByOrderId(orderId)
                .filter(a -> a.getStatus() == DeliveryAssignmentStatus.ACCEPTED)
                .map(DeliveryAssignment::getPartner);
    }

    private void save(User recipient, Order order, String message) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setOrder(order);
        notification.setMessage(message);
        notificationRepository.save(notification);
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getOrder().getId(),
                notification.getMessage(), notification.isRead(), notification.getCreatedAt());
    }
}
