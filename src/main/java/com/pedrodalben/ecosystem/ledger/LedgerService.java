package com.pedrodalben.ecosystem.ledger;

import com.pedrodalben.ecosystem.audit.AuditLogger;
import com.pedrodalben.ecosystem.core.LangKeys;
import com.pedrodalben.ecosystem.core.ServerEvents;
import com.pedrodalben.ecosystem.currency.Currency;
import com.pedrodalben.ecosystem.currency.CurrencyRegistry;
import com.pedrodalben.ecosystem.currency.CurrencyType;
import com.pedrodalben.ecosystem.storage.StorageBackend;
import com.pedrodalben.ecosystem.tax.TaxResult;
import com.pedrodalben.ecosystem.tax.TaxService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Central service for all balance operations. Single source of truth for saldo
 * mutations.
 * All operations are atomic via per-player locking; items are never duplicated.
 */
public class LedgerService {
    private static final Logger LOGGER = LoggerFactory.getLogger("EcoSystem");

    private final StorageBackend storage;
    private final CurrencyRegistry currencyRegistry;
    private final TaxService taxService;
    private final AuditLogger auditLogger;

    // In-memory balance cache: AccountKey -> balance in minor units
    private final ConcurrentHashMap<AccountKey, AtomicLong> balances = new ConcurrentHashMap<>();

    // Per-player lock to prevent race conditions
    private final ConcurrentHashMap<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();

    // Track dirty accounts for batch persistence
    private final ConcurrentHashMap<AccountKey, Boolean> dirtyAccounts = new ConcurrentHashMap<>();

    public LedgerService(StorageBackend storage, CurrencyRegistry currencyRegistry,
            TaxService taxService, AuditLogger auditLogger) {
        this.storage = storage;
        this.currencyRegistry = currencyRegistry;
        this.taxService = taxService;
        this.auditLogger = auditLogger;
    }

    public void init() {
        // Load all balances from storage into cache
        Map<AccountKey, Long> loaded = storage.loadAllBalances();
        for (Map.Entry<AccountKey, Long> entry : loaded.entrySet()) {
            balances.put(entry.getKey(), new AtomicLong(entry.getValue()));
        }
    }

    // ==================== BALANCE QUERIES ====================

    public long getBalance(UUID playerUuid, String currencyId) {
        AccountKey key = new AccountKey(playerUuid, currencyId);
        AtomicLong bal = balances.get(key);
        return bal != null ? bal.get() : 0L;
    }

    public Map<String, Long> getAllBalances(UUID playerUuid) {
        Map<String, Long> result = new HashMap<>();
        for (Currency c : currencyRegistry.getAllCurrencies()) {
            result.put(c.getId(), getBalance(playerUuid, c.getId()));
        }
        return result;
    }

    public int getLoadedAccountCount() {
        return balances.size();
    }

    // ==================== DEPOSITS (ITEM → VIRTUAL) ====================

