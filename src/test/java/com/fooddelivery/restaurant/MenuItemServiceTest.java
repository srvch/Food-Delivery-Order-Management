package com.fooddelivery.restaurant;

import com.fooddelivery.common.exception.ForbiddenException;
import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.restaurant.dto.MenuItemRequest;
import com.fooddelivery.restaurant.dto.MenuItemResponse;
import com.fooddelivery.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuItemServiceTest {

    @Mock private MenuItemRepository menuItemRepository;
    @Mock private RestaurantService restaurantService;
    @InjectMocks private MenuItemService menuItemService;

    private Restaurant ownedRestaurant(Long ownerId) {
        User owner = new User();
        owner.setId(ownerId);
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);
        restaurant.setOwner(owner);
        return restaurant;
    }

    @Test
    void addMenuItemSucceedsForOwner() {
        when(restaurantService.getOwnedRestaurantEntity(5L, 1L)).thenReturn(ownedRestaurant(1L));
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(inv -> {
            MenuItem m = inv.getArgument(0);
            m.setId(50L);
            return m;
        });

        var request = new MenuItemRequest("Paneer Tikka", new BigDecimal("199.00"), 10, true);
        MenuItemResponse response = menuItemService.addMenuItem(1L, 5L, request);

        assertThat(response.id()).isEqualTo(50L);
        assertThat(response.name()).isEqualTo("Paneer Tikka");
    }

    @Test
    void addMenuItemFailsForNonOwner() {
        when(restaurantService.getOwnedRestaurantEntity(5L, 2L))
                .thenThrow(new ForbiddenException("You do not own restaurant 5"));

        var request = new MenuItemRequest("Paneer Tikka", new BigDecimal("199.00"), 10, true);

        assertThatThrownBy(() -> menuItemService.addMenuItem(2L, 5L, request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateMenuItemRejectsItemFromDifferentRestaurant() {
        when(restaurantService.getOwnedRestaurantEntity(5L, 1L)).thenReturn(ownedRestaurant(1L));
        when(menuItemRepository.findByIdAndRestaurantId(50L, 5L)).thenReturn(Optional.empty());

        var request = new MenuItemRequest("Paneer Tikka", new BigDecimal("199.00"), 10, true);

        assertThatThrownBy(() -> menuItemService.updateMenuItem(1L, 5L, 50L, request))
                .isInstanceOf(NotFoundException.class);
    }
}
