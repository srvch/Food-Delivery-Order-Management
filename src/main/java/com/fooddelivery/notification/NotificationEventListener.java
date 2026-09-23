package com.fooddelivery.notification;

import com.fooddelivery.delivery.event.AssignmentAcceptedEvent;
import com.fooddelivery.order.event.OrderPlacedEvent;
import com.fooddelivery.order.event.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        notificationService.notifyOrderPlaced(event.orderId());
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        notificationService.notifyOrderStatusChanged(event.orderId(), event.oldStatus(), event.newStatus());
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAssignmentAccepted(AssignmentAcceptedEvent event) {
        notificationService.notifyAssignmentAccepted(event.orderId(), event.partnerId());
    }
}
