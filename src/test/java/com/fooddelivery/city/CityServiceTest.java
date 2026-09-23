package com.fooddelivery.city;

import com.fooddelivery.city.dto.CityRequest;
import com.fooddelivery.city.dto.CityResponse;
import com.fooddelivery.common.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CityServiceTest {

    @Mock private CityRepository cityRepository;
    @InjectMocks private CityService cityService;

    @Test
    void createCityRejectsDuplicateName() {
        when(cityRepository.findByNameIgnoreCase("Bangalore")).thenReturn(Optional.of(new City()));

        assertThatThrownBy(() -> cityService.createCity(new CityRequest("Bangalore", true)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createCitySucceeds() {
        when(cityRepository.findByNameIgnoreCase("Pune")).thenReturn(Optional.empty());
        when(cityRepository.save(any(City.class))).thenAnswer(inv -> {
            City c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        CityResponse response = cityService.createCity(new CityRequest("Pune", true));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Pune");
    }
}
