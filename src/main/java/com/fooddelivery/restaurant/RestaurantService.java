package com.fooddelivery.restaurant;

import com.fooddelivery.city.City;
import com.fooddelivery.city.CityRepository;
import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.ForbiddenException;
import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.restaurant.dto.AdminCreateRestaurantRequest;
import com.fooddelivery.restaurant.dto.RestaurantResponse;
import com.fooddelivery.restaurant.dto.RestaurantUpdateRequest;
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
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final CityRepository cityRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public RestaurantResponse createRestaurantWithOwner(AdminCreateRestaurantRequest request) {
        City city = cityRepository.findById(request.cityId())
                .orElseThrow(() -> new NotFoundException("City not found: " + request.cityId()));

        User owner = userRepository.findByEmail(request.ownerEmail()).map(existing -> {
            if (existing.getRole() != Role.RESTAURANT_OWNER) {
                throw new ConflictException(
                        "Email belongs to an account that is not a restaurant owner: " + request.ownerEmail());
            }
            return existing;
        }).orElseGet(() -> {
            User newOwner = new User();
            newOwner.setEmail(request.ownerEmail());
            newOwner.setPassword(passwordEncoder.encode(request.ownerPassword()));
            newOwner.setRole(Role.RESTAURANT_OWNER);
            return userRepository.save(newOwner);
        });

        Restaurant restaurant = new Restaurant();
        restaurant.setCity(city);
        restaurant.setOwner(owner);
        restaurant.setName(request.name());
        restaurant.setAddress(request.address());
        return toResponse(restaurantRepository.save(restaurant));
    }

    public RestaurantResponse getRestaurant(Long id) {
        return toResponse(getRestaurantEntity(id));
    }

    public Restaurant getRestaurantEntity(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Restaurant not found: " + id));
    }

    public Restaurant getOwnedRestaurantEntity(Long restaurantId, Long ownerId) {
        Restaurant restaurant = getRestaurantEntity(restaurantId);
        if (!restaurant.getOwner().getId().equals(ownerId)) {
            throw new ForbiddenException("You do not own restaurant " + restaurantId);
        }
        return restaurant;
    }

    public List<RestaurantResponse> listRestaurants(Long cityId) {
        List<Restaurant> restaurants = cityId == null
                ? restaurantRepository.findAll()
                : restaurantRepository.findByCityId(cityId);
        return restaurants.stream().map(this::toResponse).toList();
    }

    @Transactional
    public RestaurantResponse updateRestaurant(Long id, RestaurantUpdateRequest request) {
        Restaurant restaurant = getRestaurantEntity(id);
        restaurant.setName(request.name());
        restaurant.setAddress(request.address());
        return toResponse(restaurant);
    }

    private RestaurantResponse toResponse(Restaurant restaurant) {
        return new RestaurantResponse(
                restaurant.getId(), restaurant.getCity().getId(), restaurant.getCity().getName(),
                restaurant.getOwner().getId(), restaurant.getName(), restaurant.getAddress(),
                restaurant.getAvgRating(), restaurant.getRatingCount());
    }
}
