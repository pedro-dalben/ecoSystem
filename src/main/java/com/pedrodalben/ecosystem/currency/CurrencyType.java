package com.pedrodalben.ecosystem.currency;

/**
 * Type of currency in the economy system.
 */
public enum CurrencyType {
    /** Pure virtual currency - no physical item representation */
    VIRTUAL,
    /** Backed by a physical item - can deposit/withdraw items */
    ITEM_BACKED
}