    /**
     * Deposit items from player inventory into virtual balance.
     * Only for ITEM_BACKED currencies.
     *
     * @param amount amount in minor units, or -1 for "all"
     */
    public TransactionResult depositFromInventory(ServerPlayer player, Currency currency, long amount) {
        if (currency.getType() != CurrencyType.ITEM_BACKED) {
            return TransactionResult.failure("§cThis currency is not item-backed.");
        }

        ReentrantLock lock = getPlayerLock(player.getUUID());
        lock.lock();
        try {
            // Count items in inventory
            Item targetItem = getItemForCurrency(currency);
            if (targetItem == null) {
                return TransactionResult.failure("§cInvalid item configured for currency.");
            }

            int totalItems = countItems(player, targetItem);
            if (totalItems == 0) {
                return TransactionResult.failure("§cYou don't have any items to deposit.");
            }

            // Calculate how many items to take
            long itemsToTake;
            long minorUnitsToCredit;
            if (amount < 0) {
                // Deposit all
                itemsToTake = totalItems;
                minorUnitsToCredit = itemsToTake / currency.getUnitAmount();
                // Adjust to only take full units
                itemsToTake = minorUnitsToCredit * currency.getUnitAmount();
            } else {
                minorUnitsToCredit = amount;
                itemsToTake = amount * currency.getUnitAmount();
            }

            if (itemsToTake <= 0 || minorUnitsToCredit <= 0) {
                return TransactionResult.failure("§cNot enough items for even one unit.");
            }

            if (itemsToTake > totalItems) {
                return TransactionResult.failure("§cYou don't have enough items. Need " + itemsToTake + ".");
            }

            // Calculate tax
            TaxResult taxResult = taxService.calculateTax(currency, TransactionType.DEPOSIT, minorUnitsToCredit);

            // Step 1: Remove items first (NEVER credit before confirming removal)
            int removed = removeItems(player, targetItem, (int) itemsToTake);
            if (removed < itemsToTake) {
                // Rollback: return removed items
                giveItems(player, targetItem, removed);
                return TransactionResult.failure(Component.translatable(LangKeys.ERROR_GENERIC));
            }

            // Step 2: Credit the balance
            long creditAmount = taxResult.netAmount();
            addBalance(player.getUUID(), currency.getId(), creditAmount);

            // Step 3: Handle tax destination
            handleTaxDestination(taxResult, currency, null);

            // Step 4: Log transaction
            Transaction tx = Transaction.create(TransactionType.DEPOSIT, currency.getId(),
                    minorUnitsToCredit, taxResult.taxAmount(), creditAmount,
                    null, player.getUUID(), "deposit_items=" + itemsToTake);
            auditLogger.log(tx);

            return TransactionResult.success(
                    Component.translatable(LangKeys.COMMAND_RECEIVED,
                            currency.formatAmount(creditAmount),
                            (taxResult.taxAmount() > 0 ? " (" + currency.formatAmount(taxResult.taxAmount()) + " tax)"
                                    : "")),
                    tx);
        } finally {
            lock.unlock();
        }
    }

    // ==================== WITHDRAWALS (VIRTUAL → ITEM) ====================

    public TransactionResult withdrawToInventory(ServerPlayer player, Currency currency, long amount) {
        if (currency.getType() != CurrencyType.ITEM_BACKED) {
            return TransactionResult.failure("§cThis currency is not item-backed.");
        }

        ReentrantLock lock = getPlayerLock(player.getUUID());
        lock.lock();
        try {
            // Calculate tax
            TaxResult taxResult = taxService.calculateTax(currency, TransactionType.WITHDRAW, amount);
            long totalCost = amount + taxResult.taxAmount(); // Player pays amount + tax

            // Check balance
            long currentBalance = getBalance(player.getUUID(), currency.getId());
            if (currentBalance < totalCost) {
                return TransactionResult.failure("§cInsufficient balance. Need " +
                        currency.formatAmount(totalCost) + " but have " + currency.formatAmount(currentBalance));
            }

            // Calculate items to give
            int itemsToGive = (int) (amount * currency.getUnitAmount());

            // Check inventory space
            Item targetItem = getItemForCurrency(currency);
            if (targetItem == null) {
                return TransactionResult.failure("§cInvalid item configured for currency.");
            }

            int freeSpace = countFreeSpace(player, targetItem);
            if (freeSpace < itemsToGive) {
                return TransactionResult.failure(
                        "§cNot enough inventory space. Need " + itemsToGive + " slots but have " + freeSpace + ".");
            }

            // Step 1: Debit balance first
            subtractBalance(player.getUUID(), currency.getId(), totalCost);

            // Step 2: Give items
            int given = giveItems(player, targetItem, itemsToGive);
            if (given < itemsToGive) {
                // Rollback: restore balance
                addBalance(player.getUUID(), currency.getId(), totalCost);
                return TransactionResult.failure(Component.translatable(LangKeys.ERROR_GENERIC));
            }

            // Step 3: Handle tax
            handleTaxDestination(taxResult, currency, null);

            // Step 4: Log
            Transaction tx = Transaction.create(TransactionType.WITHDRAW, currency.getId(),
                    amount, taxResult.taxAmount(), totalCost,
                    player.getUUID(), null, "withdraw_items=" + itemsToGive);
            auditLogger.log(tx);

            return TransactionResult.success(
                    Component.translatable(LangKeys.COMMAND_RECEIVED, // Reusing generic received/success or creating
                                                                      // specific
                            itemsToGive + " items",
                            (taxResult.taxAmount() > 0 ? " (" + currency.formatAmount(taxResult.taxAmount()) + " tax)"
                                    : "")),
                    tx);
        } finally {
            lock.unlock();
        }
    }

