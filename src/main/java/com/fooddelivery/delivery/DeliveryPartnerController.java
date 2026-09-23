package com.fooddelivery.delivery;

import com.fooddelivery.delivery.dto.AdminCreateDeliveryPartnerRequest;
import com.fooddelivery.delivery.dto.DeliveryPartnerResponse;
import com.fooddelivery.delivery.dto.DeliveryPartnerUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/delivery-partners")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class DeliveryPartnerController {

    private final DeliveryPartnerService deliveryPartnerService;

    @PostMapping
    public ResponseEntity<DeliveryPartnerResponse> create(@Valid @RequestBody AdminCreateDeliveryPartnerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deliveryPartnerService.createPartner(request));
    }

    @GetMapping
    public List<DeliveryPartnerResponse> list() {
        return deliveryPartnerService.listPartners();
    }

    @PutMapping("/{id}")
    public DeliveryPartnerResponse update(@PathVariable Long id, @Valid @RequestBody DeliveryPartnerUpdateRequest request) {
        return deliveryPartnerService.updatePartner(id, request);
    }
}
