package com.fooddelivery.restaurant;

import com.fooddelivery.restaurant.dto.MenuItemRequest;
import com.fooddelivery.restaurant.dto.MenuItemResponse;
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
@RequestMapping("/restaurants/{restaurantId}")
@RequiredArgsConstructor
public class MenuItemController {

    private final MenuItemService menuItemService;

    @PostMapping("/menu-items")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public ResponseEntity<MenuItemResponse> add(@PathVariable Long restaurantId,
                                                 @AuthenticationPrincipal User owner,
                                                 @Valid @RequestBody MenuItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(menuItemService.addMenuItem(owner.getId(), restaurantId, request));
    }

    @PutMapping("/menu-items/{itemId}")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public MenuItemResponse update(@PathVariable Long restaurantId, @PathVariable Long itemId,
                                   @AuthenticationPrincipal User owner,
                                   @Valid @RequestBody MenuItemRequest request) {
        return menuItemService.updateMenuItem(owner.getId(), restaurantId, itemId, request);
    }

    @DeleteMapping("/menu-items/{itemId}")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    public ResponseEntity<Void> delete(@PathVariable Long restaurantId, @PathVariable Long itemId,
                                        @AuthenticationPrincipal User owner) {
        menuItemService.deleteMenuItem(owner.getId(), restaurantId, itemId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/menu")
    public List<MenuItemResponse> listMenu(@PathVariable Long restaurantId) {
        return menuItemService.listMenu(restaurantId);
    }
}