    // ==================== PAY (PLAYER → PLAYER) ====================

    // ==================== BALANCE CHECKS ====================

    public boolean hasBalance(UUID playerUuid, Currency currency, long amount) {
        return getBalance(playerUuid, currency.getId()) >= amount;
    }

    // ==================== PAY (PLAYER → PLAYER) ====================

    public TransactionResult pay(UUID fromUuid, UUID toUuid, Currency currency, long amount) {
        return transfer(fromUuid, toUuid, currency, amount, "pay");
    }

    public TransactionResult transfer(UUID fromUuid, UUID toUuid, Currency currency, long amount, String context) {
        if (amount <= 0) {
            return TransactionResult.failure("§cAmount must be positive.");
        }
        if (fromUuid.equals(toUuid)) {
            return TransactionResult.failure("§cYou cannot pay yourself.");
        }

        // Lock both players in UUID order to prevent deadlock
        UUID first = fromUuid.compareTo(toUuid) < 0 ? fromUuid : toUuid;
        UUID second = fromUuid.compareTo(toUuid) < 0 ? toUuid : fromUuid;

        ReentrantLock lockFirst = getPlayerLock(first);
        ReentrantLock lockSecond = getPlayerLock(second);
        lockFirst.lock();
        lockSecond.lock();
        try {
            // Calculate tax
            TaxResult taxResult = taxService.calculateTax(currency, TransactionType.PAY, amount);
            long totalDebit = amount; // Sender pays the amount
            long receiverGets = taxResult.netAmount(); // Receiver gets amount minus tax

            // Check sender balance
            long senderBalance = getBalance(fromUuid, currency.getId());
            if (senderBalance < totalDebit) {
                return TransactionResult.failure(Component.translatable(LangKeys.ERROR_INSUFFICIENT_FUNDS));
            }

            // Execute transfer
            subtractBalance(fromUuid, currency.getId(), totalDebit);
            addBalance(toUuid, currency.getId(), receiverGets);

            // Handle tax
            handleTaxDestination(taxResult, currency, null);

            // Log
            Transaction tx = Transaction.create(TransactionType.PAY, currency.getId(),
                    amount, taxResult.taxAmount(), receiverGets,
                    fromUuid, toUuid, context);
            auditLogger.log(tx);

            String targetName = toUuid.toString();
            if (ServerEvents.getServer() != null) {
                ServerPlayer target = ServerEvents.getServer().getPlayerList().getPlayer(toUuid);
                if (target != null)
                    targetName = target.getName().getString();
            }

            return TransactionResult.success(
                    Component.translatable(LangKeys.COMMAND_SENT,
                            currency.formatAmount(amount),
                            targetName),
                    tx);
        } finally {
            lockSecond.unlock();
            lockFirst.unlock();
        }
    }

    // ==================== ADMIN OPERATIONS ====================

    public TransactionResult adminGive(UUID targetUuid, Currency currency, long amount) {
        ReentrantLock lock = getPlayerLock(targetUuid);
        lock.lock();
        try {
            addBalance(targetUuid, currency.getId(), amount);
            Transaction tx = Transaction.create(TransactionType.ADMIN_GIVE, currency.getId(),
                    amount, 0, amount, null, targetUuid, "admin_give");
            auditLogger.log(tx);
            return TransactionResult.success(
                    Component.translatable(LangKeys.COMMAND_GAVE_ADMIN, currency.formatAmount(amount)), tx);
        } finally {
            lock.unlock();
        }
    }

