package com.pedrodalben.ecosystem.ledger;

/**
 * Type of transaction for audit trail.
 */
public enum TransactionType {
    DEPOSIT,
    WITHDRAW,
    PAY,
    SHOP_BUY,
    SHOP_SELL,
    ADMIN_SET,
    ADMIN_GIVE,
    ADMIN_TAKE
}
