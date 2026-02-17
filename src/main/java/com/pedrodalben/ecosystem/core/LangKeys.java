package com.pedrodalben.ecosystem.core;

public class LangKeys {
    // Commands
    public static final String COMMAND_ONLY_PLAYERS = "command.ecosystem.only_players";
    public static final String COMMAND_BALANCE_HEADER = "command.ecosystem.balance.header";
    public static final String COMMAND_BALANCE_ENTRY = "command.ecosystem.balance.entry";
    public static final String COMMAND_BALANCE_OTHER = "command.ecosystem.balance.other";
    public static final String COMMAND_RECEIVED = "command.ecosystem.received";
    public static final String COMMAND_SENT = "command.ecosystem.sent";
    public static final String COMMAND_GAVE_ADMIN = "command.ecosystem.admin.gave";
    public static final String COMMAND_TOOK_ADMIN = "command.ecosystem.admin.took";
    public static final String COMMAND_SET_ADMIN = "command.ecosystem.admin.set";
    public static final String COMMAND_RELOADED = "command.ecosystem.admin.reloaded";
    public static final String COMMAND_CURRENCY_EXISTS = "command.ecosystem.currency.exists";
    public static final String COMMAND_CURRENCY_CREATED = "command.ecosystem.currency.created";
    public static final String COMMAND_CURRENCY_UNKNOWN = "command.ecosystem.currency.unknown";
    public static final String COMMAND_NOT_ITEM_BACKED = "command.ecosystem.not_item_backed";
    public static final String COMMAND_NO_TRANSACTIONS = "command.ecosystem.audit.none";
    public static final String COMMAND_AUDIT_HEADER = "command.ecosystem.audit.header";
    public static final String COMMAND_AUDIT_ENTRY = "command.ecosystem.audit.entry";

    // Errors
    public static final String ERROR_GENERIC = "error.ecosystem.generic";
    public static final String ERROR_INSUFFICIENT_FUNDS = "error.ecosystem.insufficient_funds";
    public static final String ERROR_INSUFFICIENT_SPACE = "error.ecosystem.insufficient_space";
    public static final String ERROR_INSUFFICIENT_STOCK = "error.ecosystem.insufficient_stock";
    public static final String ERROR_INVALID_AMOUNT = "error.ecosystem.invalid_amount";
    public static final String ERROR_SHOP_NO_CHEST = "error.ecosystem.shop.no_chest";
    public static final String ERROR_NO_PERMISSION = "error.ecosystem.no_permission";
    public static final String ERROR_FEATURE_DISABLED = "error.ecosystem.feature_disabled";
    public static final String ERROR_INVALID_INDEX = "error.ecosystem.invalid_index";
    public static final String ERROR_NOT_FOR_SALE = "error.ecosystem.not_for_sale";
    public static final String ERROR_SHOP_NOT_FOUND = "error.ecosystem.shop_not_found";
    public static final String ERROR_NO_LINKED_CHEST = "error.ecosystem.no_linked_chest";
    public static final String ERROR_OUT_OF_STOCK = "error.ecosystem.out_of_stock";
    public static final String ERROR_CURRENCY_NOT_FOUND = "error.ecosystem.currency_not_found";
    public static final String ERROR_SHOP_NO_BUY = "error.ecosystem.shop_no_buy";
    public static final String ERROR_INSUFFICIENT_ITEMS = "error.ecosystem.insufficient_items";
    public static final String ERROR_CHEST_FULL = "error.ecosystem.chest_full";
    public static final String ERROR_OWNER_INSUFFICIENT_FUNDS = "error.ecosystem.owner_insufficient_funds";

