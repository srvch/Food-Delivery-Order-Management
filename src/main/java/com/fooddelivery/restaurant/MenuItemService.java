package com.fooddelivery.restaurant;

import com.fooddelivery.common.exception.NotFoundException;
import com.fooddelivery.restaurant.dto.MenuItemRequest;
import com.fooddelivery.restaurant.dto.MenuItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final RestaurantService restaurantService;

    @Transactional
    public MenuItemResponse addMenuItem(Long ownerId, Long restaurantId, MenuItemRequest request) {
        Restaurant restaurant = restaurantService.getOwnedRestaurantEntity(restaurantId, ownerId);
        MenuItem item = new MenuItem();
        item.setRestaurant(restaurant);
        applyRequest(item, request);
        return toResponse(menuItemRepository.save(item));
    }

    @Transactional
    public MenuItemResponse updateMenuItem(Long ownerId, Long restaurantId, Long itemId, MenuItemRequest request) {
        restaurantService.getOwnedRestaurantEntity(restaurantId, ownerId);
        MenuItem item = menuItemRepository.findByIdAndRestaurantId(itemId, restaurantId)
                .orElseThrow(() -> new NotFoundException("Menu item not found: " + itemId));
        applyRequest(item, request);
        return toResponse(item);
    }

    @Transactional
    public void deleteMenuItem(Long ownerId, Long restaurantId, Long itemId) {
        restaurantService.getOwnedRestaurantEntity(restaurantId, ownerId);
        MenuItem item = menuItemRepository.findByIdAndRestaurantId(itemId, restaurantId)
                .orElseThrow(() -> new NotFoundException("Menu item not found: " + itemId));
        menuItemRepository.delete(item);
    }

    public List<MenuItemResponse> listMenu(Long restaurantId) {
        restaurantService.getRestaurantEntity(restaurantId);
        return menuItemRepository.findByRestaurantId(restaurantId).stream().map(this::toResponse).toList();
    }

    private void applyRequest(MenuItem item, MenuItemRequest request) {
        item.setName(request.name());
        item.setPrice(request.price());
        item.setStockQuantity(request.stockQuantity());
        item.setAvailable(request.available());
    }

    private MenuItemResponse toResponse(MenuItem item) {
        return new MenuItemResponse(item.getId(), item.getRestaurant().getId(), item.getName(),
                item.getPrice(), item.getStockQuantity(), item.isAvailable());
    }
}
