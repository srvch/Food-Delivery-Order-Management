package com.fooddelivery.delivery;

import com.fooddelivery.city.City;
import com.fooddelivery.city.CityRepository;
import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.delivery.dto.AdminCreateDeliveryPartnerRequest;
import com.fooddelivery.delivery.dto.DeliveryPartnerResponse;
import com.fooddelivery.delivery.dto.DeliveryPartnerUpdateRequest;
import com.fooddelivery.user.Role;
import com.fooddelivery.user.User;
import com.fooddelivery.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryPartnerService {

    private final DeliveryPartnerProfileRepository profileRepository;
    private final CityRepository cityRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public DeliveryPartnerResponse createPartner(AdminCreateDeliveryPartnerRequest request) {
        City city = cityRepository.findById(request.cityId())
                .orElseThrow(() -> new NotFoundException("City not found: " + request.cityId()));
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered: " + request.email());
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.DELIVERY_PARTNER);
        user = userRepository.save(user);

        DeliveryPartnerProfile profile = new DeliveryPartnerProfile();
        profile.setUser(user);
        profile.setCity(city);
        profile.setActive(true);
        return toResponse(profileRepository.save(profile));
    }

    @Transactional
    public DeliveryPartnerProfile registerProfileForSelf(User user, Long cityId) {
        City city = cityRepository.findById(cityId)
                .orElseThrow(() -> new NotFoundException("City not found: " + cityId));
        DeliveryPartnerProfile profile = new DeliveryPartnerProfile();
        profile.setUser(user);
        profile.setCity(city);
        profile.setActive(false);
        return profileRepository.save(profile);
    }

    public List<DeliveryPartnerResponse> listPartners() {
        return profileRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public DeliveryPartnerResponse updatePartner(Long profileId, DeliveryPartnerUpdateRequest request) {
        DeliveryPartnerProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new NotFoundException("Delivery partner profile not found: " + profileId));
        City city = cityRepository.findById(request.cityId())
                .orElseThrow(() -> new NotFoundException("City not found: " + request.cityId()));
        profile.setCity(city);
        profile.setActive(request.active());
        return toResponse(profile);
    }

    private DeliveryPartnerResponse toResponse(DeliveryPartnerProfile profile) {
        return new DeliveryPartnerResponse(profile.getId(), profile.getUser().getId(),
                profile.getUser().getEmail(), profile.getCity().getId(), profile.getCity().getName(),
                profile.isActive());
    }
}
