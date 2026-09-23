package com.fooddelivery.order.dto;

import java.math.BigDecimal;

public record OrderItemLine(Long menuItemId, String menuItemName, int quantity, BigDecimal unitPrice) {
}
