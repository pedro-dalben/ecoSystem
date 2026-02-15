package com.pedrodalben.ecosystem.net;

import com.pedrodalben.ecosystem.core.EcoSystemMod;
import com.pedrodalben.ecosystem.core.LangKeys;
import com.pedrodalben.ecosystem.core.ServerEvents;
import com.pedrodalben.ecosystem.currency.Currency;
import com.pedrodalben.ecosystem.ledger.LedgerService;
import com.pedrodalben.ecosystem.ledger.TransactionResult;
import com.pedrodalben.ecosystem.shop.gui.CatalogEntry;
import com.pedrodalben.ecosystem.shop.gui.ShopCatalog;
import com.pedrodalben.ecosystem.shop.gui.ShopDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers all network payloads for the mod.
 */
public class ModPayloads {

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(EcoSystemMod.MOD_ID).versioned("1");

        // Server → Client
        registrar.playToClient(
                BalanceSyncPayload.TYPE,
                BalanceSyncPayload.STREAM_CODEC,
                ModPayloads::handleBalanceSyncOnClient);

        registrar.playToClient(
                CatalogSyncPayload.TYPE,
                CatalogSyncPayload.STREAM_CODEC,
                ModPayloads::handleCatalogSyncOnClient);

        // Client → Server
        registrar.playToServer(
                ShopActionPayload.TYPE,
                ShopActionPayload.STREAM_CODEC,
                ModPayloads::handleShopActionOnServer);
    }

    // ==================== CLIENT HANDLERS ====================

    private static void handleBalanceSyncOnClient(BalanceSyncPayload payload, IPayloadContext context) {
        ClientBalanceCache.update(payload.balances());
    }

    private static void handleCatalogSyncOnClient(CatalogSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Convert payload to ShopDefinition and pass to the active screen
            ShopDefinition def = payload.toDefinition();
            ClientCatalogCache.update(def);
        });
    }

    // ==================== SERVER HANDLER ====================

    private static void handleShopActionOnServer(ShopActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            LedgerService ledger = ServerEvents.getLedgerService();
            ShopCatalog catalog = ServerEvents.getShopCatalog();

            if (ledger == null || catalog == null)
                return;

            // Validate currency
            Currency currency = ServerEvents.getCurrencyRegistry().getCurrency(payload.currencyId());
            if (currency == null) {
                player.sendSystemMessage(Component.translatable(LangKeys.ERROR_CURRENCY_NOT_FOUND));
                return;
            }

            // Look up catalog entry by shopId + page + slot
            CatalogEntry entry = catalog.getEntry(payload.shopId(), payload.page(), payload.slotId());
            if (entry == null) {
                player.sendSystemMessage(Component.translatable(LangKeys.SHOP_ITEM_NOT_FOUND));
                return;
            }

            // Only SHOP entries can be traded
            if (entry.type() != CatalogEntry.EntryType.SHOP) {
                player.sendSystemMessage(Component.translatable(LangKeys.ERROR_NOT_FOR_SALE));
                return;
            }

            // Resolve the Minecraft item
            ResourceLocation itemLoc = ResourceLocation.parse(entry.itemId());
            Item item = BuiltInRegistries.ITEM.get(itemLoc);
            int qty = entry.quantity() * payload.quantity();
            ItemStack itemStack = new ItemStack(item, qty);

            TransactionResult result;

            if (payload.action() == ShopActionPayload.Action.BUY) {
                if (!entry.allowBuy() || entry.buyPrice() <= 0) {
                    player.sendSystemMessage(Component.translatable(LangKeys.ERROR_NOT_FOR_SALE));
                    return;
                }

                long totalPrice = entry.buyPrice() * payload.quantity();
                result = ledger.shopBuy(player.getUUID(), currency, itemStack, totalPrice,
                        "gui_shop:" + payload.shopId() + ":" + entry.itemId());

                if (result.success()) {
                    // Give item to player
                    if (!player.getInventory().add(itemStack.copy())) {
                        player.drop(itemStack.copy(), false);
                    }
                }
            } else {
                // SELL
                if (!entry.allowSell() || entry.sellPrice() <= 0) {
                    player.sendSystemMessage(Component.translatable(LangKeys.SHOP_NO_BUY));
                    return;
                }

                int count = countItems(player, item, qty);
                if (count < qty) {
                    player.sendSystemMessage(
                            Component.translatable(LangKeys.ERROR_INSUFFICIENT_ITEMS, String.valueOf(count)));
                    return;
                }

                long totalPrice = entry.sellPrice() * payload.quantity();
                removeItems(player, item, qty);
                result = ledger.shopSell(player.getUUID(), currency, totalPrice,
                        "gui_shop:" + payload.shopId() + ":" + entry.itemId());

                if (!result.success()) {
                    // Rollback: give items back
                    player.getInventory().add(new ItemStack(item, qty));
                }
            }

            player.sendSystemMessage(result.message());
            if (result.success()) {
                sendBalanceSync(player);
            }
        });
    }

    // ==================== UTILITY ====================

    /**
     * Send balance sync to a specific player.
     */
    public static void sendBalanceSync(ServerPlayer player) {
        LedgerService ledger = ServerEvents.getLedgerService();
        if (ledger == null)
            return;

        var balances = ledger.getAllBalances(player.getUUID());
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                player,
                new BalanceSyncPayload(balances));
    }

    /**
     * Send shop catalog data to a player (when opening a shop screen).
     */
    public static void sendCatalogSync(ServerPlayer player, String shopId) {
        ShopCatalog catalog = ServerEvents.getShopCatalog();
        if (catalog == null)
            return;

        ShopDefinition def = catalog.getShop(shopId);
        if (def == null)
            return;

        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                player,
                CatalogSyncPayload.fromDefinition(def));
    }

    private static int countItems(ServerPlayer player, Item item, int maxCount) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
                if (count >= maxCount)
                    return count;
            }
        }
        return count;
    }

    private static void removeItems(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                int toRemove = Math.min(remaining, stack.getCount());
                stack.shrink(toRemove);
                remaining -= toRemove;
                if (stack.isEmpty()) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                }
            }
        }
    }
}