    public TransactionResult adminTake(UUID targetUuid, Currency currency, long amount) {
        ReentrantLock lock = getPlayerLock(targetUuid);
        lock.lock();
        try {
            long current = getBalance(targetUuid, currency.getId());
            long toTake = Math.min(amount, current);
            subtractBalance(targetUuid, currency.getId(), toTake);
            Transaction tx = Transaction.create(TransactionType.ADMIN_TAKE, currency.getId(),
                    toTake, 0, toTake, targetUuid, null, "admin_take");
            auditLogger.log(tx);
            return TransactionResult.success(
                    Component.translatable(LangKeys.COMMAND_TOOK_ADMIN), tx);
        } finally {
            lock.unlock();
        }
    }

    public TransactionResult adminSet(UUID targetUuid, Currency currency, long amount) {
        ReentrantLock lock = getPlayerLock(targetUuid);
        lock.lock();
        try {
            setBalance(targetUuid, currency.getId(), amount);
            Transaction tx = Transaction.create(TransactionType.ADMIN_SET, currency.getId(),
                    amount, 0, amount, null, targetUuid, "admin_set");
            auditLogger.log(tx);
            return TransactionResult.success(
                    Component.translatable(LangKeys.COMMAND_SET_ADMIN), tx);
        } finally {
            lock.unlock();
        }
    }

    // ==================== SHOP OPERATIONS ====================

    public TransactionResult shopBuy(UUID playerUuid, Currency currency, ItemStack itemTemplate,
            long price, @Nullable String shopContext) {
        ReentrantLock lock = getPlayerLock(playerUuid);
        lock.lock();
        try {
            TaxResult taxResult = taxService.calculateTax(currency, TransactionType.SHOP_BUY, price);
            long totalCost = price + taxResult.taxAmount();

            long balance = getBalance(playerUuid, currency.getId());
            if (balance < totalCost) {
                return TransactionResult.failure(Component.translatable(LangKeys.ERROR_INSUFFICIENT_FUNDS));
            }

            subtractBalance(playerUuid, currency.getId(), totalCost);
            handleTaxDestination(taxResult, currency, null);

            Transaction tx = Transaction.create(TransactionType.SHOP_BUY, currency.getId(),
                    price, taxResult.taxAmount(), totalCost,
                    playerUuid, null, shopContext != null ? shopContext : "shop_buy");
            auditLogger.log(tx);

            return TransactionResult.success(
                    Component.translatable(LangKeys.SHOP_SUCCESS_BUY,
                            itemTemplate.getHoverName(), // Item name
                            currency.formatAmount(totalCost)),
                    tx);
        } finally {
            lock.unlock();
        }
    }

    public TransactionResult shopSell(UUID playerUuid, Currency currency, long price,
            @Nullable String shopContext) {
        ReentrantLock lock = getPlayerLock(playerUuid);
        lock.lock();
        try {
            TaxResult taxResult = taxService.calculateTax(currency, TransactionType.SHOP_SELL, price);
            long netCredit = taxResult.netAmount();

            addBalance(playerUuid, currency.getId(), netCredit);
            handleTaxDestination(taxResult, currency, null);

            Transaction tx = Transaction.create(TransactionType.SHOP_SELL, currency.getId(),
                    price, taxResult.taxAmount(), netCredit,
                    null, playerUuid, shopContext != null ? shopContext : "shop_sell");
            auditLogger.log(tx);

            return TransactionResult.success(
                    Component.translatable(LangKeys.SHOP_SUCCESS_SELL,
                            "-", // We don't have item info here easily without changing signature
                            currency.formatAmount(netCredit)),
                    tx);
        } finally {
            lock.unlock();
        }
    }

    // ==================== INTERNAL BALANCE OPS ====================

    private void addBalance(UUID playerUuid, String currencyId, long amount) {
        AccountKey key = new AccountKey(playerUuid, currencyId);
        balances.computeIfAbsent(key, k -> new AtomicLong(0)).addAndGet(amount);
        dirtyAccounts.put(key, true);
    }

