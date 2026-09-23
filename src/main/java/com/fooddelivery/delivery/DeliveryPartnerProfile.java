package com.fooddelivery.delivery;

import com.fooddelivery.city.City;
import com.fooddelivery.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "delivery_partner_profiles")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryPartnerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "city_id")
    private City city;

    @Column(nullable = false)
    private boolean active = false;
}
