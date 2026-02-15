package com.pedrodalben.ecosystem.shop.gui;

import com.pedrodalben.ecosystem.core.EcoSystemMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Registers client-side screen factories for all menus.
 * Only loaded on the client (Dist.CLIENT).
 */
@EventBusSubscriber(modid = EcoSystemMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientScreenRegistry {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(EcoSystemMod.WALLET_MENU.get(), WalletScreen::new);
        event.register(EcoSystemMod.DEPOSIT_WITHDRAW_MENU.get(), DepositWithdrawScreen::new);
        event.register(EcoSystemMod.SHOP_MENU.get(), ShopScreen::new);
    }
}
