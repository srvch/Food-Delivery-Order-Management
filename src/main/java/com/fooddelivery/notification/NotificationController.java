package com.fooddelivery.notification;

import com.fooddelivery.notification.dto.NotificationResponse;
import com.fooddelivery.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> myNotifications(@AuthenticationPrincipal User user) {
        return notificationService.getMyNotifications(user.getId());
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return notificationService.markRead(user.getId(), id);
    }
}
