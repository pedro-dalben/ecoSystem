package com.pedrodalben.ecosystem.shop.gui;

import com.pedrodalben.ecosystem.core.LangKeys;
import com.pedrodalben.ecosystem.net.ClientBalanceCache;
import com.pedrodalben.ecosystem.net.ClientCatalogCache;
import com.pedrodalben.ecosystem.net.ShopActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Client-side shop screen rendering a slot-grid layout inspired by GUIShop.
 * Displays items in a 9×N grid with pagination, price tooltips, and buy/sell
 * via click.
 */
public class ShopScreen extends AbstractContainerScreen<ShopMenu> {

    // Layout constants
    private static final int SLOT_SIZE = 18;
    private static final int GRID_COLS = 9;
    private static final int GRID_X_OFFSET = 8;
    private static final int GRID_Y_OFFSET = 18;
    private static final int SIDEBAR_WIDTH = 80;

    // Colors
    private static final int COLOR_BG = 0xCC101020;
    private static final int COLOR_SLOT = 0x88333355;
    private static final int COLOR_SLOT_HOVER = 0xAA5555AA;
    private static final int COLOR_HEADER = 0xFFDDDDFF;
    private static final int COLOR_DISABLED = 0xFF777777;
    private static final int COLOR_NAV_BG = 0xAA222244;
    private static final int COLOR_NAV_HOVER = 0xCC4444AA;
    private static final int COLOR_SIDEBAR_BG = 0xAA1A1A30;
    private static final int COLOR_SIDEBAR_ENTRY = 0x88333355;
    private static final int COLOR_SIDEBAR_SELECTED = 0xCC5555AA;
    private static final int COLOR_WHITE = 0xFFFFFFFF;

    // Cached data
    private ShopDefinition shopDef;
    private int page = 0;
    private int gridRows = 6;
    private int hoveredSlot = -1;

    // Sidebar: list of available shops
    private List<String> shopIds = new ArrayList<>();
    private int selectedShopIndex = 0;

    @SuppressWarnings("resource")
    public ShopScreen(ShopMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = SIDEBAR_WIDTH + GRID_COLS * SLOT_SIZE + 16;
        this.imageHeight = 6 * SLOT_SIZE + GRID_Y_OFFSET + 36;
    }

