package com.pedrodalben.ecosystem.shop.gui;

import com.pedrodalben.ecosystem.core.EcoSystemMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Client-side deposit/withdraw screen.
 */
public class DepositWithdrawScreen extends AbstractContainerScreen<DepositWithdrawMenu> {

    public DepositWithdrawScreen(DepositWithdrawMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Draw custom dark background
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xCC1a1a2e);

        // Accent border
        int accentColor = menu.isDeposit() ? 0xFF2ecc71 : 0xFFe74c3c;
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 2, accentColor);
        graphics.fill(leftPos, topPos + imageHeight - 2, leftPos + imageWidth, topPos + imageHeight, accentColor);
        graphics.fill(leftPos, topPos, leftPos + 2, topPos + imageHeight, accentColor);
        graphics.fill(leftPos + imageWidth - 2, topPos, leftPos + imageWidth, topPos + imageHeight, accentColor);

        // Header
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + 22, 0xFF2d2d5e);

        // Info area
        graphics.fill(leftPos + 5, topPos + 26, leftPos + imageWidth - 5, topPos + 76, 0x40FFFFFF);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        String mode = menu.isDeposit() ? "§a§l⬇ Deposit" : "§c§l⬆ Withdraw";
        graphics.drawCenteredString(font, mode, leftPos + imageWidth / 2, topPos + 8, 0xFFFFFF);

        // Currency info
        graphics.drawString(font, "§7Currency: §f" + menu.getCurrencyId(),
                leftPos + 10, topPos + 30, 0xFFFFFF, false);

        // Balance
        long balance = menu.getCurrentBalanceRaw() & 0xFFFFFFFFL;
        graphics.drawString(font, "§7Balance: §a" + String.format("%,d", balance),
                leftPos + 10, topPos + 44, 0xFFFFFF, false);

        // Instructions
        if (menu.isDeposit()) {
            graphics.drawString(font, "§7Use /eco deposit to convert items",
                    leftPos + 10, topPos + 60, 0xAAAAAA, false);
        } else {
            graphics.drawString(font, "§7Use /eco withdraw to get items",
                    leftPos + 10, topPos + 60, 0xAAAAAA, false);
        }

        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Handled in render()
    }
}
