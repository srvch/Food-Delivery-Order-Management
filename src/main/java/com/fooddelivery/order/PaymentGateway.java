package com.fooddelivery.order;

import java.math.BigDecimal;

public interface PaymentGateway {
    void charge(BigDecimal amount);
}
