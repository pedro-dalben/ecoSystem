package com.pedrodalben.ecosystem.ledger;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable record of a completed transaction, for audit purposes.
 */
public record Transaction(
        UUID txId,
        Instant timestamp,
        TransactionType type,
        String currencyId,
        long amount,
        long taxAmount,
        long netAmount,
        @Nullable UUID from,
        @Nullable UUID to,
        String context) {
    /**
     * Creates a new transaction with auto-generated ID and current timestamp.
     */
    public static Transaction create(TransactionType type, String currencyId,
            long amount, long taxAmount, long netAmount,
            @Nullable UUID from, @Nullable UUID to, String context) {
        return new Transaction(UUID.randomUUID(), Instant.now(), type, currencyId,
                amount, taxAmount, netAmount, from, to, context);
    }
}
