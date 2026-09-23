package com.fooddelivery.user;

import com.fooddelivery.common.exception.BadRequestException;
import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.ForbiddenException;
import com.fooddelivery.common.security.JwtService;
import com.fooddelivery.delivery.DeliveryPartnerService;
import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.LoginRequest;
import com.fooddelivery.user.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final DeliveryPartnerService deliveryPartnerService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.role() != Role.CUSTOMER && request.role() != Role.DELIVERY_PARTNER) {
            throw new ForbiddenException(
                    "Self-registration is only allowed for CUSTOMER or DELIVERY_PARTNER roles");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered: " + request.email());
        }
        if (request.role() == Role.DELIVERY_PARTNER && request.cityId() == null) {
            throw new BadRequestException("cityId is required when registering as a DELIVERY_PARTNER");
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user = userRepository.save(user);

        if (request.role() == Role.DELIVERY_PARTNER) {
            deliveryPartnerService.registerProfileForSelf(user, request.cityId());
        }

        return new AuthResponse(jwtService.generateToken(user), user.getId(), user.getRole());
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalStateException("Authenticated user vanished: " + request.email()));
        return new AuthResponse(jwtService.generateToken(user), user.getId(), user.getRole());
    }
}
