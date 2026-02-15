package com.pedrodalben.ecosystem.shop.gui;

import com.pedrodalben.ecosystem.core.EcoSystemMod;
import com.pedrodalben.ecosystem.core.ServerEvents;
import com.pedrodalben.ecosystem.net.ModPayloads;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable;
import java.util.Set;

/**
 * Virtual shop menu supporting multi-shop, multi-page catalog.
 * No real inventory slots — all rendering is done in ShopScreen.
 */
public class ShopMenu extends AbstractContainerMenu {

    private String currencyId = "money";
    private String shopId = "";
    private int page = 0;

    // Cached catalog data (server-side)
    @Nullable
    private ShopDefinition shopDefinition;

    // Client-side: list of available shop IDs for the category overview
    private Set<String> availableShopIds = Set.of();

    /**
     * Server constructor: loads catalog data and sends it to client.
     */
    public ShopMenu(int containerId, Inventory playerInventory, String currencyId, String shopId) {
        super(EcoSystemMod.SHOP_MENU.get(), containerId);
        this.currencyId = currencyId;
        this.shopId = shopId;

        // Load catalog on server
        ShopCatalog catalog = ServerEvents.getShopCatalog();
        if (catalog != null) {
            this.shopDefinition = catalog.getShop(shopId);
            this.availableShopIds = catalog.getShopIds();
        }

        // Send balance sync
        if (playerInventory.player instanceof ServerPlayer sp) {
            ModPayloads.sendBalanceSync(sp);
            // Send catalog data to client
            if (shopId != null && !shopId.isEmpty()) {
                ModPayloads.sendCatalogSync(sp, shopId);
            }
        }
    }

    /**
     * Client constructor: minimal setup, catalog arrives via CatalogSyncPayload.
     */
    public ShopMenu(int containerId, Inventory playerInventory) {
        super(EcoSystemMod.SHOP_MENU.get(), containerId);
    }

    /**
     * Client constructor from network buffer — used by IMenuTypeExtension.
     */
    public static ShopMenu clientConstructor(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        return new ShopMenu(containerId, playerInventory, buf);
    }

    /**
     * Client constructor from network buffer.
     */
    public ShopMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        super(EcoSystemMod.SHOP_MENU.get(), containerId);
        this.currencyId = buf.readUtf();
        this.shopId = buf.readUtf();
    }

    // ==================== PAGE NAVIGATION ====================

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, page);
    }

    public void nextPage() {
        int max = getMaxPages();
        if (page < max - 1)
            page++;
    }

    public void prevPage() {
        if (page > 0)
            page--;
    }

    public int getMaxPages() {
        if (shopDefinition != null)
            return shopDefinition.getPageCount();
        return 1;
    }

    // ==================== ACCESSORS ====================

    public String getCurrencyId() {
        return currencyId;
    }

    public String getShopId() {
        return shopId;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
        this.page = 0;
    }

    @Nullable
    public ShopDefinition getShopDefinition() {
        return shopDefinition;
    }

    public void setShopDefinition(ShopDefinition def) {
        this.shopDefinition = def;
    }

    public Set<String> getAvailableShopIds() {
        return availableShopIds;
    }

    @Nullable
    public CatalogPage getCurrentPage() {
        if (shopDefinition == null)
            return null;
        return shopDefinition.getPage(page);
    }

    // ==================== CONTAINER MENU OVERRIDES ====================

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY; // Virtual menu — no slot interaction
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // ==================== MENU PROVIDER ====================

    /**
     * Creates a MenuProvider for opening this shop menu for a specific shop.
     */
    public static net.minecraft.world.MenuProvider provider(String currencyId, String shopId) {
        return new net.minecraft.world.MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("shop.ecosystem.title");
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
                return new ShopMenu(containerId, playerInventory, currencyId, shopId);
            }
        };
    }
}
