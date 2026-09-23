package com.fooddelivery.delivery;

import com.fooddelivery.city.City;
import com.fooddelivery.city.CityRepository;
import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.delivery.dto.AdminCreateDeliveryPartnerRequest;
import com.fooddelivery.delivery.dto.DeliveryPartnerResponse;
import com.fooddelivery.delivery.dto.DeliveryPartnerUpdateRequest;
import com.fooddelivery.user.Role;
import com.fooddelivery.user.User;
import com.fooddelivery.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryPartnerServiceTest {

    @Mock private DeliveryPartnerProfileRepository profileRepository;
    @Mock private CityRepository cityRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private DeliveryPartnerService deliveryPartnerService;

    @Test
    void adminCreatePartnerActivatesImmediately() {
        City city = new City();
        city.setId(1L);
        city.setName("Pune");
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city));
        when(userRepository.existsByEmail("partner1@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(20L);
            return u;
        });
        when(profileRepository.save(any(DeliveryPartnerProfile.class))).thenAnswer(inv -> {
            DeliveryPartnerProfile p = inv.getArgument(0);
            p.setId(200L);
            return p;
        });

        var request = new AdminCreateDeliveryPartnerRequest(1L, "partner1@example.com", "password123");
        DeliveryPartnerResponse response = deliveryPartnerService.createPartner(request);

        assertThat(response.active()).isTrue();
        assertThat(response.cityId()).isEqualTo(1L);
    }

    @Test
    void registerProfileForSelfStartsInactive() {
        City city = new City();
        city.setId(2L);
        when(cityRepository.findById(2L)).thenReturn(Optional.of(city));
        User user = new User();
        user.setId(30L);
        when(profileRepository.save(any(DeliveryPartnerProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        DeliveryPartnerProfile profile = deliveryPartnerService.registerProfileForSelf(user, 2L);

        assertThat(profile.isActive()).isFalse();
        assertThat(profile.getCity()).isEqualTo(city);
    }

    @Test
    void registerProfileForSelfRejectsUnknownCity() {
        when(cityRepository.findById(99L)).thenReturn(Optional.empty());
        User user = new User();
        user.setId(31L);

        assertThatThrownBy(() -> deliveryPartnerService.registerProfileForSelf(user, 99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatePartnerTogglesActive() {
        City city = new City();
        city.setId(1L);
        User user = new User();
        user.setId(7L);
        user.setEmail("partner@example.com");
        DeliveryPartnerProfile profile = new DeliveryPartnerProfile();
        profile.setId(200L);
        profile.setUser(user);
        profile.setCity(city);
        profile.setActive(false);
        when(profileRepository.findById(200L)).thenReturn(Optional.of(profile));
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city));

        var request = new DeliveryPartnerUpdateRequest(1L, true);
        DeliveryPartnerResponse response = deliveryPartnerService.updatePartner(200L, request);

        assertThat(response.active()).isTrue();
    }
}
