package com.pedrodalben.ecosystem.net;

import com.pedrodalben.ecosystem.shop.gui.ShopDefinition;

import javax.annotation.Nullable;

/**
 * Client-side cache for the currently-viewed shop catalog data.
 * Updated when the server sends a CatalogSyncPayload.
 */
public class ClientCatalogCache {

    @Nullable
    private static ShopDefinition currentShop;

    public static void update(ShopDefinition def) {
        currentShop = def;
    }

    @Nullable
    public static ShopDefinition getCurrentShop() {
        return currentShop;
    }

    public static void clear() {
        currentShop = null;
    }
}
