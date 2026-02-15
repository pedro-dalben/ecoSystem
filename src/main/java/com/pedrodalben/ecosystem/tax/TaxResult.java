package com.pedrodalben.ecosystem.tax;

/**
 * Result of a tax calculation.
 */
public record TaxResult(
        long grossAmount,
        long taxAmount,
        long netAmount,
        TaxDestination destination) {
    /**
     * No-tax result for the given amount.
     */
    public static TaxResult noTax(long amount) {
        return new TaxResult(amount, 0, amount, TaxDestination.SINK);
    }
}
