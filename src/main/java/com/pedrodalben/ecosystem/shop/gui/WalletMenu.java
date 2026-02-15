package com.pedrodalben.ecosystem.shop.gui;

import com.pedrodalben.ecosystem.core.EcoSystemMod;
import com.pedrodalben.ecosystem.core.ServerEvents;
import com.pedrodalben.ecosystem.ledger.LedgerService;
import com.pedrodalben.ecosystem.net.ModPayloads;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * Wallet menu – displays player's balances across all currencies.
 * Uses DataSlots to sync balance values to the client.
 */
public class WalletMenu extends AbstractContainerMenu {

    // Maximum currencies we track via DataSlots (expandable)
    private static final int MAX_CURRENCIES = 16;

    private final DataSlot[] balanceSlots = new DataSlot[MAX_CURRENCIES];
    private String[] currencyIds = new String[MAX_CURRENCIES];
    private int currencyCount = 0;

    // Server constructor
    public WalletMenu(int containerId, Inventory playerInventory, Player player) {
        super(EcoSystemMod.WALLET_MENU.get(), containerId);

        // Initialize data slots for balance sync
        for (int i = 0; i < MAX_CURRENCIES; i++) {
            balanceSlots[i] = DataSlot.standalone();
            addDataSlot(balanceSlots[i]);
        }

        // Load balances if on server
        if (player instanceof ServerPlayer serverPlayer) {
            LedgerService ledger = ServerEvents.getLedgerService();
            if (ledger != null) {
                Map<String, Long> balances = ledger.getAllBalances(serverPlayer.getUUID());
                int idx = 0;
                for (Map.Entry<String, Long> entry : balances.entrySet()) {
                    if (idx >= MAX_CURRENCIES)
                        break;
                    currencyIds[idx] = entry.getKey();
                    // Store as int (DataSlot is int only) — we split long into high/low
                    balanceSlots[idx].set((int) (entry.getValue() & 0xFFFFFFFFL));
                    idx++;
                }
                currencyCount = idx;

                // Also send full balance sync via packet
                ModPayloads.sendBalanceSync(serverPlayer);
            }
        }
    }

    // Client constructor (from IContainerFactory)
    public static WalletMenu clientConstructor(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        return new WalletMenu(containerId, playerInventory, playerInventory.player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true; // Always valid (no block association)
    }

    public int getCurrencyCount() {
        return currencyCount;
    }

    public String getCurrencyId(int index) {
        return currencyIds[index];
    }

    public int getBalanceRaw(int index) {
        return balanceSlots[index].get();
    }

    /**
     * MenuProvider for opening this menu from commands/blocks.
     */
    public static MenuProvider provider() {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("gui.ecosystem.wallet");
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
                return new WalletMenu(containerId, playerInventory, player);
            }
        };
    }
}