    @Override
    protected void init() {
        super.init();
        // Try to get catalog from client cache
        ShopDefinition cached = ClientCatalogCache.getCurrentShop();
        if (cached != null) {
            setShopDefinition(cached);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Check if catalog was updated
        ShopDefinition cached = ClientCatalogCache.getCurrentShop();
        if (cached != null && (shopDef == null || !cached.shopId().equals(shopDef.shopId()))) {
            setShopDefinition(cached);
        }

        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        renderShopGui(graphics, mouseX, mouseY);
    }

    public void setShopDefinition(ShopDefinition def) {
        this.shopDef = def;
        this.gridRows = def.rows();
        this.page = 0;
        this.imageHeight = gridRows * SLOT_SIZE + GRID_Y_OFFSET + 36;
        menu.setShopDefinition(def);
    }

    // ==================== RENDERING ====================

    private void renderShopGui(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // Main background
        graphics.fill(x, y, x + imageWidth, y + imageHeight, COLOR_BG);

        // Sidebar (shop list)
        renderSidebar(graphics, x, y, mouseX, mouseY);

        // Grid area
        int gridX = x + SIDEBAR_WIDTH;
        int gridY = y + GRID_Y_OFFSET;

        // Title bar
        String title = shopDef != null ? shopDef.title() : "Shop";
        graphics.drawCenteredString(font, title,
                gridX + (GRID_COLS * SLOT_SIZE) / 2 + GRID_X_OFFSET,
                y + 5, COLOR_HEADER);

        // Render slot grid
        renderSlotGrid(graphics, gridX + GRID_X_OFFSET, gridY, mouseX, mouseY);

        // Bottom bar: pagination + balance
        renderBottomBar(graphics, gridX, y + imageHeight - 16, mouseX, mouseY);

        // Tooltip for hovered slot
        if (hoveredSlot >= 0 && shopDef != null) {
            CatalogPage currentPage = shopDef.getPage(page);
            if (currentPage != null) {
                CatalogEntry entry = currentPage.getEntry(hoveredSlot);
                if (entry != null) {
                    renderItemTooltip(graphics, entry, mouseX, mouseY);
                }
            }
        }
    }

    private void renderSidebar(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        graphics.fill(x, y, x + SIDEBAR_WIDTH - 2, y + imageHeight, COLOR_SIDEBAR_BG);

        graphics.drawString(font, "§l" + Component.translatable(LangKeys.SHOP_TITLE).getString(),
                x + 4, y + 5, COLOR_HEADER);

        // Dynamically fetch shop IDs from catalog or menu
        if (shopIds.isEmpty() && shopDef != null) {
            shopIds = new ArrayList<>(menu.getAvailableShopIds());
            if (shopIds.isEmpty()) {
                shopIds.add(shopDef.shopId());
            }
            // Find selectedShopIndex
            for (int i = 0; i < shopIds.size(); i++) {
                if (shopIds.get(i).equals(shopDef.shopId())) {
                    selectedShopIndex = i;
                    break;
                }
            }
        }

        int entryY = y + 18;
        for (int i = 0; i < shopIds.size(); i++) {
            int btnH = 14;
            boolean hovered = mouseX >= x + 2 && mouseX <= x + SIDEBAR_WIDTH - 4
                    && mouseY >= entryY && mouseY <= entryY + btnH;
            boolean selected = i == selectedShopIndex;

            graphics.fill(x + 2, entryY, x + SIDEBAR_WIDTH - 4, entryY + btnH,
                    selected ? COLOR_SIDEBAR_SELECTED : (hovered ? COLOR_SLOT_HOVER : COLOR_SIDEBAR_ENTRY));

            String displayName = shopIds.get(i);
            // Truncate if too long
            if (font.width(displayName) > SIDEBAR_WIDTH - 12) {
                while (font.width(displayName + "..") > SIDEBAR_WIDTH - 12 && displayName.length() > 1) {
                    displayName = displayName.substring(0, displayName.length() - 1);
                }
                displayName += "..";
            }
            graphics.drawString(font, displayName, x + 5, entryY + 3,
                    selected ? COLOR_WHITE : COLOR_HEADER, false);
            entryY += btnH + 2;
        }

        // Balance at bottom of sidebar
        long balance = 0;
        String currId = shopDef != null ? shopDef.currencyId() : "money";
        Map<String, Long> balances = ClientBalanceCache.getAllBalances();
        if (balances != null && balances.containsKey(currId)) {
            balance = balances.get(currId);
        }
        String balStr = "§a" + balance;
        graphics.drawString(font, Component.translatable(LangKeys.SHOP_BALANCE, balStr),
                x + 4, y + imageHeight - 14, COLOR_WHITE, false);
    }

    private void renderSlotGrid(GuiGraphics graphics, int gridX, int gridY, int mouseX, int mouseY) {
        hoveredSlot = -1;

        if (shopDef == null) {
            graphics.drawCenteredString(font, "Loading...",
                    gridX + (GRID_COLS * SLOT_SIZE) / 2, gridY + 40, COLOR_DISABLED);
            return;
        }

        CatalogPage currentPage = shopDef.getPage(page);
        if (currentPage == null)
            return;

        for (int row = 0; row < gridRows; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int slot = row * GRID_COLS + col;
                int sx = gridX + col * SLOT_SIZE;
                int sy = gridY + row * SLOT_SIZE;

                CatalogEntry entry = currentPage.getEntry(slot);
                boolean hovered = mouseX >= sx && mouseX < sx + SLOT_SIZE
                        && mouseY >= sy && mouseY < sy + SLOT_SIZE;

                if (hovered)
                    hoveredSlot = slot;

                // Slot background
                int slotColor;
                if (entry == null) {
                    slotColor = COLOR_SLOT;
                } else if (hovered) {
                    slotColor = COLOR_SLOT_HOVER;
                } else if (entry.type() == CatalogEntry.EntryType.NAVIGATION) {
                    slotColor = COLOR_NAV_BG;
                } else {
                    slotColor = COLOR_SLOT;
                }
                graphics.fill(sx, sy, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, slotColor);

                if (entry != null) {
                    // Render item icon
                    try {
                        ResourceLocation itemLoc = ResourceLocation.parse(entry.itemId());
                        Item item = BuiltInRegistries.ITEM.get(itemLoc);
                        ItemStack stack = new ItemStack(item, entry.type() == CatalogEntry.EntryType.SHOP
                                ? entry.quantity()
                                : 1);
                        graphics.renderItem(stack, sx + 1, sy + 1);
                    } catch (Exception e) {
                        // Invalid item ID — show placeholder
                    }
                }
            }
        }
    }

