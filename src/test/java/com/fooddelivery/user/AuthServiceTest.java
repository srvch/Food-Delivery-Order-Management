package com.fooddelivery.user;

import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.ForbiddenException;
import com.fooddelivery.common.security.JwtService;
import com.fooddelivery.user.dto.AuthResponse;
import com.fooddelivery.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @InjectMocks private AuthService authService;

    @Test
    void registerCreatesCustomerAndReturnsToken() {
        var request = new RegisterRequest("alice@example.com", "password123", Role.CUSTOMER, null);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("token-123");

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("token-123");
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void registerRejectsAdminRole() {
        var request = new RegisterRequest("bob@example.com", "password123", Role.ADMIN, null);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        var request = new RegisterRequest("alice@example.com", "password123", Role.CUSTOMER, null);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class);
    }
}
