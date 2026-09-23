package com.fooddelivery.delivery;

import com.fooddelivery.delivery.dto.AssignmentResponse;
import com.fooddelivery.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/assignments")
@PreAuthorize("hasRole('DELIVERY_PARTNER')")
@RequiredArgsConstructor
public class DeliveryAssignmentController {

    private final DeliveryAssignmentService deliveryAssignmentService;

    @GetMapping("/open")
    public List<AssignmentResponse> listOpen(@RequestParam Long cityId) {
        return deliveryAssignmentService.listOpenAssignments(cityId);
    }

    @PostMapping("/{id}/accept")
    public AssignmentResponse accept(@PathVariable Long id, @AuthenticationPrincipal User partner) {
        return deliveryAssignmentService.acceptAssignment(partner, id);
    }
}
