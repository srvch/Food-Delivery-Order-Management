package com.fooddelivery.delivery;

import com.fooddelivery.city.City;
import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.ForbiddenException;
import com.fooddelivery.delivery.dto.AssignmentResponse;
import com.fooddelivery.order.Order;
import com.fooddelivery.restaurant.Restaurant;
import com.fooddelivery.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryAssignmentServiceTest {

    @Mock private DeliveryAssignmentRepository assignmentRepository;
    @Mock private DeliveryPartnerProfileRepository profileRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private DeliveryAssignmentService deliveryAssignmentService;

    private City city(Long id) {
        City c = new City();
        c.setId(id);
        return c;
    }

    private Order orderInCity(Long cityId) {
        Restaurant restaurant = new Restaurant();
        restaurant.setCity(city(cityId));
        Order order = new Order();
        order.setId(1L);
        order.setRestaurant(restaurant);
        return order;
    }

    private DeliveryAssignment openAssignment(Long cityId) {
        DeliveryAssignment assignment = new DeliveryAssignment();
        assignment.setId(50L);
        assignment.setOrder(orderInCity(cityId));
        assignment.setStatus(DeliveryAssignmentStatus.OPEN);
        return assignment;
    }

    private DeliveryPartnerProfile activeProfile(Long userId, Long cityId) {
        User user = new User();
        user.setId(userId);
        DeliveryPartnerProfile profile = new DeliveryPartnerProfile();
        profile.setUser(user);
        profile.setCity(city(cityId));
        profile.setActive(true);
        return profile;
    }

    @Test
    void acceptAssignmentSucceedsForEligiblePartner() {
        DeliveryAssignment assignment = openAssignment(1L);
        when(assignmentRepository.findById(50L)).thenReturn(Optional.of(assignment));
        User partnerUser = new User();
        partnerUser.setId(7L);
        when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(activeProfile(7L, 1L)));
        when(assignmentRepository.acceptIfOpen(50L, 7L)).thenReturn(1);
        DeliveryAssignment accepted = openAssignment(1L);
        accepted.setStatus(DeliveryAssignmentStatus.ACCEPTED);
        accepted.setPartner(partnerUser);
        when(assignmentRepository.findById(50L)).thenReturn(Optional.of(assignment), Optional.of(accepted));

        AssignmentResponse response = deliveryAssignmentService.acceptAssignment(partnerUser, 50L);

        assertThat(response.status()).isEqualTo(DeliveryAssignmentStatus.ACCEPTED);
        assertThat(response.partnerId()).isEqualTo(7L);
    }

    @Test
    void acceptAssignmentFailsWhenAlreadyTaken() {
        DeliveryAssignment assignment = openAssignment(1L);
        when(assignmentRepository.findById(50L)).thenReturn(Optional.of(assignment));
        User partnerUser = new User();
        partnerUser.setId(7L);
        when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(activeProfile(7L, 1L)));
        when(assignmentRepository.acceptIfOpen(50L, 7L)).thenReturn(0);

        assertThatThrownBy(() -> deliveryAssignmentService.acceptAssignment(partnerUser, 50L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void acceptAssignmentRejectsPartnerFromDifferentCity() {
        DeliveryAssignment assignment = openAssignment(1L);
        when(assignmentRepository.findById(50L)).thenReturn(Optional.of(assignment));
        User partnerUser = new User();
        partnerUser.setId(7L);
        when(profileRepository.findByUserId(7L)).thenReturn(Optional.of(activeProfile(7L, 2L)));

        assertThatThrownBy(() -> deliveryAssignmentService.acceptAssignment(partnerUser, 50L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void isAssignedPartnerReflectsAcceptedAssignment() {
        DeliveryAssignment assignment = openAssignment(1L);
        assignment.setStatus(DeliveryAssignmentStatus.ACCEPTED);
        User partner = new User();
        partner.setId(7L);
        assignment.setPartner(partner);
        when(assignmentRepository.findByOrderId(1L)).thenReturn(Optional.of(assignment));

        assertThat(deliveryAssignmentService.isAssignedPartner(1L, 7L)).isTrue();
        assertThat(deliveryAssignmentService.isAssignedPartner(1L, 8L)).isFalse();
    }
}
