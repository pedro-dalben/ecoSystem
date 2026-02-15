package com.pedrodalben.ecosystem.shop.chest;

import com.pedrodalben.ecosystem.core.EcoSystemConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a 4-line sign format inspired by ChestShop-3:
 *
 * <pre>
 * Line 0: Owner name  (player name or "Admin Shop")
 * Line 1: Quantity    (1–999999)
 * Line 2: Price       ("B 50", "S 25", "B 50:S 25", "free")
 * Line 3: Item ID     ("minecraft:diamond", "diamond")
 * </pre>
 */
public class ChestShopSignParser {
    private static final Logger LOGGER = LoggerFactory.getLogger("EcoSystem");

    /** Matches quantity: 1 to 999999 */
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("^([1-9]\\d{0,5})$");

    /**
     * Price line pattern. Supports:
     * "B 50" → buy price 50
     * "S 25" → sell price 25
     * "B 50:S 25" → buy 50, sell 25
     * "S 25:B 50" → sell 25, buy 50
     * "B free" → buy for free
     * "free" → buy and sell for free
     */
    private static final Pattern PRICE_SEGMENT = Pattern.compile(
            "(?i)([BS])\\s*(?:(\\d+(?:\\.\\d+)?)|free)");

    /**
     * Result of parsing a shop sign.
     */
    public record ParseResult(
            boolean valid,
            @Nullable String errorKey,
            @Nullable String ownerName,
            boolean adminShop,
            int quantity,
            long buyPrice,
            long sellPrice,
            boolean canBuy,
            boolean canSell,
            @Nullable String itemId,
            @Nullable String itemDisplayName) {
        public static ParseResult error(String errorKey) {
            return new ParseResult(false, errorKey, null, false, 0, 0, 0, false, false, null, null);
        }

        public static ParseResult success(String ownerName, boolean adminShop, int quantity,
                long buyPrice, long sellPrice,
                boolean canBuy, boolean canSell,
                String itemId, String itemDisplayName) {
            return new ParseResult(true, null, ownerName, adminShop, quantity,
                    buyPrice, sellPrice, canBuy, canSell, itemId, itemDisplayName);
        }
    }

    /**
     * Parse 4 sign lines into a ParseResult.
     *
     * @param lines exactly 4 lines from a sign
     * @return ParseResult with validated fields or an error key
     */
    public static ParseResult parse(String[] lines) {
        if (lines == null || lines.length < 4) {
            return ParseResult.error("error.ecosystem.sign.invalid_format");
        }

        // Line 0: Owner
        String ownerLine = strip(lines[0]);
        if (ownerLine.isEmpty()) {
            return ParseResult.error("error.ecosystem.sign.missing_owner");
        }

        boolean adminShop = isAdminShopName(ownerLine);

        // Line 1: Quantity
        String quantityLine = strip(lines[1]);
        Matcher quantityMatcher = QUANTITY_PATTERN.matcher(quantityLine);
        if (!quantityMatcher.matches()) {
            return ParseResult.error("error.ecosystem.sign.invalid_quantity");
        }
        int quantity = Integer.parseInt(quantityMatcher.group(1));

        // Line 2: Price
        String priceLine = strip(lines[2]);
        long buyPrice = -1;
        long sellPrice = -1;
        boolean canBuy = false;
        boolean canSell = false;

        if (priceLine.equalsIgnoreCase("free")) {
            buyPrice = 0;
            sellPrice = 0;
            canBuy = true;
            canSell = true;
        } else {
            Matcher priceMatcher = PRICE_SEGMENT.matcher(priceLine);
            while (priceMatcher.find()) {
                String type = priceMatcher.group(1).toUpperCase(Locale.ROOT);
                String valueStr = priceMatcher.group(2);
                long value = 0;
                if (valueStr != null) {
                    try {
                        value = Math.round(Double.parseDouble(valueStr));
                    } catch (NumberFormatException e) {
                        return ParseResult.error("error.ecosystem.sign.invalid_price");
                    }
                }
                // group(2) is null when "free" is matched (entire match is "B free" etc.)
                // In that case value stays 0

                if ("B".equals(type)) {
                    buyPrice = value;
                    canBuy = true;
                } else if ("S".equals(type)) {
                    sellPrice = value;
                    canSell = true;
                }
            }

            if (!canBuy && !canSell) {
                return ParseResult.error("error.ecosystem.sign.invalid_price");
            }
        }

        if (buyPrice < 0)
            buyPrice = 0;
        if (sellPrice < 0)
            sellPrice = 0;

        // Line 3: Item ID
        String itemLine = strip(lines[3]);
        if (itemLine.isEmpty()) {
            return ParseResult.error("error.ecosystem.sign.missing_item");
        }

        // Resolve item — support both "minecraft:diamond" and "diamond" (default to
        // minecraft:)
        String itemId = itemLine.contains(":") ? itemLine : "minecraft:" + itemLine;
        ResourceLocation rl;
        try {
            rl = ResourceLocation.parse(itemId);
        } catch (Exception e) {
            return ParseResult.error("error.ecosystem.sign.invalid_item");
        }

        // Verify item exists in registry
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == net.minecraft.world.item.Items.AIR) {
            return ParseResult.error("error.ecosystem.sign.invalid_item");
        }

        String displayName = item.getDescription().getString();
        String resolvedId = rl.toString();

        return ParseResult.success(ownerLine, adminShop, quantity,
                buyPrice, sellPrice, canBuy, canSell, resolvedId, displayName);
    }

    /**
     * Check if a name matches the configured admin shop name (case-insensitive,
     * spaces ignored).
     */
    public static boolean isAdminShopName(String name) {
        String normalized = name.replace(" ", "").toLowerCase(Locale.ROOT);
        String adminName = EcoSystemConfig.ADMIN_SHOP_NAME.get().replace(" ", "").toLowerCase(Locale.ROOT);
        return normalized.equals(adminName);
    }

    private static String strip(String s) {
        return s == null ? "" : s.trim();
    }
}
