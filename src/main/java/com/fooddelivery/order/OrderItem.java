package com.fooddelivery.order;

import com.fooddelivery.restaurant.MenuItem;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(optional = false)
    @JoinColumn(name = "menu_item_id")
    private MenuItem menuItem;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_at_order", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPriceAtOrder;
}
