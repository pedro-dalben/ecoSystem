package com.pedrodalben.ecosystem.storage;

import com.pedrodalben.ecosystem.ledger.AccountKey;

import java.util.Map;
import java.util.UUID;

/**
 * Interface for economy data persistence backends.
 */
public interface StorageBackend {

    /** Initialize the backend (create tables, open connections, etc.) */
    void init();

    /** Shut down the backend (close connections, flush remaining data) */
    void shutdown();

    /** Load all balances from storage. Called once at server start. */
    Map<AccountKey, Long> loadAllBalances();

    /** Load a single player's balances. Called on player login. */
    Map<String, Long> loadPlayerBalances(UUID playerUuid);

    /** Save a batch of balance changes. */
    void saveBalances(Map<AccountKey, Long> balances);

    /** Save a single player's balances. Called on player logout. */
    void savePlayerBalances(UUID playerUuid, Map<String, Long> balances);
}