    private void renderBottomBar(GuiGraphics graphics, int barX, int barY, int mouseX, int mouseY) {
        int barW = GRID_COLS * SLOT_SIZE + GRID_X_OFFSET * 2;

        // Page info
        int maxPages = shopDef != null ? shopDef.getPageCount() : 1;
        String pageStr = Component.translatable(LangKeys.GUI_PAGE,
                String.valueOf(page + 1), String.valueOf(maxPages)).getString();
        graphics.drawCenteredString(font, pageStr,
                barX + barW / 2, barY + 3, COLOR_HEADER);

        // Prev button (left side)
        if (page > 0) {
            int btnX = barX + GRID_X_OFFSET;
            boolean hovered = mouseX >= btnX && mouseX <= btnX + 30
                    && mouseY >= barY && mouseY <= barY + 12;
            graphics.fill(btnX, barY, btnX + 30, barY + 12,
                    hovered ? COLOR_NAV_HOVER : COLOR_NAV_BG);
            graphics.drawCenteredString(font, "« Prev", btnX + 15, barY + 2,
                    hovered ? COLOR_WHITE : COLOR_HEADER);
        }

        // Next button (right side)
        if (page < maxPages - 1) {
            int btnX = barX + barW - 38;
            boolean hovered = mouseX >= btnX && mouseX <= btnX + 30
                    && mouseY >= barY && mouseY <= barY + 12;
            graphics.fill(btnX, barY, btnX + 30, barY + 12,
                    hovered ? COLOR_NAV_HOVER : COLOR_NAV_BG);
            graphics.drawCenteredString(font, "Next »", btnX + 15, barY + 2,
                    hovered ? COLOR_WHITE : COLOR_HEADER);
        }
    }

    private void renderItemTooltip(GuiGraphics graphics, CatalogEntry entry, int mouseX, int mouseY) {
        List<Component> tooltip = new ArrayList<>();

        // Display name
        tooltip.add(Component.literal("§f§l" + entry.displayName()));

        // Lore lines
        for (String loreLine : entry.lore()) {
            tooltip.add(Component.literal("§7" + loreLine));
        }

        // Price info for SHOP entries
        if (entry.type() == CatalogEntry.EntryType.SHOP) {
            tooltip.add(Component.literal("")); // separator

            if (entry.allowBuy() && entry.buyPrice() > 0) {
                tooltip.add(Component.literal("§a▶ ")
                        .append(Component.translatable(LangKeys.SHOP_PRICE_BUY, entry.buyPrice())));

                // Can the player afford it?
                String currId = shopDef != null ? shopDef.currencyId() : entry.currencyId();
                Map<String, Long> balances = ClientBalanceCache.getAllBalances();
                if (balances != null) {
                    long bal = balances.getOrDefault(currId, 0L);
                    if (bal < entry.buyPrice()) {
                        tooltip.add(Component.literal("§c  ")
                                .append(Component.translatable(LangKeys.SHOP_CANNOT_AFFORD)));
                    }
                }
            } else {
                tooltip.add(Component.literal("§c✖ ")
                        .append(Component.translatable(LangKeys.ERROR_NOT_FOR_SALE)));
            }

            if (entry.allowSell() && entry.sellPrice() > 0) {
                tooltip.add(Component.literal("§6◀ ")
                        .append(Component.translatable(LangKeys.SHOP_PRICE_SELL, entry.sellPrice())));
            }

            if (entry.quantity() > 1) {
                tooltip.add(Component.literal("§7")
                        .append(Component.translatable(LangKeys.SHOP_QUANTITY, entry.quantity())));
            }

            tooltip.add(Component.literal("")); // separator
            tooltip.add(Component.literal("§8Left-click: Buy | Right-click: Sell"));
        }

        // Navigation info
        if (entry.type() == CatalogEntry.EntryType.NAVIGATION) {
            if (entry.navAction() != null) {
                String action = switch (entry.navAction()) {
                    case NEXT_PAGE -> Component.translatable(LangKeys.SHOP_NEXT_PAGE).getString();
                    case PREV_PAGE -> Component.translatable(LangKeys.SHOP_PREV_PAGE).getString();
                    case BACK -> Component.translatable(LangKeys.SHOP_BACK).getString();
                };
                tooltip.add(Component.literal("§e" + action));
            }
            if (entry.targetShop() != null) {
                tooltip.add(Component.literal("§7→ " + entry.targetShop()));
            }
        }

        graphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
    }

    // ==================== INTERACTION ====================

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Background already rendered in renderShopGui
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = leftPos;
        int y = topPos;

