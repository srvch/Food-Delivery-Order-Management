package com.fooddelivery.restaurant;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findByRestaurantId(Long restaurantId);
    Optional<MenuItem> findByIdAndRestaurantId(Long id, Long restaurantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MenuItem m where m.id in :ids order by m.id")
    List<MenuItem> findAllByIdInForUpdate(@Param("ids") List<Long> ids);
}
