package com.pedrodalben.ecosystem.tax;

import com.pedrodalben.ecosystem.ledger.TransactionType;

import java.util.EnumMap;
import java.util.Map;

/**
 * A named collection of tax rates for different transaction types.
 */
public record TaxProfile(
        String id,
        Map<TransactionType, TaxRate> rates) {
    public TaxRate getRateFor(TransactionType type) {
        return rates.getOrDefault(type, TaxRate.ZERO);
    }

    public static TaxProfile empty(String id) {
        return new TaxProfile(id, new EnumMap<>(TransactionType.class));
    }
}