    // Shop
    public static final String SHOP_TITLE = "shop.ecosystem.title";
    public static final String SHOP_OWNER = "shop.ecosystem.owner";
    public static final String SHOP_STOCK = "shop.ecosystem.stock";
    public static final String SHOP_PRICE = "shop.ecosystem.price";
    public static final String SHOP_BUY = "shop.ecosystem.buy";
    public static final String SHOP_SELL = "shop.ecosystem.sell";
    public static final String SHOP_ADMIN_MODE = "shop.ecosystem.admin_mode";
    public static final String SHOP_PLAYER_MODE = "shop.ecosystem.player_mode";
    public static final String SHOP_SUCCESS_BUY = "shop.ecosystem.success.buy";
    public static final String SHOP_SUCCESS_SELL = "shop.ecosystem.success.sell";
    public static final String SHOP_NOT_CONFIGURED = "shop.ecosystem.not_configured";
    public static final String SHOP_HEADER = "shop.ecosystem.header";
    public static final String SHOP_CONFIG_LINKED = "shop.ecosystem.config.linked";
    public static final String SHOP_CONFIG_NO_CHEST = "shop.ecosystem.config.no_chest";
    public static final String SHOP_CONFIG_TITLE = "shop.ecosystem.config.title";
    public static final String SHOP_BALANCE = "shop.ecosystem.balance";
    public static final String SHOP_ITEM_NOT_FOUND = "shop.ecosystem.item_not_found";
    public static final String SHOP_NO_BUY = "shop.ecosystem.no_buy";
    public static final String SHOP_PRICE_BUY = "shop.ecosystem.price.buy";
    public static final String SHOP_PRICE_SELL = "shop.ecosystem.price.sell";
    public static final String SHOP_TAX_BREAKDOWN = "shop.ecosystem.tax_breakdown";
    public static final String SHOP_PREV_PAGE = "shop.ecosystem.prev_page";
    public static final String SHOP_NEXT_PAGE = "shop.ecosystem.next_page";
    public static final String SHOP_BACK = "shop.ecosystem.back";
    public static final String SHOP_CANNOT_AFFORD = "shop.ecosystem.cannot_afford";
    public static final String SHOP_RELOAD_SUCCESS = "shop.ecosystem.reload.success";
    public static final String SHOP_RELOAD_WARNINGS = "shop.ecosystem.reload.warnings";
    public static final String SHOP_QUANTITY = "shop.ecosystem.quantity";

    // Sign-based shop creation
    public static final String SIGN_SHOP_CREATED = "shop.ecosystem.sign.created";
    public static final String SIGN_ERROR_NOT_YOUR_NAME = "shop.ecosystem.sign.not_your_name";
    public static final String SIGN_ERROR_NO_TERMINAL = "shop.ecosystem.sign.no_terminal";
    public static final String SIGN_ERROR_OWN_SHOP = "shop.ecosystem.sign.own_shop";
    public static final String SIGN_ERROR_INVALID_FORMAT = "error.ecosystem.sign.invalid_format";
    public static final String SIGN_ERROR_MISSING_OWNER = "error.ecosystem.sign.missing_owner";
    public static final String SIGN_ERROR_INVALID_QUANTITY = "error.ecosystem.sign.invalid_quantity";
    public static final String SIGN_ERROR_INVALID_PRICE = "error.ecosystem.sign.invalid_price";
    public static final String SIGN_ERROR_MISSING_ITEM = "error.ecosystem.sign.missing_item";
    public static final String SIGN_ERROR_INVALID_ITEM = "error.ecosystem.sign.invalid_item";

    // Admin shop inspect/remove
    public static final String INSPECT_HEADER = "command.ecosystem.shop.inspect.header";
    public static final String INSPECT_OWNER = "command.ecosystem.shop.inspect.owner";
    public static final String INSPECT_TYPE = "command.ecosystem.shop.inspect.type";
    public static final String INSPECT_CURRENCY = "command.ecosystem.shop.inspect.currency";
    public static final String INSPECT_LISTING = "command.ecosystem.shop.inspect.listing";
    public static final String INSPECT_STOCK = "command.ecosystem.shop.inspect.stock";
    public static final String INSPECT_CHEST = "command.ecosystem.shop.inspect.chest";
    public static final String INSPECT_NO_SHOP = "command.ecosystem.shop.inspect.no_shop";
    public static final String SHOP_REMOVED = "command.ecosystem.shop.removed";

    // GUI
    public static final String GUI_TERMINAL_TITLE = "gui.ecosystem.terminal.title";
    public static final String GUI_BUTTON_CONFIGURE = "gui.ecosystem.button.configure";
    public static final String GUI_PAGE = "gui.ecosystem.page";
    public static final String GUI_DEPOSIT = "gui.ecosystem.deposit";
    public static final String GUI_WITHDRAW = "gui.ecosystem.withdraw";
    public static final String GUI_CURRENCY = "gui.ecosystem.currency";
    public static final String GUI_DEPOSIT_INSTRUCTION = "gui.ecosystem.deposit.instruction";
    public static final String GUI_WITHDRAW_INSTRUCTION = "gui.ecosystem.withdraw.instruction";

    // Sign Shop Specific
    public static final String ERROR_SELF_TRANSACTION = "error.ecosystem.sign.self_transaction";
    public static final String ERROR_ITEM_NOT_FOUND = "error.ecosystem.sign.item_not_found";
    public static final String ERROR_INVENTORY_FULL = "error.ecosystem.sign.inventory_full";
    public static final String ERROR_PLAYER_NOT_FOUND = "error.ecosystem.sign.player_not_found";
    public static final String ERROR_SHOP_OWNER_NO_FUNDS = "error.ecosystem.sign.owner_no_funds";
    public static final String TRANSACTION_SUCCESS_BUY = "transaction.ecosystem.buy.success";
    public static final String TRANSACTION_SUCCESS_SELL = "transaction.ecosystem.sell.success";
}
