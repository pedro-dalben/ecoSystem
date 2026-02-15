package com.pedrodalben.ecosystem.ledger;

import java.util.UUID;

/**
 * Composite key for a player's balance in a specific currency.
 */
public record AccountKey(UUID playerUuid, String currencyId) {

    @Override
    public String toString() {
        return playerUuid + ":" + currencyId;
    }
}
