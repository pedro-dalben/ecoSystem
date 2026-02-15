package com.pedrodalben.ecosystem.shop.chest;

import com.pedrodalben.ecosystem.core.EcoSystemMod;
import com.pedrodalben.ecosystem.core.LangKeys;
import com.pedrodalben.ecosystem.core.ServerEvents;
import com.pedrodalben.ecosystem.ledger.LedgerService;
import com.pedrodalben.ecosystem.ledger.TransactionResult;
import com.pedrodalben.ecosystem.currency.Currency;
import com.pedrodalben.ecosystem.net.ModPayloads;
import com.pedrodalben.ecosystem.tax.TaxResult;
import com.pedrodalben.ecosystem.tax.TaxService;
import com.pedrodalben.ecosystem.ledger.TransactionType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Chest shop menu — displays shop terminal's listings for buying/selling.
 * Supports both player shops (chest-backed, stock limited) and
 * admin shops (unlimited stock, no chest required).
 *
 * All transactions are protected by a per-terminal ReentrantLock to
 * prevent race conditions from concurrent access.
 */
public class ChestShopMenu extends AbstractContainerMenu {

    private final BlockPos terminalPos;
    private List<ShopTerminalBlockEntity.ShopListing> listings = List.of();
    private String ownerName = "";
    private String currencyId = "money";
    private boolean adminShop = false;

