package com.pedrodalben.ecosystem.shop.gui;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Top-level definition of a shop loaded from a YAML/JSON file.
 * Each file in world/serverconfig/ecosystem/shops/ becomes one ShopDefinition.
 */
public record ShopDefinition(
        String shopId,
        String title,
        String currencyId,
        int rows,
        List<CatalogPage> pages) {

    /** Default number of inventory rows. */
    public static final int DEFAULT_ROWS = 6;
    /** Slots per row in a Minecraft chest GUI. */
    public static final int SLOTS_PER_ROW = 9;

    public int getPageCount() {
        return pages.size();
    }

    @Nullable
    public CatalogPage getPage(int index) {
        if (index < 0 || index >= pages.size())
            return null;
        return pages.get(index);
    }

    @Nullable
    public CatalogEntry getEntry(int pageIndex, int slot) {
        CatalogPage page = getPage(pageIndex);
        return page != null ? page.getEntry(slot) : null;
    }

    public int getMaxSlot() {
        return rows * SLOTS_PER_ROW - 1;
    }
}
