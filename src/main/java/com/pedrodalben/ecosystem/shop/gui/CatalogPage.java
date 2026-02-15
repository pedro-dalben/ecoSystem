package com.pedrodalben.ecosystem.shop.gui;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;

/**
 * A single page in a shop, mapping slot index (0-53) to catalog entries.
 * Mirrors GUIShop's Page0 / Page1 structure with numbered slots.
 */
public record CatalogPage(Map<Integer, CatalogEntry> slots) {

    /** Maximum slot index in a 6-row inventory (0-53). */
    public static final int MAX_SLOT = 53;

    @Nullable
    public CatalogEntry getEntry(int slot) {
        return slots.get(slot);
    }

    public int getEntryCount() {
        return slots.size();
    }

    public Map<Integer, CatalogEntry> getSlots() {
        return Collections.unmodifiableMap(slots);
    }
}
