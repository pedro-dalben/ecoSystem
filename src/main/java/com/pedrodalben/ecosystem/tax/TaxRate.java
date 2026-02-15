package com.pedrodalben.ecosystem.tax;

/**
 * Defines a tax rate for a specific transaction type.
 */
public record TaxRate(
        long flatAmount,
        double percent,
        long minTax,
        long maxTax,
        TaxDestination destination) {
    public static final TaxRate ZERO = new TaxRate(0, 0, 0, 0, TaxDestination.SINK);
}
