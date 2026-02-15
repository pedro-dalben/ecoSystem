package com.pedrodalben.ecosystem.net;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side balance cache. Updated via BalanceSyncPayload from the server.
 * Used by GUI screens to display current balances without server round-trips.
 */
public class ClientBalanceCache {
    private static volatile Map<String, Long> balances = new HashMap<>();

    public static void update(Map<String, Long> newBalances) {
        balances = new HashMap<>(newBalances);
    }

    public static long getBalance(String currencyId) {
        return balances.getOrDefault(currencyId, 0L);
    }

    public static Map<String, Long> getAllBalances() {
        return new HashMap<>(balances);
    }

    public static void clear() {
        balances = new HashMap<>();
    }
}
