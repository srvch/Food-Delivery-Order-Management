package com.fooddelivery.restaurant;

import com.fooddelivery.city.City;
import com.fooddelivery.city.CityRepository;
import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.ForbiddenException;
import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.restaurant.dto.AdminCreateRestaurantRequest;
import com.fooddelivery.restaurant.dto.RestaurantResponse;
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
class RestaurantServiceTest {

    @Mock private RestaurantRepository restaurantRepository;
    @Mock private CityRepository cityRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private RestaurantService restaurantService;

    @Test
    void createRestaurantCreatesNewOwnerWhenEmailUnused() {
        City city = new City();
        city.setId(1L);
        city.setName("Pune");
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city));
        when(userRepository.findByEmail("owner1@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> {
            Restaurant r = inv.getArgument(0);
            r.setId(100L);
            return r;
        });

        var request = new AdminCreateRestaurantRequest(1L, "Tasty Bites", "MG Road", "owner1@example.com", "password123");
        RestaurantResponse response = restaurantService.createRestaurantWithOwner(request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.ownerId()).isEqualTo(10L);
    }

    @Test
    void createRestaurantRejectsEmailBelongingToNonOwner() {
        City city = new City();
        city.setId(1L);
        when(cityRepository.findById(1L)).thenReturn(Optional.of(city));
        User existing = new User();
        existing.setRole(Role.CUSTOMER);
        when(userRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(existing));

        var request = new AdminCreateRestaurantRequest(1L, "Tasty Bites", "MG Road", "taken@example.com", "password123");

        assertThatThrownBy(() -> restaurantService.createRestaurantWithOwner(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createRestaurantRejectsUnknownCity() {
        when(cityRepository.findById(99L)).thenReturn(Optional.empty());

        var request = new AdminCreateRestaurantRequest(99L, "Tasty Bites", "MG Road", "owner2@example.com", "password123");

        assertThatThrownBy(() -> restaurantService.createRestaurantWithOwner(request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getOwnedRestaurantEntityRejectsWrongOwner() {
        User owner = new User();
        owner.setId(1L);
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        restaurant.setOwner(owner);
        when(restaurantRepository.findById(5L)).thenReturn(Optional.of(restaurant));

        assertThatThrownBy(() -> restaurantService.getOwnedRestaurantEntity(5L, 2L))
                .isInstanceOf(ForbiddenException.class);
    }
}
