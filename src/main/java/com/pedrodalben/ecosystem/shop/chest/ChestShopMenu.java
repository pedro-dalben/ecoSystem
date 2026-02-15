package com.pedrodalben.ecosystem.shop.chest;

import com.pedrodalben.ecosystem.core.EcoSystemMod;
import com.pedrodalben.ecosystem.core.LangKeys;
import com.pedrodalben.ecosystem.core.ServerEvents;
import com.pedrodalben.ecosystem.ledger.LedgerService;
import com.pedrodalben.ecosystem.ledger.TransactionResult;
import com.pedrodalben.ecosystem.currency.Currency;
import com.pedrodalben.ecosystem.net.ModPayloads;
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

/**
 * Chest shop menu — displays shop terminal's listings for buying/selling.
 * Includes the player's inventory for convenience.
 */
public class ChestShopMenu extends AbstractContainerMenu {

    private final BlockPos terminalPos;
    private List<ShopTerminalBlockEntity.ShopListing> listings = List.of();
    private String ownerName = "";
    private String currencyId = "money";

    // Server constructor
    public ChestShopMenu(int containerId, Inventory playerInventory, BlockPos pos,
            ShopTerminalBlockEntity terminalBE) {
        super(EcoSystemMod.CHEST_SHOP_MENU.get(), containerId);
        this.terminalPos = pos;
        this.listings = terminalBE.getListings();
        this.ownerName = terminalBE.getOwnerName();
        this.currencyId = terminalBE.getCurrencyId();

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
        // We receive minimal data; listings come via separate sync or are embedded
        return new ChestShopMenu(containerId, playerInventory, pos, createDummyBE(pos, buf));
    }

    private static ShopTerminalBlockEntity createDummyBE(BlockPos pos, FriendlyByteBuf buf) {
        // Read shop data from buffer for client display
        ShopTerminalBlockEntity dummy = new ShopTerminalBlockEntity(pos,
                EcoSystemMod.SHOP_TERMINAL_BLOCK.get().defaultBlockState());
        String owner = buf.readUtf();
        String currency = buf.readUtf();
        int listingCount = buf.readVarInt();
        dummy.setOwnerName(owner);
        dummy.setCurrencyId(currency);
        for (int i = 0; i < listingCount; i++) {
            dummy.addListing(new ShopTerminalBlockEntity.ShopListing(
                    buf.readUtf(), buf.readUtf(),
                    buf.readLong(), buf.readLong(),
                    buf.readBoolean(), buf.readBoolean()));
        }
        return dummy;
    }

    /**
     * Server-side buy operation for a listed item.
     */
    public TransactionResult buyItem(ServerPlayer player, int listingIndex, int quantity) {
        if (listingIndex < 0 || listingIndex >= listings.size()) {
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_INVALID_INDEX));
        }
        ShopTerminalBlockEntity.ShopListing listing = listings.get(listingIndex);
        if (!listing.canBuy()) {
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_NOT_FOR_SALE));
        }

        // Check stock in linked chest
        ShopTerminalBlockEntity be = getTerminalBE(player);
        if (be == null)
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_SHOP_NOT_FOUND));

        Container chest = be.getLinkedChest();
        if (chest == null)
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_NO_LINKED_CHEST));

        int stock = be.countStock(listing.itemId());
        if (stock < quantity) {
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_OUT_OF_STOCK, stock));
        }

        // Process payment
        Currency currency = ServerEvents.getCurrencyRegistry().getCurrency(currencyId);
        if (currency == null)
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_CURRENCY_NOT_FOUND));

        LedgerService ledger = ServerEvents.getLedgerService();
        long totalPrice = listing.buyPrice() * quantity;

        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(listing.itemId()));
        ItemStack itemStack = new ItemStack(item, quantity);

        TransactionResult result = ledger.shopBuy(player.getUUID(), currency, itemStack, totalPrice,
                "chest_shop:" + listing.itemId() + "@" + terminalPos.toShortString());

        if (result.success()) {
            // Remove from chest
            removeFromChest(chest, listing.itemId(), quantity);
            // Give to player
            if (!player.getInventory().add(itemStack.copy())) {
                player.drop(itemStack.copy(), false);
            }
            // Credit shop owner
            ledger.adminGive(be.getOwnerUuid(), currency, totalPrice);
            ModPayloads.sendBalanceSync(player);
        }

        return result;
    }

    /**
     * Server-side sell operation.
     */
    public TransactionResult sellItem(ServerPlayer player, int listingIndex, int quantity) {
        if (listingIndex < 0 || listingIndex >= listings.size()) {
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_INVALID_INDEX));
        }
        ShopTerminalBlockEntity.ShopListing listing = listings.get(listingIndex);
        if (!listing.canSell()) {
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_SHOP_NO_BUY));
        }

        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(listing.itemId()));
        int playerCount = countPlayerItems(player, item);
        if (playerCount < quantity) {
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_INSUFFICIENT_ITEMS, playerCount));
        }

        Currency currency = ServerEvents.getCurrencyRegistry().getCurrency(currencyId);
        if (currency == null)
            return TransactionResult.failure(Component.translatable(LangKeys.ERROR_CURRENCY_NOT_FOUND));

        LedgerService ledger = ServerEvents.getLedgerService();
        long totalPrice = listing.sellPrice() * quantity;

        // Remove items from player
        removeFromPlayer(player, item, quantity);

        TransactionResult result = ledger.shopSell(player.getUUID(), currency, totalPrice,
                "chest_shop:" + listing.itemId() + "@" + terminalPos.toShortString());

        if (result.success()) {
            // Add items to chest
            ShopTerminalBlockEntity be = getTerminalBE(player);
            if (be != null) {
                Container chest = be.getLinkedChest();
                if (chest != null) {
                    addToChest(chest, item, quantity);
                }
            }
            ModPayloads.sendBalanceSync(player);
        } else {
            // Rollback: give items back
            player.getInventory().add(new ItemStack(item, quantity));
        }

        return result;
    }

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

    private int countPlayerItems(Player player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item))
                count += stack.getCount();
        }
        return count;
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

    public List<ShopTerminalBlockEntity.ShopListing> getListings() {
        return listings;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getCurrencyId() {
        return currencyId;
    }

    public BlockPos getTerminalPos() {
        return terminalPos;
    }

    public static MenuProvider provider(BlockPos pos, ShopTerminalBlockEntity terminalBE) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable(LangKeys.SHOP_HEADER, terminalBE.getOwnerName());
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
                return new ChestShopMenu(containerId, playerInventory, pos, terminalBE);
            }
        };
    }
}