    // Server constructor
    public ChestShopMenu(int containerId, Inventory playerInventory, BlockPos pos,
            ShopTerminalBlockEntity terminalBE) {
        super(EcoSystemMod.CHEST_SHOP_MENU.get(), containerId);
        this.terminalPos = pos;
        this.listings = terminalBE.getListings();
        this.ownerName = terminalBE.getOwnerName();
        this.currencyId = terminalBE.getCurrencyId();
        this.adminShop = terminalBE.isAdminShop();

        // Player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 198));
        }
    }

    // Client constructor
    public static ChestShopMenu clientConstructor(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        return new ChestShopMenu(containerId, playerInventory, pos, createDummyBE(pos, buf));
    }

    private static ShopTerminalBlockEntity createDummyBE(BlockPos pos, FriendlyByteBuf buf) {
        ShopTerminalBlockEntity dummy = new ShopTerminalBlockEntity(pos,
                EcoSystemMod.SHOP_TERMINAL_BLOCK.get().defaultBlockState());
        String owner = buf.readUtf();
        String currency = buf.readUtf();
        boolean isAdmin = buf.readBoolean();
        int listingCount = buf.readVarInt();
        dummy.setOwnerName(owner);
        dummy.setCurrencyId(currency);
        dummy.setAdminShop(isAdmin);
        for (int i = 0; i < listingCount; i++) {
            dummy.addListing(new ShopTerminalBlockEntity.ShopListing(
                    buf.readUtf(), buf.readUtf(),
                    buf.readLong(), buf.readLong(),
                    buf.readBoolean(), buf.readBoolean()));
        }
        return dummy;
    }

    // ==================== BUY ====================

    /**
     * Server-side buy operation for a listed item.
     *
     * <p>
     * Player shop: deducts from chest stock, credits owner (after tax).
     * <p>
     * Admin shop: unlimited stock, tax goes to SINK, no owner credit.
     */
    public TransactionResult buyItem(ServerPlayer player, int listingIndex, int quantity) {
        if (listingIndex < 0 || listingIndex >= listings.size()) {
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_INVALID_INDEX));
        }
        ShopTerminalBlockEntity.ShopListing listing = listings.get(listingIndex);
        if (!listing.canBuy()) {
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_NOT_FOR_SALE));
        }

        // Permission check
        if (!ShopPermission.SHOP_BUY.hasPermission(player)) {
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_NO_PERMISSION));
        }

        ShopTerminalBlockEntity be = getTerminalBE(player);
        if (be == null)
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_SHOP_NOT_FOUND));

        // Acquire per-terminal lock
        ReentrantLock lock = be.getTransactionLock();
        lock.lock();
        try {
            return executeBuy(player, be, listing, quantity);
        } finally {
            lock.unlock();
        }
    }

    private TransactionResult executeBuy(ServerPlayer player, ShopTerminalBlockEntity be,
            ShopTerminalBlockEntity.ShopListing listing, int quantity) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(listing.itemId()));
        ItemStack itemStack = new ItemStack(item, quantity);

        // Check player has inventory space
        if (countFreeSpace(player, item) < quantity) {
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_INSUFFICIENT_SPACE));
        }

        if (adminShop) {
            // Admin shop — unlimited stock, no chest
            return executeAdminBuy(player, be, listing, item, itemStack, quantity);
        } else {
            // Player shop — deduct from chest
            return executePlayerBuy(player, be, listing, item, itemStack, quantity);
        }
    }

    private TransactionResult executePlayerBuy(ServerPlayer player, ShopTerminalBlockEntity be,
            ShopTerminalBlockEntity.ShopListing listing,
            Item item, ItemStack itemStack, int quantity) {
        Container chest = be.getLinkedChest();
        if (chest == null)
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_NO_LINKED_CHEST));

        int stock = be.countStock(listing.itemId());
        if (stock < quantity) {
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_OUT_OF_STOCK, stock));
        }

        Currency currency = ServerEvents.getCurrencyRegistry().getCurrency(currencyId);
        if (currency == null)
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_CURRENCY_NOT_FOUND));

        LedgerService ledger = ServerEvents.getLedgerService();
        long totalPrice = listing.buyPrice() * quantity;

        // Calculate tax
        TaxService taxService = ServerEvents.getTaxService();
        TaxResult taxResult = taxService.calculateTax(currency, TransactionType.SHOP_BUY, totalPrice);

        TransactionResult result = ledger.shopBuy(player.getUUID(), currency, itemStack, totalPrice,
                "chest_shop:" + listing.itemId() + "@" + terminalPos.toShortString());

        if (result.success()) {
            removeFromChest(chest, listing.itemId(), quantity);
            if (!player.getInventory().add(itemStack.copy())) {
                player.drop(itemStack.copy(), false);
            }
            // Credit owner with net amount (after tax)
            long ownerProceeds = taxResult.netAmount();
            ledger.adminGive(be.getOwnerUuid(), currency, ownerProceeds);
            ModPayloads.sendBalanceSync(player);
        }

        return result;
    }

    private TransactionResult executeAdminBuy(ServerPlayer player, ShopTerminalBlockEntity be,
            ShopTerminalBlockEntity.ShopListing listing,
            Item item, ItemStack itemStack, int quantity) {
        Currency currency = ServerEvents.getCurrencyRegistry().getCurrency(currencyId);
        if (currency == null)
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_CURRENCY_NOT_FOUND));

        LedgerService ledger = ServerEvents.getLedgerService();
        long totalPrice = listing.buyPrice() * quantity;

        TransactionResult result = ledger.shopBuy(player.getUUID(), currency, itemStack, totalPrice,
                "admin_shop:" + listing.itemId());

        if (result.success()) {
            // Admin shop: just give the items, no chest interaction
            if (!player.getInventory().add(itemStack.copy())) {
                player.drop(itemStack.copy(), false);
            }
            // Tax goes to SINK — no owner credit needed for admin shops
            ModPayloads.sendBalanceSync(player);
        }

        return result;
    }

    // ==================== SELL ====================

    /**
     * Server-side sell operation.
     *
     * <p>
     * Player shop: adds items to chest (if space), owner pays the seller.
     * <p>
     * Admin shop: unlimited buying, payment from server/sink.
     */
    public TransactionResult sellItem(ServerPlayer player, int listingIndex, int quantity) {
        if (listingIndex < 0 || listingIndex >= listings.size()) {
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_INVALID_INDEX));
        }
        ShopTerminalBlockEntity.ShopListing listing = listings.get(listingIndex);
        if (!listing.canSell()) {
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_SHOP_NO_BUY));
        }

        // Permission check
        if (!ShopPermission.SHOP_SELL.hasPermission(player)) {
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_NO_PERMISSION));
        }

        ShopTerminalBlockEntity be = getTerminalBE(player);
        if (be == null)
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_SHOP_NOT_FOUND));

        // Acquire per-terminal lock
        ReentrantLock lock = be.getTransactionLock();
        lock.lock();
        try {
            return executeSell(player, be, listing, quantity);
        } finally {
            lock.unlock();
        }
    }

    private TransactionResult executeSell(ServerPlayer player, ShopTerminalBlockEntity be,
            ShopTerminalBlockEntity.ShopListing listing, int quantity) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(listing.itemId()));
        int playerCount = countPlayerItems(player, item);
        if (playerCount < quantity) {
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_INSUFFICIENT_ITEMS, playerCount));
        }

        if (adminShop) {
            return executeAdminSell(player, be, listing, item, quantity);
        } else {
            return executePlayerSell(player, be, listing, item, quantity);
        }
    }

    private TransactionResult executePlayerSell(ServerPlayer player, ShopTerminalBlockEntity be,
            ShopTerminalBlockEntity.ShopListing listing,
            Item item, int quantity) {
        Container chest = be.getLinkedChest();
        if (chest == null)
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_NO_LINKED_CHEST));

        // Check chest has space before accepting
        if (countChestFreeSpace(chest, item) < quantity) {
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_CHEST_FULL));
        }

        Currency currency = ServerEvents.getCurrencyRegistry().getCurrency(currencyId);
        if (currency == null)
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_CURRENCY_NOT_FOUND));

        LedgerService ledger = ServerEvents.getLedgerService();
        long totalPrice = listing.sellPrice() * quantity;

        // Check that the shop owner has enough balance to pay
        long ownerBalance = ledger.getBalance(be.getOwnerUuid(), currencyId);
        if (ownerBalance < totalPrice) {
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_OWNER_INSUFFICIENT_FUNDS));
        }

        // Remove items from player first
        removeFromPlayer(player, item, quantity);

        TransactionResult result = ledger.shopSell(player.getUUID(), currency, totalPrice,
                "chest_shop:" + listing.itemId() + "@" + terminalPos.toShortString());

        if (result.success()) {
            addToChest(chest, item, quantity);
            // Deduct from owner balance
            ledger.adminTake(be.getOwnerUuid(), currency, totalPrice);
            ModPayloads.sendBalanceSync(player);
        } else {
            // Rollback: give items back
            player.getInventory().add(new ItemStack(item, quantity));
        }

        return result;
    }

    private TransactionResult executeAdminSell(ServerPlayer player, ShopTerminalBlockEntity be,
            ShopTerminalBlockEntity.ShopListing listing,
            Item item, int quantity) {
        Currency currency = ServerEvents.getCurrencyRegistry().getCurrency(currencyId);
        if (currency == null)
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_CURRENCY_NOT_FOUND));

        LedgerService ledger = ServerEvents.getLedgerService();
        long totalPrice = listing.sellPrice() * quantity;

        // Remove from player
        removeFromPlayer(player, item, quantity);

        TransactionResult result = ledger.shopSell(player.getUUID(), currency, totalPrice,
                "admin_shop:" + listing.itemId());

        if (result.success()) {
            // Admin shop: items are consumed (destroyed), money comes from void
            ModPayloads.sendBalanceSync(player);
        } else {
            // Rollback
            player.getInventory().add(new ItemStack(item, quantity));
        }

        return result;
    }

    // ==================== MENU OVERRIDES ====================

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(terminalPos.getX() + 0.5, terminalPos.getY() + 0.5,
                terminalPos.getZ() + 0.5) < 64.0;
    }

    // ==================== HELPERS ====================

    @Nullable
    private ShopTerminalBlockEntity getTerminalBE(Player player) {
        if (player.level().getBlockEntity(terminalPos) instanceof ShopTerminalBlockEntity be) {
            return be;
        }
        return null;
    }

    private void removeFromChest(Container chest, String itemId, int amount) {
        int remaining = amount;
        for (int i = 0; i < chest.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = chest.getItem(i);
            if (!stack.isEmpty()
                    && stack.getItem().builtInRegistryHolder().key().location().toString().equals(itemId)) {
                int toRemove = Math.min(remaining, stack.getCount());
                stack.shrink(toRemove);
                remaining -= toRemove;
                if (stack.isEmpty())
                    chest.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private void addToChest(Container chest, Item item, int amount) {
        int remaining = amount;
        // Try to stack first
        for (int i = 0; i < chest.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = chest.getItem(i);
            if (stack.is(item) && stack.getCount() < stack.getMaxStackSize()) {
                int toAdd = Math.min(remaining, stack.getMaxStackSize() - stack.getCount());
                stack.grow(toAdd);
                remaining -= toAdd;
            }
        }
        // Then fill empty slots
        for (int i = 0; i < chest.getContainerSize() && remaining > 0; i++) {
            if (chest.getItem(i).isEmpty()) {
                int toAdd = Math.min(remaining, item.getDefaultMaxStackSize());
                chest.setItem(i, new ItemStack(item, toAdd));
                remaining -= toAdd;
            }
        }
    }

    /**
     * Count how much free space is available in the chest for a given item.
     */
    private int countChestFreeSpace(Container chest, Item item) {
        int space = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (stack.isEmpty()) {
                space += item.getDefaultMaxStackSize();
            } else if (stack.is(item) && stack.getCount() < stack.getMaxStackSize()) {
                space += stack.getMaxStackSize() - stack.getCount();
            }
        }
        return space;
    }

    private int countPlayerItems(Player player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item))
                count += stack.getCount();
        }
        return count;
    }

    private int countFreeSpace(Player player, Item item) {
        int space = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                space += item.getDefaultMaxStackSize();
            } else if (stack.is(item) && stack.getCount() < stack.getMaxStackSize()) {
                space += stack.getMaxStackSize() - stack.getCount();
            }
        }
        return space;
    }

    private void removeFromPlayer(Player player, Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                int toRemove = Math.min(remaining, stack.getCount());
                stack.shrink(toRemove);
                remaining -= toRemove;
                if (stack.isEmpty())
                    player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    // ==================== ACCESSORS ====================

    public List<ShopTerminalBlockEntity.ShopListing> getListings() {
        return listings;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getCurrencyId() {
        return currencyId;
    }

    public boolean isAdminShop() {
        return adminShop;
    }

    public BlockPos getTerminalPos() {
        return terminalPos;
    }

    public static MenuProvider provider(BlockPos pos, ShopTerminalBlockEntity terminalBE) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                if (terminalBE.isAdminShop()) {
                    return Component.translatable(LangKeys.SHOP_ADMIN_MODE);
                }
                return Component.translatable(LangKeys.SHOP_HEADER, terminalBE.getOwnerName());
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
                return new ChestShopMenu(containerId, playerInventory, pos, terminalBE);
            }
        };
    }
}