    private void subtractBalance(UUID playerUuid, String currencyId, long amount) {
        AccountKey key = new AccountKey(playerUuid, currencyId);
        AtomicLong bal = balances.computeIfAbsent(key, k -> new AtomicLong(0));
        bal.addAndGet(-amount);
        dirtyAccounts.put(key, true);
    }

    private void setBalance(UUID playerUuid, String currencyId, long amount) {
        AccountKey key = new AccountKey(playerUuid, currencyId);
        balances.computeIfAbsent(key, k -> new AtomicLong(0)).set(amount);
        dirtyAccounts.put(key, true);
    }

    // ==================== INVENTORY HELPERS ====================

    @Nullable
    private Item getItemForCurrency(Currency currency) {
        ResourceLocation itemId = currency.getItemId();
        if (itemId == null)
            return null;
        return BuiltInRegistries.ITEM.get(itemId);
    }

    private int countItems(ServerPlayer player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private int removeItems(ServerPlayer player, Item item, int amount) {
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
        return amount - remaining;
    }

    private int giveItems(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, item.getDefaultMaxStackSize());
            ItemStack stack = new ItemStack(item, stackSize);
            if (!player.getInventory().add(stack)) {
                // Inventory full — drop remaining items at player's feet would be bad;
                // We already checked space; this shouldn't happen
                break;
            }
            remaining -= stackSize;
        }
        return amount - remaining;
    }

    private int countFreeSpace(ServerPlayer player, Item item) {
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

    // ==================== TAX DESTINATION ====================

    private void handleTaxDestination(TaxResult taxResult, Currency currency, @Nullable UUID shopOwner) {
        if (taxResult.taxAmount() <= 0)
            return;

        switch (taxResult.destination()) {
            case SERVER_ACCOUNT -> {
                // Credit to a virtual "server" account
                AccountKey serverKey = new AccountKey(new UUID(0, 0), currency.getId());
                balances.computeIfAbsent(serverKey, k -> new AtomicLong(0)).addAndGet(taxResult.taxAmount());
                dirtyAccounts.put(serverKey, true);
            }
            case SHOP_OWNER -> {
                if (shopOwner != null) {
                    addBalance(shopOwner, currency.getId(), taxResult.taxAmount());
                }
                // If no owner, tax sinks
            }
            case SINK -> {
                // Money disappears from the economy
            }
        }
    }

    // ==================== PERSISTENCE ====================

    public void loadPlayerData(UUID playerUuid) {
        Map<String, Long> playerBalances = storage.loadPlayerBalances(playerUuid);
        for (Map.Entry<String, Long> entry : playerBalances.entrySet()) {
            AccountKey key = new AccountKey(playerUuid, entry.getKey());
            balances.put(key, new AtomicLong(entry.getValue()));
        }
    }

    public void flushPlayer(UUID playerUuid) {
        Map<String, Long> toSave = new HashMap<>();
        for (Map.Entry<AccountKey, AtomicLong> entry : balances.entrySet()) {
            if (entry.getKey().playerUuid().equals(playerUuid)) {
                toSave.put(entry.getKey().currencyId(), entry.getValue().get());
                dirtyAccounts.remove(entry.getKey());
            }
        }
        if (!toSave.isEmpty()) {
            storage.savePlayerBalances(playerUuid, toSave);
        }
    }

    public void flushAll() {
        Map<AccountKey, Long> allDirty = new HashMap<>();
        for (AccountKey key : dirtyAccounts.keySet()) {
            AtomicLong bal = balances.get(key);
            if (bal != null) {
                allDirty.put(key, bal.get());
            }
        }
        dirtyAccounts.clear();
        if (!allDirty.isEmpty()) {
            storage.saveBalances(allDirty);
        }
    }

    /**
     * Get or create a lock for the given player.
     */
    private ReentrantLock getPlayerLock(UUID playerUuid) {
        return playerLocks.computeIfAbsent(playerUuid, k -> new ReentrantLock());
    }
}
