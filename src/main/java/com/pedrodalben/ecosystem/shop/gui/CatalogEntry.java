package com.pedrodalben.ecosystem.shop.gui;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A single slot entry in a shop page.
 * Mirrors GUIShop item types: SHOP (tradeable), DECORATIVE (display-only),
 * NAVIGATION (page/shop nav).
 */
public record CatalogEntry(
        String itemId,
        String displayName,
        List<String> lore,
        String currencyId,
        long buyPrice,
        long sellPrice,
        int quantity,
        boolean allowBuy,
        boolean allowSell,
        EntryType type,
        @Nullable String targetShop,
        @Nullable NavigationAction navAction) {

    public enum EntryType {
        /** Standard purchasable/sellable item (GUIShop SHOP) */
        SHOP,
        /** Decorative filler item, not interactive (GUIShop DUMMY/BLANK) */
        DECORATIVE,
        /** Navigation button: next page, prev page, or link to another shop */
        NAVIGATION
    }

    public enum NavigationAction {
        NEXT_PAGE,
        PREV_PAGE,
        BACK
    }

    /**
     * Create a standard SHOP entry.
     */
    public static CatalogEntry shop(String itemId, String displayName, List<String> lore,
            String currencyId, long buyPrice, long sellPrice,
            int quantity, boolean allowBuy, boolean allowSell) {
        return new CatalogEntry(itemId, displayName, lore, currencyId,
                buyPrice, sellPrice, quantity, allowBuy, allowSell,
                EntryType.SHOP, null, null);
    }

    /**
     * Create a DECORATIVE entry (display-only filler).
     */
    public static CatalogEntry decorative(String itemId, String displayName) {
        return new CatalogEntry(itemId, displayName, List.of(), "",
                0, 0, 1, false, false,
                EntryType.DECORATIVE, null, null);
    }

    /**
     * Create a NAVIGATION entry for page navigation or shop linking.
     */
    public static CatalogEntry navigation(String itemId, String displayName,
            @Nullable String targetShop, @Nullable NavigationAction action) {
        return new CatalogEntry(itemId, displayName, List.of(), "",
                0, 0, 1, false, false,
                EntryType.NAVIGATION, targetShop, action);
    }
}
