package com.pedrodalben.ecosystem.core;

import com.pedrodalben.ecosystem.command.EcoCommand;
import com.pedrodalben.ecosystem.net.ModPayloads;
import com.pedrodalben.ecosystem.shop.chest.ShopTerminalBlock;
import com.pedrodalben.ecosystem.shop.chest.ShopTerminalBlockEntity;
import com.pedrodalben.ecosystem.shop.chest.ShopTerminalItem;
import com.pedrodalben.ecosystem.shop.gui.WalletMenu;
import com.pedrodalben.ecosystem.shop.gui.ShopMenu;
import com.pedrodalben.ecosystem.shop.gui.DepositWithdrawMenu;
import com.pedrodalben.ecosystem.shop.chest.ChestShopMenu;
import com.pedrodalben.ecosystem.shop.chest.ShopTerminalConfigMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

@Mod(EcoSystemMod.MOD_ID)
public class EcoSystemMod {
    public static final String MOD_ID = "ecosystem";

    // Deferred Registers
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
            .create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    // Blocks
    public static final DeferredBlock<ShopTerminalBlock> SHOP_TERMINAL_BLOCK = BLOCKS.register("shop_terminal",
            () -> new ShopTerminalBlock(
                    BlockBehaviour.Properties.of().strength(2.5f).requiresCorrectToolForDrops()));

    // Items
    public static final DeferredItem<ShopTerminalItem> SHOP_TERMINAL_ITEM = ITEMS.register("shop_terminal",
            () -> new ShopTerminalItem(
                    SHOP_TERMINAL_BLOCK.get(), new Item.Properties()));

    // Block Entities
    public static final Supplier<BlockEntityType<ShopTerminalBlockEntity>> SHOP_TERMINAL_BE = BLOCK_ENTITIES.register(
            "shop_terminal",
            () -> BlockEntityType.Builder.of(ShopTerminalBlockEntity::new,
                    SHOP_TERMINAL_BLOCK.get()).build(null));

    // Menus
    public static final DeferredHolder<MenuType<?>, MenuType<WalletMenu>> WALLET_MENU = MENUS.register("wallet",
            () -> IMenuTypeExtension.create(WalletMenu::clientConstructor));

    public static final DeferredHolder<MenuType<?>, MenuType<ShopMenu>> SHOP_MENU = MENUS.register("shop",
            () -> IMenuTypeExtension.create(ShopMenu::clientConstructor));

    public static final DeferredHolder<MenuType<?>, MenuType<DepositWithdrawMenu>> DEPOSIT_WITHDRAW_MENU = MENUS
            .register("deposit_withdraw", () -> IMenuTypeExtension.create(DepositWithdrawMenu::clientConstructor));

    public static final DeferredHolder<MenuType<?>, MenuType<ChestShopMenu>> CHEST_SHOP_MENU = MENUS
            .register("chest_shop", () -> IMenuTypeExtension.create(ChestShopMenu::clientConstructor));

    public static final DeferredHolder<MenuType<?>, MenuType<ShopTerminalConfigMenu>> SHOP_TERMINAL_CONFIG_MENU = MENUS
            .register("shop_terminal_config",
                    () -> IMenuTypeExtension.create(ShopTerminalConfigMenu::clientConstructor));

    // Creative Tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ECO_TAB = CREATIVE_TABS
            .register("ecosystem_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ecosystem"))
                    .icon(() -> SHOP_TERMINAL_ITEM.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(SHOP_TERMINAL_ITEM.get());
                    })
                    .build());

    public EcoSystemMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register deferred registers
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);

        // Register config
        modContainer.registerConfig(ModConfig.Type.SERVER, EcoSystemConfig.SPEC);

        // Register networking payloads
        modEventBus.addListener(ModPayloads::register);

        // Register server events
        NeoForge.EVENT_BUS.register(ServerEvents.class);

        // Register commands
        NeoForge.EVENT_BUS.addListener(EcoCommand::register);
    }
}
