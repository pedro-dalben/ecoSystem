package com.pedrodalben.ecosystem.shop.gui;

import com.pedrodalben.ecosystem.core.EcoSystemMod;
import com.pedrodalben.ecosystem.net.ClientBalanceCache;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.Map;

/**
 * Client-side wallet screen – renders currency balances with a clean UI.
 */
public class WalletScreen extends AbstractContainerScreen<WalletMenu> {

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(EcoSystemMod.MOD_ID,
            "textures/gui/wallet_bg.png");

    public WalletScreen(WalletMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Draw dark background
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xCC1a1a2e);
        // Border
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 2, 0xFF6c63ff);
        graphics.fill(leftPos, topPos + imageHeight - 2, leftPos + imageWidth, topPos + imageHeight, 0xFF6c63ff);
        graphics.fill(leftPos, topPos, leftPos + 2, topPos + imageHeight, 0xFF6c63ff);
        graphics.fill(leftPos + imageWidth - 2, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF6c63ff);

        // Header bar
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + 22, 0xFF2d2d5e);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // Draw title centered in header
        graphics.drawCenteredString(font, "§6§l✦ Wallet ✦", leftPos + imageWidth / 2, topPos + 8, 0xFFFFFF);

        // Draw balances from client cache
        Map<String, Long> balances = ClientBalanceCache.getAllBalances();
        int y = topPos + 30;
        int idx = 0;

        if (balances.isEmpty()) {
            graphics.drawString(font, "§7No currencies loaded...", leftPos + 10, y, 0xAAAAAA, false);
        } else {
            for (Map.Entry<String, Long> entry : balances.entrySet()) {
                String currId = entry.getKey();
                long balance = entry.getValue();

                // Currency row background (alternating)
                int bgColor = idx % 2 == 0 ? 0x40FFFFFF : 0x20FFFFFF;
                graphics.fill(leftPos + 5, y - 2, leftPos + imageWidth - 5, y + 12, bgColor);

                // Currency name
                graphics.drawString(font, "§f" + currId, leftPos + 10, y, 0xFFFFFF, false);
                // Balance (right-aligned)
                String balStr = String.format("%,d", balance);
                int balWidth = font.width(balStr);
                graphics.drawString(font, "§a" + balStr, leftPos + imageWidth - 10 - balWidth, y, 0x55FF55, false);

                y += 16;
                idx++;
                if (y > topPos + imageHeight - 20)
                    break;
            }
        }

        // Footer hint
        graphics.drawCenteredString(font, "§7Use /eco commands for transactions",
                leftPos + imageWidth / 2, topPos + imageHeight - 14, 0x888888);

        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // We handle all label rendering in render() to use absolute coordinates
    }
}
