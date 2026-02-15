package com.pedrodalben.ecosystem.ledger;

import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * Result of a ledger operation (transaction attempt).
 */
public record TransactionResult(
        boolean success,
        Component message,
        @Nullable Transaction transaction) {
    public static TransactionResult success(Component message, Transaction transaction) {
        return new TransactionResult(true, message, transaction);
    }

    public static TransactionResult failure(Component message) {
        return new TransactionResult(false, message, null);
    }

    public static TransactionResult failure(String message) {
        return new TransactionResult(false, Component.literal(message), null);
    }
}
