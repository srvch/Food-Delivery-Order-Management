package com.fooddelivery.notification;

import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.delivery.DeliveryAssignmentRepository;
import com.fooddelivery.order.Order;
import com.fooddelivery.order.OrderRepository;
import com.fooddelivery.order.OrderStatus;
import com.fooddelivery.restaurant.Restaurant;
import com.fooddelivery.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private DeliveryAssignmentRepository assignmentRepository;
    @InjectMocks private NotificationService notificationService;

    private Order orderWithParties(Long customerId, Long ownerId) {
        User customer = new User();
        customer.setId(customerId);
        User owner = new User();
        owner.setId(ownerId);
        Restaurant restaurant = new Restaurant();
        restaurant.setOwner(owner);
        Order order = new Order();
        order.setId(1L);
        order.setCustomer(customer);
        order.setRestaurant(restaurant);
        return order;
    }

    @Test
    void notifyOrderPlacedNotifiesCustomerAndOwner() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(orderWithParties(10L, 20L)));

        notificationService.notifyOrderPlaced(1L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        List<Long> recipientIds = captor.getAllValues().stream().map(n -> n.getRecipient().getId()).toList();
        assertThat(recipientIds).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    void notifyOrderStatusChangedIncludesAssignedPartnerWhenPresent() {
        Order order = orderWithParties(10L, 20L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        com.fooddelivery.delivery.DeliveryAssignment assignment = new com.fooddelivery.delivery.DeliveryAssignment();
        assignment.setStatus(com.fooddelivery.delivery.DeliveryAssignmentStatus.ACCEPTED);
        User partner = new User();
        partner.setId(30L);
        assignment.setPartner(partner);
        when(assignmentRepository.findByOrderId(1L)).thenReturn(Optional.of(assignment));

        notificationService.notifyOrderStatusChanged(1L, OrderStatus.PREPARING, OrderStatus.OUT_FOR_DELIVERY);

        verify(notificationRepository, times(3)).save(any(Notification.class));
    }

    @Test
    void markReadRejectsWrongRecipient() {
        when(notificationRepository.findByIdAndRecipientId(5L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(99L, 5L))
                .isInstanceOf(NotFoundException.class);
    }
}
