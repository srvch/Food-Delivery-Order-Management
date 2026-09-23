package com.fooddelivery.rating;

import com.fooddelivery.rating.dto.RateRequest;
import com.fooddelivery.rating.dto.RatingResponse;
import com.fooddelivery.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders/{orderId}/ratings")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    @PostMapping
    public ResponseEntity<RatingResponse> rate(@PathVariable Long orderId,
                                                @AuthenticationPrincipal User customer,
                                                @Valid @RequestBody RateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ratingService.rate(customer, orderId, request));
    }
}
