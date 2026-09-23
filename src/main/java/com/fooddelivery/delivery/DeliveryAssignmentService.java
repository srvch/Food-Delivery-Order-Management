package com.fooddelivery.delivery;

import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.ForbiddenException;
import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.delivery.dto.AssignmentResponse;
import com.fooddelivery.delivery.event.AssignmentAcceptedEvent;
import com.fooddelivery.order.Order;
import com.fooddelivery.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryAssignmentService {

    private final DeliveryAssignmentRepository assignmentRepository;
    private final DeliveryPartnerProfileRepository profileRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public DeliveryAssignment createOpenAssignment(Order order) {
        DeliveryAssignment assignment = new DeliveryAssignment();
        assignment.setOrder(order);
        assignment.setStatus(DeliveryAssignmentStatus.OPEN);
        return assignmentRepository.save(assignment);
    }

    @Transactional
    public AssignmentResponse acceptAssignment(User partnerUser, Long assignmentId) {
        DeliveryAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("Assignment not found: " + assignmentId));
        DeliveryPartnerProfile profile = profileRepository.findByUserId(partnerUser.getId())
                .orElseThrow(() -> new ForbiddenException("No delivery partner profile for this user"));
        if (!profile.isActive()) {
            throw new ForbiddenException("Delivery partner is not active");
        }
        Long assignmentCityId = assignment.getOrder().getRestaurant().getCity().getId();
        if (!profile.getCity().getId().equals(assignmentCityId)) {
            throw new ForbiddenException("Delivery partner is not eligible for this city");
        }

        int updated = assignmentRepository.acceptIfOpen(assignmentId, partnerUser.getId());
        if (updated == 0) {
            throw new ConflictException("Assignment already taken: " + assignmentId);
        }

        DeliveryAssignment accepted = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("Assignment not found: " + assignmentId));
        eventPublisher.publishEvent(new AssignmentAcceptedEvent(accepted.getOrder().getId(), partnerUser.getId()));
        return toResponse(accepted);
    }

    public List<AssignmentResponse> listOpenAssignments(Long cityId) {
        return assignmentRepository.findOpenByCityId(cityId).stream().map(this::toResponse).toList();
    }

    public boolean isAssignedPartner(Long orderId, Long partnerId) {
        return assignmentRepository.findByOrderId(orderId)
                .map(a -> a.getStatus() == DeliveryAssignmentStatus.ACCEPTED
                        && a.getPartner() != null
                        && a.getPartner().getId().equals(partnerId))
                .orElse(false);
    }

    private AssignmentResponse toResponse(DeliveryAssignment assignment) {
        return new AssignmentResponse(assignment.getId(), assignment.getOrder().getId(), assignment.getStatus(),
                assignment.getPartner() != null ? assignment.getPartner().getId() : null,
                assignment.getOfferedAt(), assignment.getAcceptedAt());
    }
}
