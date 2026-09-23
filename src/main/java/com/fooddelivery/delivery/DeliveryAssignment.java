package com.fooddelivery.delivery;

import com.fooddelivery.order.Order;
import com.fooddelivery.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "delivery_assignments")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryAssignmentStatus status = DeliveryAssignmentStatus.OPEN;

    @ManyToOne
    @JoinColumn(name = "partner_id")
    private User partner;

    @Column(name = "offered_at", nullable = false)
    private Instant offeredAt = Instant.now();

    @Column(name = "accepted_at")
    private Instant acceptedAt;
}
