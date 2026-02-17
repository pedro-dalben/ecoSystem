package com.pedrodalben.ecosystem.shop.chat;

import com.pedrodalben.ecosystem.core.EcoSystemConfig;
import com.pedrodalben.ecosystem.core.LangKeys;
import com.pedrodalben.ecosystem.core.ServerEvents;
import com.pedrodalben.ecosystem.currency.Currency;
import com.pedrodalben.ecosystem.ledger.LedgerService;
import com.pedrodalben.ecosystem.ledger.TransactionResult;
import com.pedrodalben.ecosystem.shop.gui.CatalogEntry;
import com.pedrodalben.ecosystem.shop.gui.CatalogPage;
import com.pedrodalben.ecosystem.shop.gui.ShopCatalog;
import com.pedrodalben.ecosystem.shop.gui.ShopDefinition;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ChatShopHandler {

    public static void openMain(ServerPlayer player) {
        if (!EcoSystemConfig.ENABLE_CHAT_SHOP.get()) {
            player.sendSystemMessage(Component.translatable(LangKeys.ERROR_FEATURE_DISABLED));
            return;
        }

        ShopCatalog catalog = ServerEvents.getShopCatalog();
        if (catalog == null) {
            player.sendSystemMessage(Component.translatable(LangKeys.ERROR_GENERIC));
            return;
        }

        MutableComponent message = Component.literal("=== EcoSystem Shops ===\n").withStyle(ChatFormatting.GOLD,
                ChatFormatting.BOLD);

        for (ShopDefinition shop : catalog.getAllShops()) {
            MutableComponent shopLine = Component.literal("• ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(shop.title()).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" (" + shop.currencyId() + ")").withStyle(ChatFormatting.GRAY));

            shopLine.withStyle(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/eco shop open " + shop.shopId()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Click to open " + shop.title()))));

            message.append(shopLine).append("\n");
        }

        player.sendSystemMessage(message);
    }

    public static void openShop(ServerPlayer player, String shopId, int pageIndex) {
        if (!EcoSystemConfig.ENABLE_CHAT_SHOP.get()) {
            player.sendSystemMessage(Component.translatable(LangKeys.ERROR_FEATURE_DISABLED));
            return;
        }

        ShopCatalog catalog = ServerEvents.getShopCatalog();
        ShopDefinition shop = catalog.getShop(shopId);
        if (shop == null) {
            player.sendSystemMessage(Component.translatable(LangKeys.ERROR_SHOP_NOT_FOUND));
            return;
        }

        CatalogPage page = shop.getPage(pageIndex);
        if (page == null) {
            if (pageIndex > 0)
                openShop(player, shopId, 0); // Reset to 0 if out of bounds
            return;
        }

        MutableComponent message = Component.literal("=== " + shop.title() + " (Page " + (pageIndex + 1) + ") ===\n")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

        Currency currency = ServerEvents.getCurrencyRegistry().getCurrency(shop.currencyId());
        String symbol = currency != null ? currency.getSymbol() : "$";

        // Iterate entries efficiently
        // We can't easily paginate slots 0-53 into chat pages without mapping.
        // For simplicity, we just list non-empty slots.
        // A full 54-slot page is too much for chat.
        // We might just list them all? Or rely on user scrolling.
        // Chat history is long. Let's list all valid shop entries in this page.

        for (var entryMap : page.slots().entrySet()) {
            int slot = entryMap.getKey();
            CatalogEntry entry = entryMap.getValue();

            if (entry.type() == CatalogEntry.EntryType.SHOP) {
                MutableComponent itemLine = Component.literal("• ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.literal(entry.displayName()).withStyle(ChatFormatting.AQUA));

                if (entry.allowBuy()) {
                    itemLine.append(" ")
                            .append(Component.literal("[BUY: " + symbol + entry.buyPrice() + "]")
                                    .withStyle(ChatFormatting.GREEN)
                                    .withStyle(
                                            style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                                    "/eco shop buy " + shopId + " " + pageIndex + " " + slot + " 1"))));
                }

                if (entry.allowSell()) {
                    itemLine.append(" ")
                            .append(Component.literal("[SELL: " + symbol + entry.sellPrice() + "]")
                                    .withStyle(ChatFormatting.RED)
                                    .withStyle(style -> style.withClickEvent(new ClickEvent(
                                            ClickEvent.Action.RUN_COMMAND,
                                            "/eco shop sell " + shopId + " " + pageIndex + " " + slot + " 1"))));
                }

                message.append(itemLine).append("\n");
            } else if (entry.type() == CatalogEntry.EntryType.NAVIGATION) {
                // Convert Nav entries to links?
                // Most nav entries are Next Page / Prev Page.
                // We can render them if simpler.
                // For now, ignore purely visual nav buttons and just rely on /eco shop open
                // <id> <page+1>
            }
        }

        // Navigation footer
        MutableComponent footer = Component.literal("");
        if (pageIndex > 0) {
            footer.append(Component.literal("[<< Prev] ").withStyle(ChatFormatting.YELLOW)
                    .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/eco shop open " + shopId + " " + (pageIndex - 1)))));
        }
        if (pageIndex < shop.getPageCount() - 1) {
            footer.append(Component.literal("[Next >>]").withStyle(ChatFormatting.YELLOW)
                    .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/eco shop open " + shopId + " " + (pageIndex + 1)))));
        }
        message.append(footer);

        player.sendSystemMessage(message);
    }

    public static void handleTransaction(ServerPlayer player, String type, String shopId, int pageIndex, int slot,
            int quantity) {
        if (!EcoSystemConfig.ENABLE_CHAT_SHOP.get()) {
            player.sendSystemMessage(Component.translatable(LangKeys.ERROR_FEATURE_DISABLED));
            return;
        }

        ShopCatalog catalog = ServerEvents.getShopCatalog();
        ShopDefinition shop = catalog.getShop(shopId);
        if (shop == null)
            return;

        CatalogEntry entry = shop.getEntry(pageIndex, slot);
        if (entry == null || entry.type() != CatalogEntry.EntryType.SHOP) {
            player.sendSystemMessage(Component.translatable(LangKeys.SHOP_ITEM_NOT_FOUND));
            return;
        }

        Currency currency = ServerEvents.getCurrencyRegistry().getCurrency(shop.currencyId());
        if (currency == null)
            return;

        LedgerService ledger = ServerEvents.getLedgerService();
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.itemId()));
        ItemStack stack = new ItemStack(item, entry.quantity() * quantity); // entry.quantity is unit batch

        if ("buy".equalsIgnoreCase(type)) {
            if (!entry.allowBuy()) {
                player.sendSystemMessage(Component.translatable(LangKeys.SHOP_NO_BUY));
                return;
            }
            long totalPrice = entry.buyPrice() * quantity;

            // Check inv space
            if (player.getInventory().getFreeSlot() == -1 && player.getInventory().findSlotMatchingItem(stack) == -1) {
                player.sendSystemMessage(Component.translatable(LangKeys.ERROR_INSUFFICIENT_SPACE));
                return;
            }

            TransactionResult result = ledger.shopBuy(player.getUUID(), currency, stack, totalPrice, "chat_shop");
            if (result.success()) {
                player.getInventory().add(stack);
                player.sendSystemMessage(Component.translatable(LangKeys.SHOP_SUCCESS_BUY, entry.displayName(),
                        currency.formatAmount(totalPrice)));
            } else {
                player.sendSystemMessage(result.message());
            }

        } else if ("sell".equalsIgnoreCase(type)) {
            if (!entry.allowSell()) {
                player.sendSystemMessage(Component.translatable(LangKeys.SHOP_NO_BUY));
                return;
            }
            long totalPrice = entry.sellPrice() * quantity;

            // Check player has items
            int count = 0;
            for (ItemStack s : player.getInventory().items) {
                if (s.is(item))
                    count += s.getCount();
            }
            if (count < stack.getCount()) {
                player.sendSystemMessage(Component.translatable(LangKeys.ERROR_INSUFFICIENT_ITEMS, count));
                return;
            }

            TransactionResult result = ledger.shopSell(player.getUUID(), currency, totalPrice, "chat_shop");
            if (result.success()) {
                player.getInventory().clearOrCountMatchingItems(p -> p.is(item), stack.getCount(),
                        player.inventoryMenu.getCraftSlots());
                player.sendSystemMessage(Component.translatable(LangKeys.SHOP_SUCCESS_SELL, entry.displayName(),
                        currency.formatAmount(totalPrice)));
            } else {
                player.sendSystemMessage(result.message());
            }
        }
    }
}
