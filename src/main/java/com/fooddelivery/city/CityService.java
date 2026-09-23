package com.fooddelivery.city;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.exception.ConflictException;
import com.fooddelivery.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CityService {

    private final CityRepository cityRepository;

    @Transactional
    public CityResponse createCity(CityRequest request) {
        cityRepository.findByNameIgnoreCase(request.name()).ifPresent(c -> {
            throw new ConflictException("City already exists: " + request.name());
        });
        City city = new City();
        city.setName(request.name());
        city.setActive(request.active());
        return toResponse(cityRepository.save(city));
    }

    public List<CityResponse> listCities() {
        return cityRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public CityResponse updateCity(Long id, CityRequest request) {
        City city = cityRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("City not found: " + id));
        city.setName(request.name());
        city.setActive(request.active());
        return toResponse(city);
    }

    private CityResponse toResponse(City city) {
        return new CityResponse(city.getId(), city.getName(), city.isActive());
    }
}
