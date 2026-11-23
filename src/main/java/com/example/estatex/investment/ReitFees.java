package com.example.estatex.investment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * EstateX's REIT transaction fee: a percentage added on top of every purchase and taken off the
 * proceeds of every sale. Shown to the investor before they confirm (see the fee-quote endpoint).
 */
@Component
public class ReitFees {
    private final BigDecimal percent;

    public ReitFees(@Value("${app.investments.transaction-fee-percent:1.5}") BigDecimal percent) {
        if (percent.signum() < 0 || percent.compareTo(new BigDecimal("10")) > 0) {
            throw new IllegalArgumentException("app.investments.transaction-fee-percent must be between 0 and 10");
        }
        this.percent = percent;
    }

    public BigDecimal percent() {
        return percent;
    }

    /** gross = units x unit price; fee = gross x percent (to the cent); total/net = what changes hands. */
    public record Quote(BigDecimal gross, BigDecimal fee, BigDecimal investorAmount, BigDecimal feePercent) {}

    /** Buying: the investor pays gross + fee. */
    public Quote buy(BigDecimal unitPrice, BigDecimal units) {
        BigDecimal gross = unitPrice.multiply(units).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = fee(gross);
        return new Quote(gross, fee, gross.add(fee), percent);
    }

    /** Selling: the investor receives gross - fee. */
    public Quote sell(BigDecimal unitPrice, BigDecimal units) {
        BigDecimal gross = unitPrice.multiply(units).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = fee(gross);
        return new Quote(gross, fee, gross.subtract(fee), percent);
    }

    private BigDecimal fee(BigDecimal gross) {
        return gross.multiply(percent).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
}
