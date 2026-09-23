package com.fooddelivery.order;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * No real payment provider is in scope. This simulated gateway always
 * succeeds; tests that need to exercise the decline/rollback path override
 * this bean with a {@code @MockBean}.
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    @Override
    public void charge(BigDecimal amount) {
        // Always succeeds.
    }
}
