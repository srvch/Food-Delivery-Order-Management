package com.fooddelivery.restaurant;

import com.fooddelivery.order.OrderService;
import com.fooddelivery.order.dto.OrderResponse;
import com.fooddelivery.restaurant.dto.AdminCreateRestaurantRequest;
import com.fooddelivery.restaurant.dto.RestaurantResponse;
import com.fooddelivery.restaurant.dto.RestaurantUpdateRequest;
import com.fooddelivery.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;
    private final OrderService orderService;

    @PostMapping("/admin/restaurants")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestaurantResponse> create(@Valid @RequestBody AdminCreateRestaurantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(restaurantService.createRestaurantWithOwner(request));
    }

    @GetMapping("/admin/restaurants")
    @PreAuthorize("hasRole('ADMIN')")
    public List<RestaurantResponse> listForAdmin() {
        return restaurantService.listRestaurants(null);
    }

    @PutMapping("/admin/restaurants/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RestaurantResponse update(@PathVariable Long id, @Valid @RequestBody RestaurantUpdateRequest request) {
        return restaurantService.updateRestaurant(id, request);
    }

    @GetMapping("/restaurants")
    public List<RestaurantResponse> list(@RequestParam(required = false) Long cityId) {
        return restaurantService.listRestaurants(cityId);
    }

    @GetMapping("/restaurants/{id}")
    public RestaurantResponse get(@PathVariable Long id) {
        return restaurantService.getRestaurant(id);
    }

    @GetMapping("/restaurants/{id}/orders")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public List<OrderResponse> restaurantOrders(@PathVariable Long id, @AuthenticationPrincipal User owner) {
        return orderService.listRestaurantOrders(owner.getId(), id);
    }
}
