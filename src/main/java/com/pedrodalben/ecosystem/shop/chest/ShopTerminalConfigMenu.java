package com.pedrodalben.ecosystem.shop.chest;

import com.pedrodalben.ecosystem.core.EcoSystemMod;
import com.pedrodalben.ecosystem.core.LangKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Config menu for shop terminal owners.
 * Allows setting currency, adding/removing item listings, and linking chests.
 */
public class ShopTerminalConfigMenu extends AbstractContainerMenu {

    private final BlockPos terminalPos;
    private String currencyId = "money";
    private int listingCount = 0;

    // Server constructor
    public ShopTerminalConfigMenu(int containerId, Inventory playerInventory,
            BlockPos pos, ShopTerminalBlockEntity terminalBE) {
        super(EcoSystemMod.SHOP_TERMINAL_CONFIG_MENU.get(), containerId);
        this.terminalPos = pos;
        this.currencyId = terminalBE.getCurrencyId();
        this.listingCount = terminalBE.getListings().size();
    }

    // Client constructor
    public static ShopTerminalConfigMenu clientConstructor(int containerId, Inventory playerInventory,
            FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String currencyId = buf.readUtf();
        int listingCount = buf.readVarInt();

        ShopTerminalConfigMenu menu = new ShopTerminalConfigMenu(containerId, playerInventory, pos,
                createDummyBE(pos, currencyId, listingCount));
        return menu;
    }

    private static ShopTerminalBlockEntity createDummyBE(BlockPos pos, String currencyId, int listingCount) {
        ShopTerminalBlockEntity dummy = new ShopTerminalBlockEntity(pos,
                EcoSystemMod.SHOP_TERMINAL_BLOCK.get().defaultBlockState());
        dummy.setCurrencyId(currencyId);
        return dummy;
    }

    /**
     * Auto-link to an adjacent chest (called from server).
     */
    public boolean autoLinkChest(ServerPlayer player) {
        if (player.level().getBlockEntity(terminalPos) instanceof ShopTerminalBlockEntity be) {
            BlockPos chestPos = be.findAdjacentChest();
            if (chestPos != null) {
                be.setLinkedChestPos(chestPos);
                player.sendSystemMessage(Component.translatable(LangKeys.SHOP_CONFIG_LINKED, chestPos.toShortString()));
                return true;
            } else {
                player.sendSystemMessage(Component.translatable(LangKeys.SHOP_CONFIG_NO_CHEST));
                return false;
            }
        }
        return false;
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

    public BlockPos getTerminalPos() {
        return terminalPos;
    }

    public String getCurrencyId() {
        return currencyId;
    }

    public int getListingCount() {
        return listingCount;
    }

    public static MenuProvider provider(BlockPos pos, ShopTerminalBlockEntity terminalBE) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable(LangKeys.SHOP_CONFIG_TITLE);
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
                return new ShopTerminalConfigMenu(containerId, playerInventory, pos, terminalBE);
            }
        };
    }
}
