package com.jakeladd.salestax;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Pure math tests: no database needed.
 */
class TaxCalculatorTest {

    // Helper: compares BigDecimal values ignoring scale (8.75 == 8.75000)
    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    @Test
    void taxOnHundredDollarsAtEightPointSevenFive() {
        BigDecimal tax = TaxCalculator.taxOwed(new BigDecimal("100.00"), new BigDecimal("8.75"));
        assertEquals(new BigDecimal("8.75"), tax);
    }

    @Test
    void halfCentRoundsUp() {
        // 10.00 x 8.75% = 0.875 -> 0.88
        BigDecimal tax = TaxCalculator.taxOwed(new BigDecimal("10.00"), new BigDecimal("8.75"));
        assertEquals(new BigDecimal("0.88"), tax);
    }

    @Test
    void justUnderHalfCentRoundsDown_noDoubleRounding() {
        // 1.47 x 8.5% = 0.12495 -> 0.12 (two-step rounding would wrongly give 0.13)
        BigDecimal tax = TaxCalculator.taxOwed(new BigDecimal("1.47"), new BigDecimal("8.5"));
        assertEquals(new BigDecimal("0.12"), tax);
    }

    @Test
    void threeDecimalRateIsNotTruncated() {
        // NYC-style rate: 10.00 x 8.875% = 0.8875 -> 0.89
        BigDecimal tax = TaxCalculator.taxOwed(new BigDecimal("10.00"), new BigDecimal("8.875"));
        assertEquals(new BigDecimal("0.89"), tax);
    }

    @Test
    void oneCentPurchaseRoundsToZeroTax() {
        // 0.01 x 8.875% = 0.00089 -> 0.00
        BigDecimal tax = TaxCalculator.taxOwed(new BigDecimal("0.01"), new BigDecimal("8.875"));
        assertEquals(new BigDecimal("0.00"), tax);
    }

    @Test
    void combinedRateAddsStateLocalAndMctd() {
        Rate rate = new Rate(1, 1,
                new BigDecimal("4.00000"), new BigDecimal("4.50000"), new BigDecimal("0.37500"),
                LocalDate.of(2009, 8, 1), null, 1);
        assertAmount("8.875", TaxCalculator.combinedRate(rate));
    }
}