        // --- Sidebar click: select shop ---
        if (mouseX >= x && mouseX <= x + SIDEBAR_WIDTH - 4) {
            int entryY = y + 18;
            for (int i = 0; i < shopIds.size(); i++) {
                if (mouseY >= entryY && mouseY <= entryY + 14) {
                    if (i != selectedShopIndex) {
                        selectedShopIndex = i;
                        String newShopId = shopIds.get(i);
                        menu.setShopId(newShopId);
                        // Request catalog from server for new shop
                        // For now, client-side re-render only works with cached data
                    }
                    return true;
                }
                entryY += 16;
            }
        }

        // --- Bottom bar: pagination ---
        int gridX = x + SIDEBAR_WIDTH;
        int barY = y + imageHeight - 16;
        int barW = GRID_COLS * SLOT_SIZE + GRID_X_OFFSET * 2;

        // Prev button
        if (page > 0) {
            int btnX = gridX + GRID_X_OFFSET;
            if (mouseX >= btnX && mouseX <= btnX + 30
                    && mouseY >= barY && mouseY <= barY + 12) {
                page--;
                menu.setPage(page);
                return true;
            }
        }

        // Next button
        int maxPages = shopDef != null ? shopDef.getPageCount() : 1;
        if (page < maxPages - 1) {
            int btnX = gridX + barW - 38;
            if (mouseX >= btnX && mouseX <= btnX + 30
                    && mouseY >= barY && mouseY <= barY + 12) {
                page++;
                menu.setPage(page);
                return true;
            }
        }

        // --- Slot grid click ---
        int gx = gridX + GRID_X_OFFSET;
        int gy = y + GRID_Y_OFFSET;

        if (shopDef != null && mouseX >= gx && mouseX < gx + GRID_COLS * SLOT_SIZE
                && mouseY >= gy && mouseY < gy + gridRows * SLOT_SIZE) {

            int col = (int) ((mouseX - gx) / SLOT_SIZE);
            int row = (int) ((mouseY - gy) / SLOT_SIZE);
            int slot = row * GRID_COLS + col;

            CatalogPage currentPage = shopDef.getPage(page);
            if (currentPage == null)
                return super.mouseClicked(mouseX, mouseY, button);

            CatalogEntry entry = currentPage.getEntry(slot);
            if (entry == null)
                return super.mouseClicked(mouseX, mouseY, button);

            // Handle click based on entry type
            switch (entry.type()) {
                case SHOP -> handleShopClick(entry, button);
                case NAVIGATION -> handleNavigationClick(entry);
                case DECORATIVE -> {
                    /* no-op */ }
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleShopClick(CatalogEntry entry, int mouseButton) {
        ShopActionPayload.Action action;
        if (mouseButton == 0) {
            // Left-click = Buy
            if (!entry.allowBuy() || entry.buyPrice() <= 0)
                return;
            action = ShopActionPayload.Action.BUY;
        } else if (mouseButton == 1) {
            // Right-click = Sell
            if (!entry.allowSell() || entry.sellPrice() <= 0)
                return;
            action = ShopActionPayload.Action.SELL;
        } else {
            return;
        }

        String shopId = shopDef != null ? shopDef.shopId() : menu.getShopId();
        String currencyId = shopDef != null ? shopDef.currencyId() : menu.getCurrencyId();

        PacketDistributor.sendToServer(new ShopActionPayload(
                shopId,
                currencyId,
                entry.itemId(),
                hoveredSlot,
                page,
                1,
                action));
    }

    private void handleNavigationClick(CatalogEntry entry) {
        if (entry.navAction() != null) {
            switch (entry.navAction()) {
                case NEXT_PAGE -> {
                    int maxPages = shopDef != null ? shopDef.getPageCount() : 1;
                    if (page < maxPages - 1) {
                        page++;
                        menu.setPage(page);
                    }
                }
                case PREV_PAGE -> {
                    if (page > 0) {
                        page--;
                        menu.setPage(page);
                    }
                }
                case BACK -> {
                    this.onClose();
                }
            }
        }

        // Target shop navigation
        if (entry.targetShop() != null && !entry.targetShop().isEmpty()) {
            for (int i = 0; i < shopIds.size(); i++) {
                if (shopIds.get(i).equals(entry.targetShop())) {
                    selectedShopIndex = i;
                    menu.setShopId(entry.targetShop());
                    page = 0;
                    menu.setPage(0);
                    break;
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
