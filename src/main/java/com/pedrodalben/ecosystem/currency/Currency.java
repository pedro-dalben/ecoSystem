package com.pedrodalben.ecosystem.currency;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Represents a currency definition in the economy system.
 * Currencies are defined in currencies.json and loaded at server start.
 */
public class Currency {
    private final String id;
    private final String displayName;
    private final CurrencyType type;
    @Nullable
    private final ResourceLocation itemId;
    private final int unitAmount;
    private final int precision;
    private final boolean shopEnabled;
    private final String taxProfileId;
    private final String symbol;

    public Currency(String id, String displayName, CurrencyType type,
            @Nullable ResourceLocation itemId, int unitAmount, int precision,
            boolean shopEnabled, String taxProfileId, String symbol) {
        this.id = id;
        this.displayName = displayName;
        this.type = type;
        this.itemId = itemId;
        this.unitAmount = unitAmount;
        this.precision = precision;
        this.shopEnabled = shopEnabled;
        this.taxProfileId = taxProfileId;
        this.symbol = symbol;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public CurrencyType getType() {
        return type;
    }

    @Nullable
    public ResourceLocation getItemId() {
        return itemId;
    }

    public int getUnitAmount() {
        return unitAmount;
    }

    public int getPrecision() {
        return precision;
    }

    public boolean isShopEnabled() {
        return shopEnabled;
    }

    public String getTaxProfileId() {
        return taxProfileId;
    }

    public String getSymbol() {
        return symbol;
    }

    public boolean isItemBacked() {
        return type == CurrencyType.ITEM_BACKED;
    }

    /**
     * Converts a human-readable amount (e.g. 10.50) to minor units (e.g. 1050 with
     * precision=2).
     */
    public long toMinorUnits(double humanAmount) {
        long multiplier = (long) Math.pow(10, precision);
        return Math.round(humanAmount * multiplier);
    }

    /**
     * Converts minor units to a human-readable double (e.g. 1050 → 10.50 with
     * precision=2).
     */
    public double toHumanAmount(long minorUnits) {
        double divisor = Math.pow(10, precision);
        return minorUnits / divisor;
    }

    /**
     * Formats a balance in minor units to a human-readable string with symbol.
     * Examples: "§6$10.50", "§b5 Gems"
     */
    public String formatAmount(long minorUnits) {
        double human = toHumanAmount(minorUnits);
        String formatted;
        if (precision == 0) {
            formatted = String.format("%,d", minorUnits);
        } else {
            formatted = String.format("%,." + precision + "f", human);
        }
        return symbol + formatted;
    }

    /**
     * Returns a Component for display in chat/GUI.
     */
    public MutableComponent formatAmountComponent(long minorUnits) {
        return Component.literal(formatAmount(minorUnits));
    }

    /**
     * Returns the display name as a Component (supports § color codes).
     */
    public MutableComponent displayNameComponent() {
        return Component.literal(displayName);
    }

    @Override
    public String toString() {
        return "Currency{id='" + id + "', type=" + type + ", precision=" + precision + "}";
    }
}
