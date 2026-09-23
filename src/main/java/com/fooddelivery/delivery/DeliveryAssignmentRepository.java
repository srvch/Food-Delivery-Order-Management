package com.fooddelivery.delivery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, Long> {

    Optional<DeliveryAssignment> findByOrderId(Long orderId);

    @Query("select da from DeliveryAssignment da where da.status = com.fooddelivery.delivery.DeliveryAssignmentStatus.OPEN and da.order.restaurant.city.id = :cityId")
    List<DeliveryAssignment> findOpenByCityId(@Param("cityId") Long cityId);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE delivery_assignments SET status = 'ACCEPTED', partner_id = :partnerId, accepted_at = now() WHERE id = :id AND status = 'OPEN'", nativeQuery = true)
    int acceptIfOpen(@Param("id") Long id, @Param("partnerId") Long partnerId);
}
