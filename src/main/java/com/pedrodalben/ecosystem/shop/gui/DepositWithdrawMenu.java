package com.pedrodalben.ecosystem.shop.gui;

import com.pedrodalben.ecosystem.core.EcoSystemMod;
import com.pedrodalben.ecosystem.core.LangKeys;
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
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for deposit/withdraw operations (item-backed currencies).
 * Contains player inventory slots for item interaction.
 */
public class DepositWithdrawMenu extends AbstractContainerMenu {

    private final DataSlot currentBalance = DataSlot.standalone();
    private final DataSlot taxPreview = DataSlot.standalone();
    private String currencyId = "";
    private boolean isDeposit = true;

    // Server constructor
    public DepositWithdrawMenu(int containerId, Inventory playerInventory, String currencyId, boolean isDeposit) {
        super(EcoSystemMod.DEPOSIT_WITHDRAW_MENU.get(), containerId);
        this.currencyId = currencyId;
        this.isDeposit = isDeposit;

        addDataSlot(currentBalance);
        addDataSlot(taxPreview);

        // Add player inventory slots
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        // Load balance on server
        if (playerInventory.player instanceof ServerPlayer serverPlayer) {
            LedgerService ledger = ServerEvents.getLedgerService();
            if (ledger != null) {
                long balance = ledger.getBalance(serverPlayer.getUUID(), currencyId);
                currentBalance.set((int) (balance & 0xFFFFFFFFL));
            }
        }
    }

    // Client constructor
    public static DepositWithdrawMenu clientConstructor(int containerId, Inventory playerInventory,
            FriendlyByteBuf buf) {
        String currencyId = buf.readUtf();
        boolean isDeposit = buf.readBoolean();
        return new DepositWithdrawMenu(containerId, playerInventory, currencyId, isDeposit);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public String getCurrencyId() {
        return currencyId;
    }

    public boolean isDeposit() {
        return isDeposit;
    }

    public int getCurrentBalanceRaw() {
        return currentBalance.get();
    }

    public int getTaxPreviewRaw() {
        return taxPreview.get();
    }

    public static MenuProvider provider(String currencyId, boolean isDeposit) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable(isDeposit ? LangKeys.GUI_DEPOSIT : LangKeys.GUI_WITHDRAW);
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
                return new DepositWithdrawMenu(containerId, playerInventory, currencyId, isDeposit);
            }
        };
    }
}
